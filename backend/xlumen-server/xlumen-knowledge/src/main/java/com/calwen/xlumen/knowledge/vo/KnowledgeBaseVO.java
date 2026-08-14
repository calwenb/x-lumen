package com.calwen.xlumen.knowledge.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库视图（F-0308，决策 D16）：内容容器与权限边界，可见性库级决定。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseVO {

    /** 知识库 ID（雪花 ID，字符串传输，BACKEND.md §5.3）。 */
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 名称（空间内唯一）。 */
    private String name;

    /** 简介。 */
    private String intro;

    /** 封面 URL。 */
    private String cover;

    /** 可见性：0 私有 1 公开。 */
    private Integer visibility;

    /** 库内知识总数（统计口径：未删除知识，含草稿）。 */
    private Long knowledgeCount;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
