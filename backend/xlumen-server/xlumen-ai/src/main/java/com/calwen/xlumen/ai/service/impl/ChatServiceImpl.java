package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.calwen.xlumen.ai.dto.ChatRequestDTO;
import com.calwen.xlumen.ai.dto.CreateConversationDTO;
import com.calwen.xlumen.ai.entity.ChatConversationEntity;
import com.calwen.xlumen.ai.entity.ChatMessageEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.mapper.ChatConversationMapper;
import com.calwen.xlumen.ai.mapper.ChatMessageMapper;
import com.calwen.xlumen.ai.prompt.PromptTemplates;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.ChatService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.tool.AgentToolContext;
import com.calwen.xlumen.ai.service.tool.ToolEvent;
import com.calwen.xlumen.ai.service.tool.ToolEventSink;
import com.calwen.xlumen.ai.service.tool.ToolPair;
import com.calwen.xlumen.ai.service.tool.ToolRun;
import com.calwen.xlumen.ai.util.SseEventName;
import com.calwen.xlumen.ai.vo.ChatMessageVO;
import com.calwen.xlumen.ai.vo.ConversationVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话服务实现（F-0701/F-0702，双轨合并后单轨）：统一走 ChatRuntime 工具化流式（ChatClient 自动多轮工具循环，
 * knowledge.search 工具即 RAG 检索），SSE 返回 chunk/citation/tool/done/error 事件；会话/消息持久化含工具轨迹。
 * OPT-2/D20 全量迁移：链路改走 Spring AI 消息类型与 ChatRuntime，业务语义与对外事件格式不变。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** SSE 超时 30 分钟。 */
    private static final long EMITTER_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    /** 上下文历史消息条数（配对修剪兜底）。 */
    private static final int HISTORY_LIMIT = 30;

    private final WorkspaceApi workspaceApi;
    private final ChatRuntime chatRuntime;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ThreadPoolTaskExecutor chatStreamExecutor;

    public ChatServiceImpl(WorkspaceApi workspaceApi,
                           ChatRuntime chatRuntime,
                           ChatConversationMapper conversationMapper,
                           ChatMessageMapper messageMapper,
                           @Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor) {
        this.workspaceApi = workspaceApi;
        this.chatRuntime = chatRuntime;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @Override
    public SseEmitter streamChat(ChatRequestDTO dto) {
        return doStream(dto, null);
    }

    @Override
    public SseEmitter askKnowledge(Long knowledgeId, ChatRequestDTO dto) {
        return doStream(dto, knowledgeId);
    }

    /** 创建 SSE 并异步执行：会话/检索/生成/持久化都在独立线程，控制器立即返回 emitter。 */
    private SseEmitter doStream(ChatRequestDTO dto, Long knowledgeId) {
        if (StrUtil.isBlank(dto.getQuery())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "提问内容不能为空");
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        Long userId = WorkspaceContext.userId();
        Long targetWs = workspaceId != null ? workspaceId : workspaceApi.getDefaultWorkspaceId();
        if (targetWs == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "暂无可用的知识空间");
        }
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        final Long ws = targetWs;
        final Long uid = userId;
        chatStreamExecutor.execute(() -> runStream(ws, uid, dto, knowledgeId, emitter));
        return emitter;
    }

    /** 流式主流程：会话解析 → 用户消息落库 → 双路径分发（Agent 模式 / 固定 RAG）。 */
    private void runStream(Long workspaceId, Long userId, ChatRequestDTO dto,
                           Long knowledgeId, SseEmitter emitter) {
        try {
            ChatConversationEntity conversation = resolveConversation(workspaceId, userId, dto.getQuery(), dto.getConversationId());
            List<ChatMessageEntity> history = loadHistory(conversation.getId());
            saveMessage(conversation.getId(), workspaceId, userId, "USER", dto.getQuery(), null, null, null, null);

            // 双轨合并：QA 一律走 Agent 工具循环（knowledge.search 工具即 RAG 检索），不再保留固定预检索路径
            runAgent(conversation, workspaceId, userId, dto, knowledgeId, history, emitter);
        } catch (Exception e) {
            log.error("AI 对话流式处理异常", e);
            send(emitter, SseEventName.ERROR, safeMessage(e));
        } finally {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                log.debug("关闭 SseEmitter 失败", ignore);
            }
        }
    }

    /** Agent 路径（双轨合并后唯一路径）：ChatRuntime 工具化流式，SSE 推送 tool 事件。 */
    private void runAgent(ChatConversationEntity conversation, Long workspaceId, Long userId,
                          ChatRequestDTO dto, Long knowledgeId, List<ChatMessageEntity> history,
                          SseEmitter emitter) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(PromptTemplates.QA_AGENT));
        messages.addAll(replayHistory(history));
        messages.add(new UserMessage(dto.getQuery()));

        AtomicBoolean errored = new AtomicBoolean(false);
        ToolEventSink sink = new ToolEventSink(event -> send(emitter, SseEventName.TOOL, JSONUtil.toJsonStr(event)));
        Map<String, SearchResultDTO> citationIndex = new LinkedHashMap<>();
        AgentToolContext toolContext = AgentToolContext.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .conversationId(conversation.getId())
                .kbId(dto.getKbId())
                .citationCollector(results -> {
                    for (SearchResultDTO r : results) {
                        if (r != null && r.getKnowledgeId() != null) {
                            citationIndex.putIfAbsent(r.getKnowledgeId() + ":" + r.getChunkSeq(), r);
                        }
                    }
                })
                .build();
        ToolRun run = ToolRun.builder()
                .scene(AiScene.QA)
                .toolContext(toolContext)
                .sink(sink)
                .build();
        StringBuilder sb = new StringBuilder();
        chatRuntime.chatStreamWithTools(workspaceId, AiScene.QA, messages, 0.7, 1024, run,
                delta -> {
                    sb.append(delta);
                    send(emitter, SseEventName.CHUNK, delta);
                },
                err -> {
                    errored.set(true);
                    send(emitter, SseEventName.ERROR, safeMessage(err));
                });
        if (errored.get()) {
            return;
        }
        // 中间轮轨迹落库：assistant 工具调用行 + tool 行（逐调用配对，合成 tool_call_id）
        persistToolPairs(conversation, workspaceId, userId, sink);
        String citationsJson = toCitationsJson(List.copyOf(citationIndex.values()));
        send(emitter, SseEventName.CITATION, citationsJson);
        ChatMessageEntity assistant = saveMessage(conversation.getId(), workspaceId, userId,
                "ASSISTANT", sb.toString(), citationsJson, null, null, null);
        sendDone(emitter, conversation, assistant);
    }

    /** 工具轨迹落库：每个工具调用一条 assistant 行（tool_calls_json 单调用）+ 一条 tool 行。 */
    private void persistToolPairs(ChatConversationEntity conversation, Long workspaceId, Long userId,
                                  ToolEventSink sink) {
        List<ToolPair> pairs = sink.getPairs();
        for (ToolPair pair : pairs) {
            String toolCallsJson = singleToolCallJson(pair);
            saveMessage(conversation.getId(), workspaceId, userId, "ASSISTANT",
                    "", null, toolCallsJson, null, null);
            saveMessage(conversation.getId(), workspaceId, userId, "TOOL",
                    pair.getContent() == null ? "" : pair.getContent(), null,
                    null, pair.getToolCallId(), pair.getName());
        }
    }

    /** 单调用 tool_calls_json（与 TOOL 行 tool_call_id 配对，前端按 id 归并工具面板）。 */
    private String singleToolCallJson(ToolPair pair) {
        return JSONUtil.createObj().set("id", pair.getToolCallId())
                .set("index", 0)
                .set("name", pair.getName())
                .set("arguments", pair.getArguments() == null ? "" : pair.getArguments())
                .toString();
    }

    @Override
    public List<ConversationVO> listConversations() {
        Long workspaceId = WorkspaceContext.workspaceId();
        Long userId = WorkspaceContext.userId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        List<ChatConversationEntity> list = conversationMapper.selectList(new LambdaQueryWrapper<ChatConversationEntity>()
                .eq(ChatConversationEntity::getWorkspaceId, workspaceId)
                .eq(userId != null, ChatConversationEntity::getUserId, userId)
                .orderByDesc(ChatConversationEntity::getUpdatedAt));
        return list.stream()
                .map(c -> ConversationVO.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .createdAt(c.getCreatedAt())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    public List<ChatMessageVO> listMessages(Long conversationId) {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        ChatConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !workspaceId.equals(conversation.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        List<ChatMessageEntity> list = messageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversationId)
                .orderByAsc(ChatMessageEntity::getCreatedAt));
        return list.stream()
                .map(m -> ChatMessageVO.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .citationsJson(m.getCitationsJson())
                        .toolCallsJson(m.getToolCallsJson())
                        .toolCallId(m.getToolCallId())
                        .toolName(m.getToolName())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public Long createConversation(CreateConversationDTO dto) {
        Long workspaceId = WorkspaceContext.workspaceId();
        Long userId = WorkspaceContext.userId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (StrUtil.isBlank(dto.getTitle())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "会话标题不能为空");
        }
        ChatConversationEntity conversation = new ChatConversationEntity();
        conversation.setWorkspaceId(workspaceId);
        conversation.setUserId(userId);
        conversation.setTitle(dto.getTitle().trim());
        conversationMapper.insert(conversation);
        return conversation.getId();
    }

    /** 解析会话：指定则校验归属，否则新建（标题取提问截断）。 */
    private ChatConversationEntity resolveConversation(Long workspaceId, Long userId, String query, Long conversationId) {
        if (conversationId != null) {
            ChatConversationEntity conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !workspaceId.equals(conversation.getWorkspaceId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
            }
            if (userId != null && !userId.equals(conversation.getUserId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "无权访问该会话");
            }
            return conversation;
        }
        ChatConversationEntity conversation = new ChatConversationEntity();
        conversation.setWorkspaceId(workspaceId);
        conversation.setUserId(userId);
        conversation.setTitle(truncateTitle(query));
        conversationMapper.insert(conversation);
        return conversation;
    }

    /** 加载最近历史消息（最新 30 条，转为时间正序）。 */
    private List<ChatMessageEntity> loadHistory(Long conversationId) {
        List<ChatMessageEntity> list = messageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversationId)
                .orderByDesc(ChatMessageEntity::getCreatedAt)
                .last("LIMIT " + HISTORY_LIMIT));
        Collections.reverse(list);
        return list;
    }

    /**
     * 历史重放（IDEA-025 F-0708 配对修剪）：TOOL 行转 tool 角色消息；窗口截断可能剪坏
     * assistant.tool_calls 与 tool 响应的配对——孤儿 tool 行剔除、toolCalls 无对应响应的 assistant 行降级为纯文本。
     */
    private List<Message> replayHistory(List<ChatMessageEntity> history) {
        List<Message> out = new ArrayList<>();
        if (history == null || history.isEmpty()) {
            return out;
        }
        Set<String> responded = new HashSet<>();
        for (ChatMessageEntity m : history) {
            if ("TOOL".equals(m.getRole()) && StrUtil.isNotBlank(m.getToolCallId())) {
                responded.add(m.getToolCallId());
            }
        }
        for (ChatMessageEntity m : history) {
            if ("USER".equals(m.getRole())) {
                out.add(new UserMessage(m.getContent()));
            } else if ("ASSISTANT".equals(m.getRole())) {
                List<AssistantMessage.ToolCall> toolCalls = parseToolCalls(m.getToolCallsJson());
                if (toolCalls != null) {
                    // 无对应 tool 响应的调用剔除（窗口截断导致响应缺失 → 降级纯文本）
                    toolCalls.removeIf(c -> c.id() == null || !responded.contains(c.id()));
                }
                out.add(AssistantMessage.builder().content(m.getContent()).toolCalls(toolCalls).build());
            } else if ("TOOL".equals(m.getRole())) {
                if (m.getToolCallId() == null || !responded.contains(m.getToolCallId())) {
                    continue; // 孤儿 tool 行剔除
                }
                out.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                m.getToolCallId(), m.getToolName(), m.getContent())))
                        .build());
            }
        }
        return out;
    }

    /** 解析 tool_calls_json 为 ToolCall 列表；非法/空返回 null（assistant 行降级纯文本）。 */
    private List<AssistantMessage.ToolCall> parseToolCalls(String toolCallsJson) {
        if (StrUtil.isBlank(toolCallsJson)) {
            return null;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(toolCallsJson);
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (Object o : arr) {
                cn.hutool.json.JSONObject item = (cn.hutool.json.JSONObject) o;
                calls.add(new AssistantMessage.ToolCall(
                        item.getStr("id"),
                        "function",
                        item.getStr("name"),
                        item.getStr("arguments")));
            }
            return calls;
        } catch (Exception e) {
            log.warn("tool_calls_json 解析失败，降级纯文本", e);
            return null;
        }
    }

    /** 组装引用证据 JSON 数组（knowledgeId 转 String 保 Long 精度）。 */
    private String toCitationsJson(List<SearchResultDTO> evidences) {
        JSONArray arr = new JSONArray();
        if (evidences != null) {
            for (SearchResultDTO e : evidences) {
                arr.add(JSONUtil.createObj()
                        .set("knowledgeId", String.valueOf(e.getKnowledgeId()))
                        .set("title", e.getTitle())
                        .set("chunkSeq", e.getChunkSeq())
                        .set("headingAnchor", e.getHeadingAnchor())
                        .set("chunkText", e.getChunkText())
                        .set("score", e.getScore()));
            }
        }
        return arr.toString();
    }

    private ChatMessageEntity saveMessage(Long conversationId, Long workspaceId, Long userId,
                                          String role, String content, String citationsJson,
                                          String toolCallsJson, String toolCallId, String toolName) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversationId(conversationId);
        message.setWorkspaceId(workspaceId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        message.setToolCallsJson(toolCallsJson);
        message.setToolCallId(toolCallId);
        message.setToolName(toolName);
        messageMapper.insert(message);
        return message;
    }

    private void sendDone(SseEmitter emitter, ChatConversationEntity conversation, ChatMessageEntity assistant) {
        send(emitter, SseEventName.DONE, JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("conversationId", String.valueOf(conversation.getId()))
                .set("messageId", String.valueOf(assistant.getId()))));
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.debug("SSE 事件发送失败 event={}", event, e);
        }
    }

    private String truncateTitle(String query) {
        String q = query == null ? "" : query.trim();
        return q.length() > 30 ? q.substring(0, 30) : q;
    }

    private String safeMessage(Throwable e) {
        String msg = e == null ? null : e.getMessage();
        if (StrUtil.isBlank(msg)) {
            return e == null ? "未知错误" : e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}