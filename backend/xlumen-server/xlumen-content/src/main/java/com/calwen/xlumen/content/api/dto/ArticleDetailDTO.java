package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开文章详情（F-0201）：已发布公开文章正文快照（F-0307 过滤后）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailDTO {

    /** 文章 ID（雪花 ID，字符串传输，BACKEND.md §5.3）。 */
    private Long id;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String summary;

    /** 正文 Markdown。 */
    private String content;

    /** 作者名（冗余展示字段）。 */
    private String authorName;

    /** 分类。 */
    private String category;

    /** 标签数组。 */
    private List<String> tags;

    /** 阅读量。 */
    private Long viewCount;

    /** 阅读时间（分钟估算）。 */
    private int readMinutes;

    /** 发布时间。 */
    private LocalDateTime publishedAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
