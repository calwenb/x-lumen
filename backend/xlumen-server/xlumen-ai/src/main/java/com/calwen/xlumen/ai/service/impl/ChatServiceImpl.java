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
import com.calwen.xlumen.ai.service.ChatService;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.agent.AgentEvents;
import com.calwen.xlumen.ai.service.agent.AgentRequest;
import com.calwen.xlumen.ai.service.agent.AgentResult;
import com.calwen.xlumen.ai.service.agent.AgentRunner;
import com.calwen.xlumen.ai.service.agent.ToolEvent;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
import com.calwen.xlumen.ai.service.provider.ProviderChatResult;
import com.calwen.xlumen.ai.service.provider.StreamCallback;
import com.calwen.xlumen.ai.service.provider.ToolCall;
import com.calwen.xlumen.ai.vo.ChatMessageVO;
import com.calwen.xlumen.ai.vo.ConversationVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话服务实现（F-0701/F-0702）：双路径——agent_enabled=0 走固定 RAG（知识库检索 → System prompt 组装 →
 * QA 流式 → citations），agent_enabled=1 走 AgentRunner 工具循环（模型自主检索，SSE 新增 tool 事件）；
 * SSE 返回 chunk/citation/tool/done/error 事件；会话/消息持久化含工具轨迹（IDEA-025 F-0708）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** SSE 超时 30 分钟。 */
    private static final long EMITTER_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    /** 检索返回条数。 */
    private static final int RETRIEVAL_TOP_K = 5;
    /** 上下文历史消息条数（IDEA-025：由 10 调大到 30，配对修剪兜底）。 */
    private static final int HISTORY_LIMIT = 30;

    /** QA System 提示词：引用 [n] 标注、明确模型生成边界、无证据说明。 */
    private static final String SYSTEM_PROMPT = "你是小光，一名基于知识库的问答助手。"
            + "请基于以下检索证据回答，引用原文时用 [1][2] 标注对应证据编号；"
            + "无法溯源的内容请明确说明是模型生成而非事实；"
            + "若没有任何检索证据，请明确说明没有相关知识依据。";

    /** Agent 模式 System 提示词（IDEA-025 F-0708）：工具检索优先、标注编号、可溯源与不可溯源边界。 */
    private static final String AGENT_SYSTEM_PROMPT = "你是小光，一名基于知识库的问答助手。"
            + "你可以调用知识库工具（knowledge.search 等）自主检索证据：先检索再回答，检索不足时可换关键词、"
            + "换知识库多次检索；引用检索到的原文时用 [n] 标注对应证据编号；"
            + "无法溯源的内容请明确说明是模型生成而非事实；"
            + "工具失败时说明原因，并基于已有信息作答；若没有任何检索证据，请明确说明没有相关知识依据。";

    private final KnowledgeApi knowledgeApi;
    private final WorkspaceApi workspaceApi;
    private final ModelGateway modelGateway;
    private final AgentRunner agentRunner;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ThreadPoolTaskExecutor chatStreamExecutor;

    public ChatServiceImpl(KnowledgeApi knowledgeApi,
                           WorkspaceApi workspaceApi,
                           ModelGateway modelGateway,
                           AgentRunner agentRunner,
                           ChatConversationMapper conversationMapper,
                           ChatMessageMapper messageMapper,
                           @Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor) {
        this.knowledgeApi = knowledgeApi;
        this.workspaceApi = workspaceApi;
        this.modelGateway = modelGateway;
        this.agentRunner = agentRunner;
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

            SceneModel sceneModel = modelGateway.resolveScene(workspaceId, AiScene.QA);
            boolean agentMode = Boolean.TRUE.equals(sceneModel.getAgentEnabled());
            if (agentMode) {
                runAgent(conversation, workspaceId, userId, dto, knowledgeId, history, emitter);
            } else {
                runFixedRag(conversation, workspaceId, userId, dto, knowledgeId, history, emitter);
            }
        } catch (Exception e) {
            log.error("AI 对话流式处理异常", e);
            send(emitter, "error", safeMessage(e));
        } finally {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                log.debug("关闭 SseEmitter 失败", ignore);
            }
        }
    }

    /** 固定 RAG 路径（agent_enabled=false）：现状行为原样保留。 */
    private void runFixedRag(ChatConversationEntity conversation, Long workspaceId, Long userId,
                             ChatRequestDTO dto, Long knowledgeId, List<ChatMessageEntity> history,
                             SseEmitter emitter) {
        List<SearchResultDTO> evidences = retrieve(workspaceId, userId, dto, knowledgeId);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(buildSystemPrompt(evidences)).build());
        messages.addAll(replayHistory(history));
        messages.add(ChatMessage.builder().role("user").content(dto.getQuery()).build());

        StringBuilder sb = new StringBuilder();
        AtomicBoolean errored = new AtomicBoolean(false);
        ProviderChatRequest request = ProviderChatRequest.builder()
                .messages(messages)
                .temperature(0.7)
                .maxTokens(1024)
                .stream(true)
                .build();
        modelGateway.chatStream(workspaceId, AiScene.QA, request,
                new StreamCallback() {
                    @Override
                    public void onContent(String delta) {
                        sb.append(delta);
                        send(emitter, "chunk", delta);
                    }

                    @Override
                    public void onResult(ProviderChatResult result) {
                        // 固定路径终态无额外处理（Agent 路径由 AgentRunner 承接）
                    }
                },
                err -> {
                    errored.set(true);
                    send(emitter, "error", safeMessage(err));
                });
        if (errored.get()) {
            return;
        }

        String citationsJson = toCitationsJson(evidences);
        send(emitter, "citation", citationsJson);
        ChatMessageEntity assistant = saveMessage(conversation.getId(), workspaceId, userId,
                "ASSISTANT", sb.toString(), citationsJson, null, null, null);
        sendDone(emitter, conversation, assistant);
    }

    /** Agent 路径（agent_enabled=true）：AgentRunner 工具循环，SSE 新增 tool 事件。 */
    private void runAgent(ChatConversationEntity conversation, Long workspaceId, Long userId,
                          ChatRequestDTO dto, Long knowledgeId, List<ChatMessageEntity> history,
                          SseEmitter emitter) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(AGENT_SYSTEM_PROMPT).build());
        messages.addAll(replayHistory(history));
        messages.add(ChatMessage.builder().role("user").content(dto.getQuery()).build());

        AtomicBoolean errored = new AtomicBoolean(false);
        AgentRequest request = AgentRequest.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .conversationId(conversation.getId())
                .kbId(dto.getKbId())
                .messages(messages)
                .scene(AiScene.QA)
                .stream(true)
                .temperature(0.7)
                .maxTokens(1024)
                .build();
        AgentResult result = agentRunner.run(request, new AgentEvents() {
            @Override
            public void onContent(String delta) {
                send(emitter, "chunk", delta);
            }

            @Override
            public void onTool(ToolEvent event) {
                send(emitter, "tool", JSONUtil.toJsonStr(event));
            }

            @Override
            public void onError(Throwable error) {
                errored.set(true);
                send(emitter, "error", safeMessage(error));
            }
        });
        if (errored.get()) {
            return;
        }
        if (StrUtil.isNotBlank(result.getError())) {
            // runner 层模型调用错误（onError 已发送）：不落最终行
            return;
        }
        // 中间轮轨迹落库：assistant 工具调用行 + tool 行
        if (result.getAuxMessages() != null) {
            for (ChatMessage aux : result.getAuxMessages()) {
                if ("assistant".equals(aux.getRole())) {
                    saveMessage(conversation.getId(), workspaceId, userId, "ASSISTANT",
                            aux.getContent() == null ? "" : aux.getContent(), null,
                            toToolCallsJson(aux.getToolCalls()), null, null);
                } else if ("tool".equals(aux.getRole())) {
                    saveMessage(conversation.getId(), workspaceId, userId, "TOOL",
                            aux.getContent() == null ? "" : aux.getContent(), null,
                            null, aux.getToolCallId(), aux.getName());
                }
            }
        }
        String citationsJson = toCitationsJson(result.getCitations());
        send(emitter, "citation", citationsJson);
        ChatMessageEntity assistant = saveMessage(conversation.getId(), workspaceId, userId,
                "ASSISTANT", result.getContent() == null ? "" : result.getContent(), citationsJson,
                null, null, null);
        sendDone(emitter, conversation, assistant);
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
    private List<ChatMessage> replayHistory(List<ChatMessageEntity> history) {
        List<ChatMessage> out = new ArrayList<>();
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
                out.add(ChatMessage.builder().role("user").content(m.getContent()).build());
            } else if ("ASSISTANT".equals(m.getRole())) {
                List<ToolCall> toolCalls = parseToolCalls(m.getToolCallsJson());
                if (toolCalls != null) {
                    // 无对应 tool 响应的调用剔除（窗口截断导致响应缺失 → 降级纯文本）
                    toolCalls.removeIf(c -> c.getId() == null || !responded.contains(c.getId()));
                }
                out.add(ChatMessage.builder()
                        .role("assistant")
                        .content(m.getContent())
                        .toolCalls(toolCalls)
                        .build());
            } else if ("TOOL".equals(m.getRole())) {
                if (m.getToolCallId() == null || !responded.contains(m.getToolCallId())) {
                    continue; // 孤儿 tool 行剔除
                }
                out.add(ChatMessage.builder()
                        .role("tool")
                        .toolCallId(m.getToolCallId())
                        .name(m.getToolName())
                        .content(m.getContent())
                        .build());
            }
        }
        return out;
    }

    /** 解析 tool_calls_json 为 ToolCall 列表；非法/空返回 null（assistant 行降级纯文本）。 */
    private List<ToolCall> parseToolCalls(String toolCallsJson) {
        if (StrUtil.isBlank(toolCallsJson)) {
            return null;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(toolCallsJson);
            List<ToolCall> calls = new ArrayList<>();
            for (Object o : arr) {
                cn.hutool.json.JSONObject item = (cn.hutool.json.JSONObject) o;
                calls.add(ToolCall.builder()
                        .id(item.getStr("id"))
                        .index(item.getInt("index"))
                        .name(item.getStr("name"))
                        .arguments(item.getStr("arguments"))
                        .build());
            }
            return calls;
        } catch (Exception e) {
            log.warn("tool_calls_json 解析失败，降级纯文本", e);
            return null;
        }
    }

    /** 序列化 ToolCall 列表为 JSON 文本。 */
    private String toToolCallsJson(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        JSONArray arr = new JSONArray();
        for (ToolCall c : toolCalls) {
            arr.add(JSONUtil.createObj()
                    .set("id", c.getId())
                    .set("index", c.getIndex())
                    .set("name", c.getName())
                    .set("arguments", c.getArguments()));
        }
        return arr.toString();
    }

    /** RAG 检索（决策 D13）：检索范围=kbId 限定单库（知识级问答锁定当前库时前端传 kbId）>
     * allVisible=false 不检索 > 默认全部可见库（resolveVisibleKbIds 按身份推导）；
     * 检索异常降级为空证据。 */
    private List<SearchResultDTO> retrieve(Long workspaceId, Long userId, ChatRequestDTO dto, Long knowledgeId) {
        List<Long> kbIds;
        if (dto.getKbId() != null) {
            kbIds = List.of(dto.getKbId());
        } else if (Boolean.FALSE.equals(dto.getAllVisible())) {
            kbIds = List.of();
        } else {
            List<Long> visible = knowledgeApi.resolveVisibleKbIds(userId);
            kbIds = visible == null ? List.of() : visible;
        }
        SearchRequestDTO request = SearchRequestDTO.builder()
                .workspaceId(workspaceId)
                .query(dto.getQuery())
                .kbIds(kbIds)
                .topK(RETRIEVAL_TOP_K)
                .knowledgeId(knowledgeId)
                .build();
        try {
            return knowledgeApi.search(request);
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为无证据问答", e);
            return List.of();
        }
    }

    /** 组装 System prompt：证据编号 + 原文片段，无证据显式说明。 */
    private String buildSystemPrompt(List<SearchResultDTO> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return SYSTEM_PROMPT + "（本次未检索到任何相关知识证据。）";
        }
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT).append("\n\n检索证据：\n");
        for (int i = 0; i < evidences.size(); i++) {
            SearchResultDTO e = evidences.get(i);
            sb.append('[').append(i + 1).append("] 《").append(e.getTitle() == null ? "" : e.getTitle()).append("》");
            if (StrUtil.isNotBlank(e.getHeadingAnchor())) {
                sb.append(" #").append(e.getHeadingAnchor());
            }
            sb.append("：").append(e.getChunkText() == null ? "" : e.getChunkText()).append('\n');
        }
        return sb.toString();
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
        send(emitter, "done", JSONUtil.toJsonStr(JSONUtil.createObj()
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