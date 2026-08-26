-- 90_notification.sql：xlumen-notification 模块站内消息（noti_ 前缀）
-- 与审核业务解耦：评论回复/@小光等事件可复用同一套站内信能力。

USE `xlumen_dev`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `noti_notification` (
    `id`           BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT       NOT NULL COMMENT '工作空间 ID',
    `user_id`      BIGINT       NOT NULL COMMENT '接收用户 ID',
    `type`         VARCHAR(32)  NOT NULL COMMENT '事件类型（REVIEW=AI 审核结果，后续事件复用）',
    `title`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '标题',
    `content`      VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '摘要内容',
    `link`         VARCHAR(255) NOT NULL DEFAULT '' COMMENT '跳转链接（前端路由）',
    `read_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读（0 未读 1 已读）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_noti_user_read` (`user_id`, `read_flag`, `created_at`)
) ENGINE = InnoDB COMMENT ='站内消息（IDEA-024 通用通知模块）';