package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用证据视图（F-0701/F-0702，检索溯源）：篇名/段落定位可跳转原文。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationVO {

    /** 文章 ID。 */
    private Long articleId;

    /** 文章标题。 */
    private String title;

    /** 切片序号。 */
    private Integer chunkSeq;

    /** 段落标题锚点（Markdown 标题）。 */
    private String headingAnchor;

    /** 切片文本。 */
    private String chunkText;

    /** 相似度分数（0~1）。 */
    private float score;
}
