-- 60_engagement.sql：xlumen-publishing 模块 engagement 域（eng_ 评论/点赞/读者纠错）
-- M03 落地 F-0203 评论与点赞；读者纠错 eng_feedback 随 M11（F-1001）落地。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 文章评论（F-0203）：parent_id 支持回复；评论计数以本表为准（不冗余到 cnt_article）
CREATE TABLE IF NOT EXISTS `eng_comment` (
    `id`           BIGINT        NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT        NOT NULL COMMENT '工作空间 ID',
    `article_id`   BIGINT        NOT NULL COMMENT '文章 ID（逻辑外键 cnt_article.id）',
    `user_id`      BIGINT        NOT NULL COMMENT '评论用户 ID（逻辑外键 iam_user.id）',
    `user_name`    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '评论用户名（冗余展示字段）',
    `parent_id`    BIGINT        NULL COMMENT '回复的评论 ID（NULL 为顶级评论）',
    `content`      VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `status`       TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 删除',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_comment_ws_article` (`workspace_id`, `article_id`, `status`),
    KEY `idx_comment_ws_status` (`workspace_id`, `status`)
) ENGINE = InnoDB COMMENT ='文章评论（F-0203）';

-- 文章点赞（F-0203）：唯一键 uk_like_ws_article_user 承担幂等（BACKEND.md §8.2），点赞/取消更新 status
CREATE TABLE IF NOT EXISTS `eng_like` (
    `id`           BIGINT   NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT   NOT NULL COMMENT '工作空间 ID',
    `article_id`   BIGINT   NOT NULL COMMENT '文章 ID（逻辑外键 cnt_article.id）',
    `user_id`      BIGINT   NOT NULL COMMENT '点赞用户 ID（逻辑外键 iam_user.id）',
    `status`       TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 已赞 0 取消',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_like_ws_article_user` (`workspace_id`, `article_id`, `user_id`),
    KEY `idx_like_ws_article` (`workspace_id`, `article_id`)
) ENGINE = InnoDB COMMENT ='文章点赞（F-0203）';
