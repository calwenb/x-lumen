package com.calwen.xlumen.ai.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
import com.calwen.xlumen.ai.service.provider.ProviderChatResult;
import com.calwen.xlumen.ai.service.provider.StreamCallback;
import com.calwen.xlumen.ai.service.provider.ToolCall;
import com.calwen.xlumen.ai.service.provider.ToolSpec;
import com.calwen.xlumen.ai.service.tool.ToolContext;
import com.calwen.xlumen.ai.service.tool.ToolRegistry;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 编排器实现（IDEA-025 F-0708）：在调用方线程内同步驱动多轮工具循环——
 * 每轮走 ModelGateway（tools 随请求透传，场景解析/供应商回退/熔断照旧），
 * 模型发起工具调用则执行（超时/截断/白名单防护在 ToolRegistry）并把结果以 tool 角色消息回喂，
 * 轮数用尽仍要工具时最后一轮去掉 tools 强制作答。流式/非流式双模式。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class AgentRunnerImpl implements AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunnerImpl.class);

    /** 强制收尾提示：轮数用尽仍要工具时追加。 */
    private static final String FORCE_ANSWER_HINT = "请基于以上工具结果直接回答，不再调用工具。";

    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final AiProperties aiProperties;

    public AgentRunnerImpl(ModelGateway modelGateway, ToolRegistry toolRegistry, AiProperties aiProperties) {
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.aiProperties = aiProperties;
    }

    @Override
    public AgentResult run(AgentRequest request, AgentEvents events) {
        List<ChatMessage> messages = new ArrayList<>(request.getMessages());
        List<ToolSpec> tools = toolRegistry.specsFor(request.getScene());
        int maxRounds = maxRounds(request.getScene());
        StringBuilder accumulator = new StringBuilder();
        List<ChatMessage> auxMessages = new ArrayList<>();
        List<ToolEvent> toolTrace = new ArrayList<>();
        Map<String, SearchResultDTO> citationIndex = new LinkedHashMap<>();

        ToolContext ctx = ToolContext.builder()
                .workspaceId(request.getWorkspaceId())
                .userId(request.getUserId())
                .conversationId(request.getConversationId())
                .kbId(request.getKbId())
                .citationCollector(results -> {
                    for (SearchResultDTO r : results) {
                        citationIndex.putIfAbsent(r.getKnowledgeId() + ":" + r.getChunkSeq(), r);
                    }
                })
                .build();

        int toolCallCount = 0;
        ProviderChatResult last = null;
        for (int round = 1; round <= maxRounds; round++) {
            last = call(request, messages, tools, accumulator, events);
            if (last == null) {
                // 模型调用错误：调用方已收到 onError，返回部分内容与错误标记
                return AgentResult.builder()
                        .content(accumulator.toString())
                        .toolTrace(toolTrace)
                        .citations(List.copyOf(citationIndex.values()))
                        .auxMessages(auxMessages)
                        .error("模型调用失败")
                        .build();
            }
            messages.add(assistantMessage(last));
            if (!last.hasToolCalls()) {
                break;
            }
            // 中间轮轨迹（assistant+tool）落库
            auxMessages.add(assistantMessage(last));
            for (ToolCall call : last.getToolCalls()) {
                int seq = toolTrace.size() + 1;
                String argsSummary = summarizeArgs(call.getArguments());
                events.onTool(ToolEvent.builder()
                        .seq(seq).name(call.getName()).phase("start").argsSummary(argsSummary).build());
                long start = System.currentTimeMillis();
                String outcome;
                boolean ok;
                if (toolCallCount >= aiProperties.getAgentMaxToolCalls()) {
                    outcome = ToolRegistry.errorEnvelope("工具调用次数已达上限");
                    ok = false;
                } else {
                    outcome = toolRegistry.execute(ctx, call);
                    ok = outcome != null && outcome.contains("\"ok\":true");
                }
                toolCallCount++;
                long durationMs = System.currentTimeMillis() - start;
                String summary = summarizeOutcome(outcome);
                events.onTool(ToolEvent.builder()
                        .seq(seq).name(call.getName()).phase("done").ok(ok)
                        .durationMs(durationMs).summary(summary).build());
                toolTrace.add(ToolEvent.builder()
                        .seq(seq).name(call.getName()).phase("done").ok(ok)
                        .durationMs(durationMs).summary(summary).build());
                ChatMessage toolMsg = ChatMessage.builder()
                        .role("tool")
                        .toolCallId(call.getId())
                        .name(call.getName())
                        .content(outcome)
                        .build();
                messages.add(toolMsg);
                auxMessages.add(toolMsg);
            }
        }
        // 轮数用尽仍要工具：最后一轮去掉 tools 强制作答
        if (last != null && last.hasToolCalls()) {
            messages.add(ChatMessage.builder().role("user").content(FORCE_ANSWER_HINT).build());
            last = call(request, messages, null, accumulator, events);
        }
        return AgentResult.builder()
                .content(accumulator.toString())
                .toolTrace(toolTrace)
                .citations(List.copyOf(citationIndex.values()))
                .auxMessages(auxMessages)
                .build();
    }

    /** 场景轮数上限：审校（发布闸门路径）独立收紧。 */
    private int maxRounds(AiScene scene) {
        return scene == AiScene.REVIEWER ? aiProperties.getReviewerAgentMaxRounds() : aiProperties.getAgentMaxRounds();
    }

    /** 单轮模型调用：流式走 chatStream（增量转发+终态聚合），非流式走 chat（整段回调）。 */
    private ProviderChatResult call(AgentRequest request, List<ChatMessage> messages, List<ToolSpec> tools,
                                    StringBuilder accumulator, AgentEvents events) {
        ProviderChatRequest req = ProviderChatRequest.builder()
                .messages(messages)
                .tools(tools)
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .stream(request.isStream())
                .build();
        if (request.isStream()) {
            AtomicReference<ProviderChatResult> holder = new AtomicReference<>();
            AtomicBoolean errored = new AtomicBoolean(false);
            modelGateway.chatStream(request.getWorkspaceId(), request.getScene(), req,
                    new StreamCallback() {
                        @Override
                        public void onContent(String delta) {
                            accumulator.append(delta);
                            events.onContent(delta);
                        }

                        @Override
                        public void onResult(ProviderChatResult result) {
                            holder.set(result);
                        }
                    },
                    err -> {
                        errored.set(true);
                        events.onError(err);
                    });
            if (errored.get()) {
                return null;
            }
            ProviderChatResult result = holder.get();
            if (result == null) {
                // 供应商未回调终态（异常流尾）：以已流过的增量兜底
                return ProviderChatResult.builder().content(accumulator.toString()).toolCalls(List.of()).build();
            }
            return result;
        }
        try {
            ProviderChatResult result = modelGateway.chat(request.getWorkspaceId(), request.getScene(), req);
            if (StrUtil.isNotBlank(result.getContent())) {
                accumulator.append(result.getContent());
                events.onContent(result.getContent());
            }
            return result;
        } catch (Exception e) {
            events.onError(e);
            return null;
        }
    }

    /** 组装 assistant 消息（携带本轮工具调用）。 */
    private ChatMessage assistantMessage(ProviderChatResult result) {
        return ChatMessage.builder()
                .role("assistant")
                .content(result.getContent() == null ? "" : result.getContent())
                .toolCalls(result.getToolCalls())
                .build();
    }

    /** 参数摘要：取 query/kbId/topK 等关键字段，掐头防超长。 */
    private String summarizeArgs(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return "";
        }
        String s = arguments.trim();
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    /** 结果摘要：成功取 data 数组规模（命中 N 条），失败取 error 字段。 */
    private String summarizeOutcome(String outcome) {
        if (StrUtil.isBlank(outcome)) {
            return "空结果";
        }
        try {
            JSONObject obj = JSONUtil.parseObj(outcome);
            if (Boolean.TRUE.equals(obj.getBool("ok"))) {
                Object data = obj.get("data");
                if (data instanceof JSONArray arr) {
                    return "命中 " + arr.size() + " 条";
                }
                if (data instanceof String) {
                    return "已返回（截断）";
                }
                return "完成";
            }
            String err = obj.getStr("error");
            if (StrUtil.isNotBlank(err)) {
                return err.length() > 60 ? err.substring(0, 60) + "…" : err;
            }
            return "执行失败";
        } catch (Exception e) {
            return "完成";
        }
    }
}