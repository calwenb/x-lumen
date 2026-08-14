package com.calwen.xlumen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 审校入参（F-0604）：content 为待审校正文（必填），knowledgeId/title 供溯源与快照。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequestDTO {

    /** 知识 ID（可空，供溯源）。 */
    private Long knowledgeId;

    /** 待审校正文（必填）。 */
    private String content;

    /** 知识标题（可空，供上下文）。 */
    private String title;
}
