-- 85_platform.sql：xlumen-identity 模块平台治理域（plt_ 前缀）——M13 落地 F-1202 审计日志
-- 只增不改（append-only）：审计日志仅 INSERT，不提供 UPDATE/DELETE。
-- 表命名规则：单 Schema、无外键、主键 BIGINT 雪花 ID、业务表含 workspace_id（BACKEND.md §7/§8）。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 平台操作审计日志（F-1202）：operator_id 可空（系统/定时任务触发）；KEY (workspace_id, created_at)
CREATE TABLE IF NOT EXISTS `plt_activity_log` (
    `id`            BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`  BIGINT      NOT NULL COMMENT '工作空间 ID',
    `operator_id`   BIGINT      NULL COMMENT '操作人用户 ID（逻辑外键 iam_user.id，可空=系统触发）',
    `operator_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '操作人名称（冗余展示字段）',
    `action`        VARCHAR(64) NOT NULL COMMENT '操作类型（如 REVIEW_REJECT/ARTICLE_PUBLISH/WORKSPACE_SETTINGS_UPDATE）',
    `target_type`   VARCHAR(32) NOT NULL DEFAULT '' COMMENT '目标类型（如 REVIEW/ARTICLE/WORKSPACE）',
    `target_id`     BIGINT      NULL COMMENT '目标 ID',
    `detail_json`   TEXT        NULL COMMENT '操作详情（JSON 文本，可空）',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_activity_ws_created` (`workspace_id`, `created_at`)
) ENGINE = InnoDB COMMENT ='平台操作审计日志（F-1202）';
