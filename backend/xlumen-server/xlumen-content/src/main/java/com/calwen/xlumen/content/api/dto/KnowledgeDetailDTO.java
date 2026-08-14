package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开知识详情（F-0201）：已发布且库可见的知识正文快照（决策 D16 库级可见性过滤后）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDetailDTO {

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

    /** 所属知识库名称（冗余展示字段，详情面包屑）。 */
    private String kbName;

    /** 所属目录 ID（0=库根）。 */
    private Long directoryId;

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
