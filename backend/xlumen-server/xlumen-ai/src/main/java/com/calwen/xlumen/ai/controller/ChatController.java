package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.ChatRequestDTO;
import com.calwen.xlumen.ai.dto.CreateConversationDTO;
import com.calwen.xlumen.ai.service.ChatService;
import com.calwen.xlumen.ai.vo.ChatMessageVO;
import com.calwen.xlumen.ai.vo.ConversationVO;
import com.calwen.xlumen.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 对话接口（F-0701/F-0702）：流式问答（SSE chunk/citation/done）、文章级问答、会话/消息管理。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 通用问答（流式 SSE）：body 传 query/conversationId（conversationId 空则新建会话）。
     */
    @PostMapping("/stream")
    public SseEmitter stream(@Valid @RequestBody ChatRequestDTO dto) {
        return chatService.streamChat(dto);
    }

    /**
     * 文章级问答（F-0702，流式 SSE）：限定单篇文章检索。
     */
    @PostMapping("/articles/{articleId}/ask")
    public SseEmitter ask(@PathVariable Long articleId,
                          @Valid @RequestBody ChatRequestDTO dto) {
        return chatService.askArticle(articleId, dto);
    }

    /**
     * 会话列表（按更新时间倒序）。
     */
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVO>> conversations() {
        return ApiResponse.success(chatService.listConversations());
    }

    /**
     * 会话消息列表（按时间正序）。
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<ChatMessageVO>> messages(@PathVariable Long conversationId) {
        return ApiResponse.success(chatService.listMessages(conversationId));
    }

    /**
     * 新建会话（标题必填）。
     */
    @PostMapping("/conversations")
    public ApiResponse<Long> createConversation(@Valid @RequestBody CreateConversationDTO dto) {
        return ApiResponse.success(chatService.createConversation(dto));
    }
}
