package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开知识卡片（F-0201）：公开列表展示用，不返回正文（详情接口才返回）。
 * KB-3 起携带库信息（决策 D16，卡片库 badge 跳库）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedKnowledgeDTO {

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

    /** 所属知识库名称（冗余展示字段，卡片 badge）。 */
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

    /** 更新时间（列表按此倒序）。 */
    private LocalDateTime updatedAt;
}
