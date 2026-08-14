package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识详情（B02，F-0201）：正文 + 互动统计 + 当前用户点赞状态（登录时）。
 * KB-3 起携带库信息（决策 D16，详情面包屑），category 废弃。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDetailVO {

    /** 知识 ID（雪花 ID，字符串传输，BACKEND.md §5.3）。 */
    private Long id;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String summary;

    /** 正文 Markdown。 */
    private String content;

    /** 作者名（冗余展示字段）。 */
    private String authorName;

    /** 所属知识库 ID（决策 D16）。 */
    private Long kbId;

    /** 所属知识库名称（冗余展示字段，详情面包屑，由公开读层填充）。 */
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

    /** 当前用户是否已点赞（登录时有效）。 */
    private boolean liked;

    /** 发布时间。 */
    private LocalDateTime publishedAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
