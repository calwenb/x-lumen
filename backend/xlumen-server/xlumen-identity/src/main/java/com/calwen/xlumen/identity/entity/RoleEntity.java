package com.calwen.xlumen.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色定义实体（iam_role，F-0103）：OWNER/ADMIN/EDITOR/AUTHOR/VISITOR，系统数据入库。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Getter
@Setter
@TableName("iam_role")
public class RoleEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色编码（唯一）。 */
    private String roleCode;

    /** 角色名称。 */
    private String roleName;

    /** 角色说明。 */
    private String description;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
