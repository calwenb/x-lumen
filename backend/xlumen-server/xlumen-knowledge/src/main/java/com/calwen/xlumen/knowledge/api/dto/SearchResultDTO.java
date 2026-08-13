package com.calwen.xlumen.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索结果（跨模块稳定类型，F-0405 引用溯源）：篇名/段落定位可跳转原文；分数供检索测试（F-0404）展示。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {

    /** 文章 ID。 */
    private Long articleId;

    /** 文章标题。 */
    private String title;

    /** 切片序号。 */
    private Integer chunkSeq;

    /** 段落标题锚点（Markdown 标题，可跳转原文定位）。 */
    private String headingAnchor;

    /** 切片文本。 */
    private String chunkText;

    /** 相似度分数（0~1）。 */
    private float score;

    /** 可见性：1 公开 0 私有。 */
    private Integer visibility;
}
