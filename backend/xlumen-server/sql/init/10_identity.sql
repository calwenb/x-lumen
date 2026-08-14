-- 10_identity.sql：xlumen-identity 模块 iam 域（用户/会话/工作空间/成员/角色）——M02 身份与多租户
-- 表命名规则：单 Schema、无外键、主键 BIGINT 雪花 ID、业务表含 workspace_id 与 (workspace_id, status) 联合索引、唯一键 uk_ 前缀（BACKEND.md §7/§8）。
-- 主键由应用侧 IdUtil 雪花生成，本脚本不设置 AUTO_INCREMENT。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 角色定义（F-0103）：OWNER/ADMIN/EDITOR/AUTHOR/VISITOR；团队角色（EDITOR/ADMIN/AUTHOR）V2 启用（决策 D9），定义先行入库
CREATE TABLE IF NOT EXISTS `iam_role` (
    `id`          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `role_code`   VARCHAR(32)  NOT NULL COMMENT '角色编码',
    `role_name`   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '角色说明',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB COMMENT ='角色定义（F-0103）';

-- 用户（F-0101）：密码 BCrypt 哈希存储（BACKEND.md §15.3）；邮箱唯一可空（MVP 登录用用户名）
CREATE TABLE IF NOT EXISTS `iam_user` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录用户名',
    `email`         VARCHAR(128) NULL COMMENT '邮箱（唯一，可空）',
    `password_hash` VARCHAR(100) NOT NULL COMMENT '密码 BCrypt 哈希',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`)
) ENGINE = InnoDB COMMENT ='用户（F-0101）';

-- 工作空间（F-0102）：注册即建空间（决策 D9）；slug 承担业务唯一约束（uk_workspace_slug，BACKEND.md §8.2）
CREATE TABLE IF NOT EXISTS `iam_workspace` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `name`         VARCHAR(64) NOT NULL COMMENT '空间名称',
    `slug`         VARCHAR(64) NOT NULL COMMENT '空间标识（唯一）',
    `owner_user_id` BIGINT     NOT NULL COMMENT '所有者用户 ID（逻辑外键 iam_user.id）',
    `status`       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workspace_slug` (`slug`),
    KEY `idx_workspace_owner_status` (`owner_user_id`, `status`)
) ENGINE = InnoDB COMMENT ='工作空间（F-0102）';

-- 空间成员（F-0102/F-0103）：成员角色绑定，唯一键 uk_workspace_member 承担幂等（BACKEND.md §8.2）
CREATE TABLE IF NOT EXISTS `iam_workspace_member` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT      NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `user_id`      BIGINT      NOT NULL COMMENT '用户 ID（逻辑外键 iam_user.id）',
    `role_code`    VARCHAR(32) NOT NULL COMMENT '角色编码（iam_role.role_code）',
    `status`       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 移除',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workspace_member` (`workspace_id`, `user_id`),
    KEY `idx_member_ws_status` (`workspace_id`, `status`),
    KEY `idx_member_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='空间成员角色绑定（F-0102/F-0103）';

-- 系统数据：内置角色定义（不含演示用户/演示内容，BACKEND.md §7）
INSERT INTO `iam_role` (`id`, `role_code`, `role_name`, `description`) VALUES
    (1, 'OWNER',   '空间所有者', '管理工作空间、成员、模型配置、配额与高风险操作；注册即建空间默认角色（决策 D9）'),
    (2, 'ADMIN',   '管理员',     '管理知识库、内容规则、发布设置与平台治理（V2 团队模式启用）'),
    (3, 'EDITOR',  '编辑',       '审核、驳回、定时发布、回滚、下架（V2 团队模式启用）'),
    (4, 'AUTHOR',  '作者',       '导入资料、发起创作、修订知识并提交审核（V2 团队模式启用）'),
    (5, 'VISITOR', '访客',       '阅读、搜索、评论、点赞、提交纠错（公开博客访问者）')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `description` = VALUES(`description`);
