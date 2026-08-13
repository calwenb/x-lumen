package com.calwen.xlumen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话入参（F-0701/F-0702）：query 为提问内容，conversationId 为空表示新会话。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDTO {

    /** 提问内容。 */
    private String query;

    /** 会话 ID（空表示新建会话）。 */
    private Long conversationId;
}
