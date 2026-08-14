package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识卡片（B01 列表，F-0201/F-0202）：含互动统计（评论/点赞数由 CommentService/LikeService 批量聚合，避免 N+1）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCardVO {

    /** 知识 ID（雪花 ID，字符串传输，BACKEND.md §5.3）。 */
    private Long id;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String summary;

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

    /** 评论数。 */
    private long commentCount;

    /** 点赞数。 */
    private long likeCount;

    /** 发布时间。 */
    private LocalDateTime publishedAt;
}
