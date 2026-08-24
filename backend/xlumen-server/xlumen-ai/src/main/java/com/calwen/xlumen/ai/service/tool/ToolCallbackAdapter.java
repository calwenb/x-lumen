package com.calwen.xlumen.ai.service.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.enums.AiScene;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具适配器（OPT-2/D20 全量迁移）：把业务 AgentTool 注册为 Spring AI ToolCallback，
 * 供 ChatClient 的 ToolCallingAdvisor 自动循环调用。执行原 ToolRegistry 的防护语义：
 * 预算上限（REVIEWER 用审校轮数上限，其余用全局调用数上限，超限给错误信封让模型作答）、
 * 超时（独立 future，孤儿结果丢弃）、结果截断、异常兜底——错误一律以信封回给模型。
 * 每次调用同时向 ToolEventSink 记录 start/done 事件（SSE 实时推送）与落库配对（合成 toolCallId）。
 *
 * @author calwen
 * @date 2026/8/24
 */
public class ToolCallbackAdapter implements ToolCallback {

    /** Spring AI ToolContext 中业务 AgentToolContext 的键（ChatRuntime 注入）。 */
    public static final String KEY_TOOL_CONTEXT = "xlumen.toolContext";
    /** Spring AI ToolContext 中收集器的键。 */
    public static final String KEY_TOOL_SINK = "xlumen.toolSink";

    private final AgentTool tool;
    private final ToolRun run;
    private final AiProperties aiProperties;
    private final ExecutorService executor;

    public ToolCallbackAdapter(AgentTool tool, ToolRun run, AiProperties aiProperties, ExecutorService executor) {
        this.tool = tool;
        this.run = run;
        this.aiProperties = aiProperties;
        this.executor = executor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DefaultToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.parametersSchema())
                .build();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    /** 业务 AgentToolContext 与 Spring 的 ToolContext 不同名，无遮蔽问题（OPT-2 改名后）。 */
    @Override
    public String call(String toolInput, org.springframework.ai.chat.model.ToolContext springContext) {
        ToolEventSink sink = run.getSink();
        int seq = sink.nextSeq();
        String argsSummary = summarizeArgs(toolInput);
        sink.record(ToolEvent.builder().seq(seq).name(tool.name()).phase("start").argsSummary(argsSummary).build());

        long start = System.currentTimeMillis();
        String envelope;
        boolean ok;
        String toolCallId = "tc-" + seq;
        if (sink.callCount() >= maxToolCalls()) {
            envelope = ToolEventPayload.errorEnvelope("工具调用次数已达上限，请基于已有信息直接回答，不再调用工具");
            ok = false;
        } else {
            sink.incrementCalls();
            JSONObject args = parseArgs(toolInput);
            AgentToolContext bizCtx = resolveBizContext(springContext);
            Future<String> future = executor.submit(() -> tool.execute(bizCtx, args));
            try {
                envelope = future.get(aiProperties.getAgentToolTimeoutMillis(), TimeUnit.MILLISECONDS);
                ok = ToolEventPayload.isOk(envelope);
            } catch (TimeoutException e) {
                future.cancel(true);
                envelope = ToolEventPayload.errorEnvelope("工具执行超时");
                ok = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                envelope = ToolEventPayload.errorEnvelope("工具执行被中断");
                ok = false;
            } catch (Exception e) {
                future.cancel(true);
                envelope = ToolEventPayload.errorEnvelope("工具执行异常");
                ok = false;
            }
            if (ok) {
                envelope = truncateResult(envelope, aiProperties.getAgentToolResultMaxChars());
            }
        }
        long durationMs = System.currentTimeMillis() - start;
        sink.record(ToolEvent.builder().seq(seq).name(tool.name()).phase("done").ok(ok)
                .durationMs(durationMs).summary(summarizeOutcome(envelope)).build());
        sink.recordPair(ToolPair.builder()
                .toolCallId(toolCallId)
                .name(tool.name())
                .arguments(toolInput == null ? "" : toolInput)
                .content(envelope)
                .ok(ok)
                .scene(run.getScene())
                .build());
        return envelope;
    }

    /** 场景预算上限：审校（发布闸门路径）独立收紧。 */
    private int maxToolCalls() {
        return run.getScene() == AiScene.REVIEWER
                ? aiProperties.getReviewerAgentMaxRounds()
                : aiProperties.getAgentMaxToolCalls();
    }

    /** 从 Spring AI ToolContext 取回业务 AgentToolContext（执行子线程共享同一实例，仅读）。 */
    private AgentToolContext resolveBizContext(org.springframework.ai.chat.model.ToolContext springContext) {
        if (springContext == null) {
            return run.getToolContext();
        }
        Map<String, Object> context = springContext.getContext();
        if (context != null && context.get(KEY_TOOL_CONTEXT) instanceof AgentToolContext biz) {
            return biz;
        }
        return run.getToolContext();
    }

    /** 参数摘要：掐头防超长。 */
    private String summarizeArgs(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return "";
        }
        String s = arguments.trim();
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    /** 结果摘要：成功取 data 数组规模（命中 N 条），失败取 error 字段。 */
    private String summarizeOutcome(String envelope) {
        if (StrUtil.isBlank(envelope)) {
            return "空结果";
        }
        try {
            JSONObject obj = JSONUtil.parseObj(envelope);
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

    /** 参数解析：非法 JSON 按空对象处理（工具自行校验必填字段）。 */
    private JSONObject parseArgs(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return JSONUtil.createObj();
        }
        try {
            return JSONUtil.parseObj(arguments);
        } catch (Exception e) {
            return JSONUtil.createObj();
        }
    }

    /** 超长结果截断并附 truncated:true。 */
    private String truncateResult(String result, int maxChars) {
        if (result == null) {
            return ToolEventPayload.errorEnvelope("工具返回空结果");
        }
        if (result.length() <= maxChars) {
            return result;
        }
        String cut = result.substring(0, maxChars);
        return JSONUtil.createObj().set("ok", true).set("data", cut).set("truncated", true).toString();
    }
}