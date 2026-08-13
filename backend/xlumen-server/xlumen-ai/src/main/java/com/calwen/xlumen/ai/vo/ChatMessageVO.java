package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 对话消息视图（F-0701/F-0702）：历史消息展示，citationsJson 为引用证据快照文本。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO {

    /** 消息 ID。 */
    private Long id;

    /** 角色：USER|ASSISTANT。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 引用证据（JSON 文本，可空）。 */
    private String citationsJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
