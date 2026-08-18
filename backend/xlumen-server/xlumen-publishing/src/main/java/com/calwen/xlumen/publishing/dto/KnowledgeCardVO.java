package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识卡片（B01 列表，F-0201/F-0202）：含互动统计（评论/点赞数由 CommentService/LikeService 批量聚合，避免 N+1）。
 * KB-3 起携带库信息（决策 D16，卡片库 badge 跳库），category 废弃。
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

    /** 所属知识库 ID（决策 D16）。 */
    private Long kbId;

    /** 所属知识库名称（冗余展示字段，卡片 badge，由公开读层批量填充）。 */
    private String kbName;

    /** 所属目录 ID（0=库根）。 */
    private Long directoryId;

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

    /** 收藏时间（F-0212 我的收藏列表专用字段，公开列表为 null）。 */
    private LocalDateTime favoritedAt;
}
