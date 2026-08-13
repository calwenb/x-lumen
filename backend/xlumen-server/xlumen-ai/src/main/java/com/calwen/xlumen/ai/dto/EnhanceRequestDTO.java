package com.calwen.xlumen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 增值入参（F-0801/F-0802）：scene 仅支持 SUMMARY|SEO，content 为待处理内容。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhanceRequestDTO {

    /** 文章 ID（可空，供独立增强）。 */
    private Long articleId;

    /** 场景：SUMMARY|SEO。 */
    private String scene;

    /** 待处理内容。 */
    private String content;
}
