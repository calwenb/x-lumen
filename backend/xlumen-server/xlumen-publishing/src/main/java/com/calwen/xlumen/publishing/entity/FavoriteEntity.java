package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识收藏实体（eng_favorite，F-0212）：唯一键 (workspace_id, knowledge_id, user_id) 承担幂等，
 * 收藏/取消更新 status，结构与 eng_like 同构。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Getter
@Setter
@TableName("eng_favorite")
public class FavoriteEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 知识 ID（逻辑外键 cnt_knowledge.id）。 */
    private Long knowledgeId;

    /** 收藏用户 ID（逻辑外键 iam_user.id）。 */
    private Long userId;

    /** 状态：1 已收藏 0 取消。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
