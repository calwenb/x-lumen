package com.calwen.xlumen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 写作入参（F-0601）：topic/draft/content 至少填写一项（Service 层校验），title 为可选标题提示。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingRequestDTO {

    /** 写作主题。 */
    private String topic;

    /** 草稿（半成品续写）。 */
    private String draft;

    /** 素材/完整文章（改写/扩写）。 */
    private String content;

    /** 标题提示（可空）。 */
    private String title;
}
