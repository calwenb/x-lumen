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
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话服务实现（F-0701/F-0702）：KnowledgeApi 检索 → System prompt 组装 → QA 流式 →
 * citations 组装 → 会话/消息持久化；SSE 返回 chunk/citation/done/error 事件。
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
    /** 上下文历史消息条数。 */
    private static final int HISTORY_LIMIT = 10;

    /** 可见性：访客仅公开。 */
    private static final String SCOPE_PUBLIC_ONLY = "PUBLIC_ONLY";
    /** 可见性：登录含私有。 */
    private static final String SCOPE_ALL = "ALL";

    /** QA System 提示词：引用 [n] 标注、明确模型生成边界、无证据说明。 */
    private static final String SYSTEM_PROMPT = "你是小光，一名基于知识库的问答助手。"
            + "请基于以下检索证据回答，引用原文时用 [1][2] 标注对应证据编号；"
            + "无法溯源的内容请明确说明是模型生成而非事实；"
            + "若没有任何检索证据，请明确说明没有相关知识依据。";

    private final KnowledgeApi knowledgeApi;
    private final WorkspaceApi workspaceApi;
    private final ModelGateway modelGateway;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ThreadPoolTaskExecutor chatStreamExecutor;

    public ChatServiceImpl(KnowledgeApi knowledgeApi,
                           WorkspaceApi workspaceApi,
                           ModelGateway modelGateway,
                           ChatConversationMapper conversationMapper,
                           ChatMessageMapper messageMapper,
                           @Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor) {
        this.knowledgeApi = knowledgeApi;
        this.workspaceApi = workspaceApi;
        this.modelGateway = modelGateway;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @Override
    public SseEmitter streamChat(ChatRequestDTO dto) {
        return doStream(dto.getQuery(), dto.getConversationId(), null);
    }

    @Override
    public SseEmitter askKnowledge(Long knowledgeId, ChatRequestDTO dto) {
        return doStream(dto.getQuery(), dto.getConversationId(), knowledgeId);
    }

    /** 创建 SSE 并异步执行：会话/检索/生成/持久化都在独立线程，控制器立即返回 emitter。 */
    private SseEmitter doStream(String query, Long conversationId, Long knowledgeId) {
        if (StrUtil.isBlank(query)) {
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
        chatStreamExecutor.execute(() -> runStream(ws, uid, query, conversationId, knowledgeId, emitter));
        return emitter;
    }

    /** 流式主流程：会话解析 → 用户消息落库 → 检索 → QA 流式 → citations → 助手消息落库。 */
    private void runStream(Long workspaceId, Long userId, String query,
                           Long conversationId, Long knowledgeId, SseEmitter emitter) {
        try {
            ChatConversationEntity conversation = resolveConversation(workspaceId, userId, query, conversationId);
            List<ChatMessageEntity> history = loadHistory(conversation.getId());
            saveMessage(conversation.getId(), workspaceId, userId, "USER", query, null);

            List<SearchResultDTO> evidences = retrieve(workspaceId, userId, query, knowledgeId);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.builder().role("system").content(buildSystemPrompt(evidences)).build());
            for (ChatMessageEntity m : history) {
                messages.add(ChatMessage.builder()
                        .role("USER".equals(m.getRole()) ? "user" : "assistant")
                        .content(m.getContent())
                        .build());
            }
            messages.add(ChatMessage.builder().role("user").content(query).build());

            StringBuilder sb = new StringBuilder();
            AtomicBoolean errored = new AtomicBoolean(false);
            ProviderChatRequest request = ProviderChatRequest.builder()
                    .messages(messages)
                    .temperature(0.7)
                    .maxTokens(1024)
                    .stream(true)
                    .build();
            modelGateway.chatStream(workspaceId, AiScene.QA, request,
                    chunk -> {
                        sb.append(chunk);
                        send(emitter, "chunk", chunk);
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
                    "ASSISTANT", sb.toString(), citationsJson);
            send(emitter, "done", JSONUtil.toJsonStr(JSONUtil.createObj()
                    .set("conversationId", String.valueOf(conversation.getId()))
                    .set("messageId", String.valueOf(assistant.getId()))));
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

    /** 加载最近历史消息（最新 10 条，转为时间正序）。 */
    private List<ChatMessageEntity> loadHistory(Long conversationId) {
        List<ChatMessageEntity> list = messageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversationId)
                .orderByDesc(ChatMessageEntity::getCreatedAt)
                .last("LIMIT " + HISTORY_LIMIT));
        Collections.reverse(list);
        return list;
    }

    /** RAG 检索：访客仅公开、登录含私有；检索异常降级为空证据。 */
    private List<SearchResultDTO> retrieve(Long workspaceId, Long userId, String query, Long knowledgeId) {
        SearchRequestDTO request = SearchRequestDTO.builder()
                .workspaceId(workspaceId)
                .query(query)
                .visibilityScope(userId == null ? SCOPE_PUBLIC_ONLY : SCOPE_ALL)
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
                                          String role, String content, String citationsJson) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversationId(conversationId);
        message.setWorkspaceId(workspaceId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        messageMapper.insert(message);
        return message;
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
