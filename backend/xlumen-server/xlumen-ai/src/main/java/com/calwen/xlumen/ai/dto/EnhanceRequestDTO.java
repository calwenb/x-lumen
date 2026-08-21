package com.calwen.xlumen.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    /** 知识 ID（可空，供独立增强）。 */
    private Long knowledgeId;

    /** 场景：SUMMARY|SEO（OpenAPI 契约暴露枚举，BUG-019）。 */
    @Schema(description = "增强场景", allowableValues = {"SUMMARY", "SEO"})
    private String scene;

    /** 待处理内容。 */
    private String content;
}
