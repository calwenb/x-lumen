package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.dto.ChatRequestDTO;
import com.calwen.xlumen.ai.dto.CreateConversationDTO;
import com.calwen.xlumen.ai.vo.ChatMessageVO;
import com.calwen.xlumen.ai.vo.ConversationVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 对话服务（F-0701/F-0702）：RAG 检索增强问答（流式 SSE）+ 引用溯源 + 会话/消息管理。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ChatService {

    /**
     * 通用问答（流式 SSE）：SSE 返回 chunk/citation/done 事件。
     *
     * @param dto 提问（query 必填，conversationId 空则新建会话）
     * @return SSE 事件流
     */
    SseEmitter streamChat(ChatRequestDTO dto);

    /**
     * 文章级问答（F-0702）：限定单篇文章检索，SSE 事件同上。
     *
     * @param articleId 文章 ID
     * @param dto       提问
     * @return SSE 事件流
     */
    SseEmitter askArticle(Long articleId, ChatRequestDTO dto);

    /**
     * 当前用户会话列表（按更新时间倒序）。
     *
     * @return 会话列表
     */
    List<ConversationVO> listConversations();

    /**
     * 会话消息列表（按时间正序）。
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    List<ChatMessageVO> listMessages(Long conversationId);

    /**
     * 新建会话（标题必填）。
     *
     * @param dto 会话入参
     * @return 会话 ID
     */
    Long createConversation(CreateConversationDTO dto);
}
