package com.calwen.xlumen.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 工作空间实体（iam_workspace，F-0102）：注册即建空间（决策 D9），全局隔离维度。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Getter
@Setter
@TableName("iam_workspace")
public class WorkspaceEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 空间名称。 */
    private String name;

    /** 空间标识（唯一，uk_workspace_slug 承担业务唯一约束）。 */
    private String slug;

    /** 空间简介（F-1201，可空）。 */
    private String intro;

    /** 所有者用户 ID（逻辑外键 iam_user.id）。 */
    private Long ownerUserId;

    /** 状态：1 正常 0 停用。 */
    private Integer status;

    /** 强制审核开关（F-1201，决策 D9）：1 开启 0 关闭。 */
    private Integer forceReview;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
