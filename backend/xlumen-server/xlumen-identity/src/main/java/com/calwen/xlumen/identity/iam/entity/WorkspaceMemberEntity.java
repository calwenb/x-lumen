package com.calwen.xlumen.identity.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 空间成员实体（iam_workspace_member，F-0102/F-0103）：成员角色绑定，
 * 唯一键 uk_workspace_member 承担幂等（BACKEND.md §8.2）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Getter
@Setter
@TableName("iam_workspace_member")
public class WorkspaceMemberEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID（逻辑外键 iam_workspace.id）。 */
    private Long workspaceId;

    /** 用户 ID（逻辑外键 iam_user.id）。 */
    private Long userId;

    /** 角色编码（iam_role.role_code）。 */
    private String roleCode;

    /** 状态：1 正常 0 移除。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
