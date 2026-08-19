-- 40_content.sql：xlumen-content 模块（cnt_ 知识主体）
-- M03 落地公开读所需字段（F-0201 列表/详情、F-0202 标签搜索）；M04 落地编辑字段（F-0301 CRUD/F-0302 自动保存）；KB-2 落地库/目录归属与回收站（F-0308/F-0309/F-0305，删除文章级 category/visibility，决策 D16）。
-- 完整 8 状态内容状态机（构思→草稿→待审核→已通过→定时发布→已发布→更新中→已下架）随 M10（F-0901）细化流转逻辑。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 知识主体（F-0201/F-0301，决策 D16）：归属单库单目录（kb_id+directory_id）；可见性由所属知识库决定（无独立 visibility 列）；回收站用 recycle_status+deleted_at（不扩 8 状态机）
CREATE TABLE IF NOT EXISTS `cnt_knowledge` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`   BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `author_id`      BIGINT       NOT NULL COMMENT '作者用户 ID（逻辑外键 iam_user.id）',
    `author_name`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '作者名（冗余展示字段，避免跨模块查 iam_user）',
    `kb_id`          BIGINT       NOT NULL COMMENT '所属知识库 ID（逻辑外键 kb_knowledge_base.id，单库单目录，决策 D16）',
    `directory_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '所属目录 ID（逻辑外键 kb_directory.id，0=库根目录）',
    `title`          VARCHAR(200) NOT NULL COMMENT '标题',
    `summary`        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '摘要',
    `content`        MEDIUMTEXT   NOT NULL COMMENT '正文 Markdown（已发布版本正文快照）',
    `tags`           JSON         NULL COMMENT '标签数组（公开筛选维度，F-0202）',
    `status`         TINYINT      NOT NULL DEFAULT 2 COMMENT '状态：1 构思 2 草稿 3 待审核 4 已通过 5 定时发布 6 已发布 7 更新中 8 已下架（F-0901 八状态机）',
    `version`        BIGINT       NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁，审核/发布/更新必须校验，冲突 HTTP 409）',
    `view_count`     BIGINT       NOT NULL DEFAULT 0 COMMENT '阅读量（F-0203，Redis 防刷后自增）',
    `recycle_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '回收站状态：0 正常 1 回收站（F-0305，独立软删标记，不扩状态机）',
    `deleted_at`     DATETIME     NULL COMMENT '进回收站时间（超期 30 天清理依据）',
    `published_at`   DATETIME     NULL COMMENT '发布时间（已发布后非空）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_ws_status` (`workspace_id`, `status`, `updated_at`),
    KEY `idx_knowledge_kb_dir` (`workspace_id`, `kb_id`, `directory_id`, `status`),
    KEY `idx_knowledge_author` (`author_id`)
) ENGINE = InnoDB COMMENT ='知识主体（F-0201）';

-- 存量库一次性迁移（M04 执行，仅对旧结构生效；重复执行幂等）：
--   ALTER TABLE cnt_knowledge ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）' AFTER visibility;
--   ALTER TABLE cnt_knowledge ADD KEY idx_knowledge_ws_status (workspace_id, status, updated_at);
--   UPDATE cnt_knowledge SET status = CASE status WHEN 1 THEN 2 WHEN 2 THEN 6 WHEN 3 THEN 8 ELSE status END;
--   UPDATE cnt_knowledge SET status = 2 WHERE status NOT BETWEEN 1 AND 8;

-- 知识版本快照表（F-0303 历史版本，BUG-014 补全）：每次落库（创建/更新/自动保存）记录当时标题/正文快照
CREATE TABLE IF NOT EXISTS `cnt_knowledge_version` (
    `id`           BIGINT     NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT     NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `knowledge_id` BIGINT     NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `version`      BIGINT     NOT NULL COMMENT '版本号（对应 cnt_knowledge.version 落库后的值）',
    `title`        VARCHAR(200) NOT NULL COMMENT '标题快照',
    `content`      MEDIUMTEXT NOT NULL COMMENT '正文 Markdown 快照',
    `created_at`   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_version_kid_version` (`knowledge_id`, `version`)
) ENGINE = InnoDB COMMENT ='知识版本快照（F-0303）';
