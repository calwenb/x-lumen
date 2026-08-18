-- 60_engagement.sql：xlumen-publishing 模块互动表（eng_ 前缀：评论/点赞/收藏/读者纠错）
-- M03 落地 F-0203 评论与点赞；读者纠错 eng_feedback 随 M11（F-1001）落地。
-- F-0212 知识赞/踩/收藏：eng_like 加 reaction_type、新增 eng_favorite；F-0213 评论赞/踩：eng_comment_reaction。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 知识评论（F-0203）：parent_id 支持回复；评论计数以本表为准（不冗余到 cnt_knowledge）
CREATE TABLE IF NOT EXISTS `eng_comment` (
    `id`           BIGINT        NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT        NOT NULL COMMENT '工作空间 ID',
    `knowledge_id` BIGINT        NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `user_id`      BIGINT        NOT NULL COMMENT '评论用户 ID（逻辑外键 iam_user.id）',
    `user_name`    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '评论用户名（冗余展示字段）',
    `parent_id`    BIGINT        NULL COMMENT '回复的评论 ID（NULL 为顶级评论）',
    `content`      VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `status`       TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 删除',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_comment_ws_knowledge` (`workspace_id`, `knowledge_id`, `status`),
    KEY `idx_comment_ws_status` (`workspace_id`, `status`)
) ENGINE = InnoDB COMMENT ='知识评论（F-0203）';

-- 知识反应（F-0203/F-0212）：唯一键 uk_like_ws_knowledge_user 承担幂等（BACKEND.md §8.2），
-- 赞/踩共用一行三态互斥（一个用户对一篇知识只有一个活动反应），取消更新 status；reaction_type 区分 1 赞 2 踩
CREATE TABLE IF NOT EXISTS `eng_like` (
    `id`            BIGINT   NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`  BIGINT   NOT NULL COMMENT '工作空间 ID',
    `knowledge_id`  BIGINT   NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `user_id`       BIGINT   NOT NULL COMMENT '点赞用户 ID（逻辑外键 iam_user.id）',
    `reaction_type` TINYINT  NOT NULL DEFAULT 1 COMMENT '反应类型：1=点赞 2=点踩',
    `status`        TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 活动中 0 已取消',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_like_ws_knowledge_user` (`workspace_id`, `knowledge_id`, `user_id`),
    KEY `idx_like_ws_knowledge` (`workspace_id`, `knowledge_id`)
) ENGINE = InnoDB COMMENT ='知识反应：点赞/点踩（F-0203/F-0212）';

-- 知识收藏（F-0212）：与 eng_like 同构（toggle 语义），唯一键 uk_favorite_ws_knowledge_user 幂等
CREATE TABLE IF NOT EXISTS `eng_favorite` (
    `id`           BIGINT   NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT   NOT NULL COMMENT '工作空间 ID',
    `knowledge_id` BIGINT   NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `user_id`      BIGINT   NOT NULL COMMENT '收藏用户 ID（逻辑外键 iam_user.id）',
    `status`       TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 已收藏 0 取消',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_favorite_ws_knowledge_user` (`workspace_id`, `knowledge_id`, `user_id`),
    KEY `idx_favorite_ws_knowledge` (`workspace_id`, `knowledge_id`)
) ENGINE = InnoDB COMMENT ='知识收藏（F-0212）';

-- 评论反应（F-0213）：赞/踩共用一行三态互斥（语义与 eng_like 一致），唯一键 uk_comment_reaction_ws_comment_user 幂等
CREATE TABLE IF NOT EXISTS `eng_comment_reaction` (
    `id`            BIGINT   NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`  BIGINT   NOT NULL COMMENT '工作空间 ID',
    `comment_id`    BIGINT   NOT NULL COMMENT '评论 ID（逻辑外键 eng_comment.id）',
    `user_id`       BIGINT   NOT NULL COMMENT '反应用户 ID（逻辑外键 iam_user.id）',
    `reaction_type` TINYINT  NOT NULL DEFAULT 1 COMMENT '反应类型：1=点赞 2=点踩',
    `status`        TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 活动中 0 已取消',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_reaction_ws_comment_user` (`workspace_id`, `comment_id`, `user_id`),
    KEY `idx_comment_reaction_ws_comment` (`workspace_id`, `comment_id`)
) ENGINE = InnoDB COMMENT ='评论反应：点赞/点踩（F-0213）';

-- 读者纠错（F-1001）：匿名可提交；track_no 业务唯一（uk_feedback_track_no），KEY (workspace_id, knowledge_id, status)
CREATE TABLE IF NOT EXISTS `eng_feedback` (
    `id`           BIGINT        NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT        NOT NULL COMMENT '工作空间 ID',
    `knowledge_id` BIGINT        NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `user_id`      BIGINT        NULL COMMENT '提交用户 ID（逻辑外键 iam_user.id，可空=匿名）',
    `position`     VARCHAR(200)  NULL COMMENT '纠错位置（可空）',
    `problem`      VARCHAR(1000) NOT NULL COMMENT '问题描述',
    `evidence`     VARCHAR(2000) NULL COMMENT '证据/建议（可空）',
    `track_no`     VARCHAR(12)   NOT NULL COMMENT '追踪号（雪花 ID 后 12 位大写字母数字）',
    `status`       TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 待处理 0 已处理',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_feedback_track_no` (`track_no`),
    KEY `idx_feedback_ws_knowledge_status` (`workspace_id`, `knowledge_id`, `status`)
) ENGINE = InnoDB COMMENT ='读者纠错（F-1001）';
