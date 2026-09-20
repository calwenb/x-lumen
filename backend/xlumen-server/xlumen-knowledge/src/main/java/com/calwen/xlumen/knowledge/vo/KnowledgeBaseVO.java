package com.calwen.xlumen.knowledge.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库视图（决策 D16）：内容容器与权限边界，可见性库级决定。
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

    /** 公开目录树（仅公开库详情填充：各目录已发布且未回收的知识数；认证路径为 null 不序列化）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<DirectoryVO> directories;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
