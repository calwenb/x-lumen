package com.calwen.xlumen.identity.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户实体（iam_user，F-0101）：密码 BCrypt 哈希存储，仅对应数据库结构（BACKEND.md §5.1）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Getter
@Setter
@TableName("iam_user")
public class UserEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录用户名。 */
    private String username;

    /** 邮箱（唯一，可空；MVP 登录用用户名）。 */
    private String email;

    /** 密码 BCrypt 哈希。 */
    private String passwordHash;

    /** 状态：1 正常 0 禁用。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
