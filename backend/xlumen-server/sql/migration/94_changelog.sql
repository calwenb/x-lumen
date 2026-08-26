-- 94_changelog.sql：站点更新日志表（存量库执行；新装库 95_changelog.sql 已含，幂等跳过）
USE `xlumen_dev`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `plt_changelog` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`  BIGINT       NOT NULL COMMENT '工作空间 ID',
    `title`         VARCHAR(200) NOT NULL COMMENT '标题',
    `content`       MEDIUMTEXT   NOT NULL COMMENT '正文（Markdown）',
    `published`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否发布（0 草稿 1 已发布）',
    `published_at`  DATETIME     NULL COMMENT '发布时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_changelog_ws_pub` (`workspace_id`, `published`, `published_at`)
) ENGINE = InnoDB COMMENT ='站点更新日志（F-0222）';