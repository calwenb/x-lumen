package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 评论回复引用溯源条目：对应一次检索命中片段，
 * 序列化为 eng_comment.citations_json 数组元素（供前端渲染引用锚点）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCitationVO {

    /** 知识 ID（命中知识）。 */
    private Long knowledgeId;

    /** 知识标题。 */
    private String title;

    /** 段落标题锚点（Markdown 标题，可跳转原文定位）。 */
    private String headingAnchor;

    /** 切片文本。 */
    private String chunkText;

    /** 相似度分数（0~1）。 */
    private float score;
}