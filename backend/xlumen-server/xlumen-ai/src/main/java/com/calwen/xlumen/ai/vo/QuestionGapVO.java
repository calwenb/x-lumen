package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 问答知识缺口视图。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGapVO {

    /** 缺口 ID。 */
    private Long id;

    /** 会话 ID（可空）。 */
    private Long conversationId;

    /** 问题文本。 */
    private String question;

    /** 来源：ANSWER_UNMATCHED|FOLLOWUP_SUGGESTED。 */
    private String source;

    /** 状态：UNHANDLED|HANDLED。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}