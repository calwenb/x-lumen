package com.calwen.xlumen.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库实体（kb_knowledge_base，F-0308，决策 D16）：内容容器与权限边界，
 * 可见性库级决定（0 私有/1 公开）；回收站用 status+deleted_at（独立软删标记，不扩 8 状态机）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Getter
@Setter
@TableName("kb_knowledge_base")
public class KbKnowledgeBaseEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID（逻辑外键 iam_workspace.id）。 */
    private Long workspaceId;

    /** 知识库名称（空间内唯一，uk_kb_ws_name）。 */
    private String name;

    /** 简介。 */
    private String intro;

    /** 封面 URL（V2 接入 MinIO 前可空）。 */
    private String cover;

    /** 可见性：0 私有 1 公开（库级统一决定知识可见范围）。 */
    private Integer visibility;

    /** 状态：0 正常 1 回收站。 */
    private Integer status;

    /** 进回收站时间（超期清理依据，默认保留 30 天）。 */
    private LocalDateTime deletedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
