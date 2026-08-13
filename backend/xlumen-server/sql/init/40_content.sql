-- 40_content.sql：xlumen-content 模块（cnt_ 文章主体/版本/可见性）
-- M03 落地公开读所需字段（F-0201 列表/详情、F-0202 分类标签搜索）；M04 落地编辑字段（F-0301 CRUD/F-0302 自动保存/F-0307 可见性）。
-- 完整 8 状态内容状态机（构思→草稿→待审核→已通过→定时发布→已发布→更新中→已下架）随 M10（F-0901）细化流转逻辑。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 文章主体（F-0201/F-0202/F-0307）：公开读过滤条件为 status=已发布(6) AND visibility=公开(1)
CREATE TABLE IF NOT EXISTS `cnt_article` (
    `id`           BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `author_id`    BIGINT       NOT NULL COMMENT '作者用户 ID（逻辑外键 iam_user.id）',
    `author_name`  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '作者名（冗余展示字段，避免跨模块查 iam_user）',
    `title`        VARCHAR(200) NOT NULL COMMENT '标题',
    `summary`      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '摘要',
    `content`      MEDIUMTEXT   NOT NULL COMMENT '正文 Markdown（已发布版本正文快照）',
    `category`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分类（公开筛选维度，F-0202）',
    `tags`         JSON         NULL COMMENT '标签数组（公开筛选维度，F-0202）',
    `status`       TINYINT      NOT NULL DEFAULT 2 COMMENT '状态：1 构思 2 草稿 3 待审核 4 已通过 5 定时发布 6 已发布 7 更新中 8 已下架（F-0901 八状态机）',
    `visibility`   TINYINT      NOT NULL DEFAULT 1 COMMENT '可见性：1 公开 0 私有（F-0307，私有不进公开列表与搜索）',
    `version`      BIGINT       NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁，审核/发布/更新必须校验，冲突 HTTP 409）',
    `view_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '阅读量（F-0203，Redis 防刷后自增）',
    `published_at` DATETIME     NULL COMMENT '发布时间（已发布后非空）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_article_ws_pub` (`workspace_id`, `status`, `visibility`, `published_at`),
    KEY `idx_article_ws_status` (`workspace_id`, `status`, `updated_at`),
    KEY `idx_article_ws_category` (`workspace_id`, `category`),
    KEY `idx_article_author` (`author_id`)
) ENGINE = InnoDB COMMENT ='文章主体（F-0201）';

-- 存量库一次性迁移（M04 执行，仅对旧结构生效；重复执行幂等）：
--   ALTER TABLE cnt_article ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）' AFTER visibility;
--   ALTER TABLE cnt_article ADD KEY idx_article_ws_status (workspace_id, status, updated_at);
--   UPDATE cnt_article SET status = CASE status WHEN 1 THEN 2 WHEN 2 THEN 6 WHEN 3 THEN 8 ELSE status END;
--   UPDATE cnt_article SET status = 2 WHERE status NOT BETWEEN 1 AND 8;
