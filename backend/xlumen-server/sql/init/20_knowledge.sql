-- 20_knowledge.sql：xlumen-knowledge 模块（kb_ 知识库/目录 + 知识索引 RAG，决策 D13/D16）
-- M05 落地 F-0402（发布即索引）、F-0403（索引多版本）、F-0404（检索测试）、F-0405（引用溯源）、F-0407（权限过滤）。
-- KB-2 落地 F-0308 知识库、F-0309 目录树（kb_knowledge_base/kb_directory）；kb_chunk/kb_index_version 增加 kb_id 维度（D13 按库切分）。
-- 表清单：kb_knowledge_base（知识库）、kb_directory（多级目录树）、kb_chunk（切片元数据）、kb_index_version（索引版本与活动指针）。
-- 主键由应用侧雪花生成（IdUtil），本脚本不设置 AUTO_INCREMENT。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 知识库（F-0308，决策 D16）：可见性库级决定（0 私有/1 公开）；回收站用 status+deleted_at（不扩 8 状态机）
CREATE TABLE IF NOT EXISTS `kb_knowledge_base` (
    `id`           BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `name`         VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `intro`        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '简介',
    `cover`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '封面 URL（V2 接入 MinIO 前可空）',
    `visibility`   TINYINT      NOT NULL DEFAULT 0 COMMENT '可见性：0 私有 1 公开（库级统一决定知识可见范围）',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 回收站',
    `deleted_at`   DATETIME     NULL COMMENT '进回收站时间（超期清理依据，默认保留 30 天）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_ws_name` (`workspace_id`, `name`),
    KEY `idx_kb_ws_vis` (`workspace_id`, `visibility`)
) ENGINE = InnoDB COMMENT ='知识库（F-0308）';

-- 目录树（F-0309）：parent_id 多级自关联（0=库根）；列表按名称排序（数据库排序规则，不设拼音列）
CREATE TABLE IF NOT EXISTS `kb_directory` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `kb_id`      BIGINT      NOT NULL COMMENT '所属知识库 ID（逻辑外键 kb_knowledge_base.id）',
    `parent_id`  BIGINT      NOT NULL DEFAULT 0 COMMENT '父目录 ID（0=根目录）',
    `name`       VARCHAR(64) NOT NULL COMMENT '目录名称',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dir_kb_parent` (`kb_id`, `parent_id`)
) ENGINE = InnoDB COMMENT ='目录树（F-0309）';

-- 切片元数据（F-0402/F-0405）：正文按标题边界切片落库，vector_id 指向向量库条目（Noop 降级时留空）；kb_id 为检索按库过滤维度（D13）
CREATE TABLE IF NOT EXISTS `kb_chunk` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`   BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `kb_id`          BIGINT       NOT NULL COMMENT '所属知识库 ID（逻辑外键 kb_knowledge_base.id，检索按库过滤）',
    `knowledge_id`   BIGINT       NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `version`        BIGINT       NOT NULL COMMENT '发布版本号（关联 kb_index_version.version）',
    `chunk_seq`      INT          NOT NULL COMMENT '切片序号（从 1 开始，引用溯源定位）',
    `heading_anchor` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '段落标题锚点（Markdown 标题，跳转原文定位）',
    `content_hash`   CHAR(64)     NOT NULL COMMENT '正文 SHA-256 哈希（幂等检查：同一 hash 已索引则跳过）',
    `vector_id`      VARCHAR(128) NULL COMMENT '向量库条目 ID（Noop 降级时留空）',
    `chunk_text`     TEXT         NOT NULL COMMENT '切片文本（约 400~500 字）',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 有效 0 失效',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chunk_knowledge_version` (`workspace_id`, `knowledge_id`, `version`),
    KEY `idx_chunk_knowledge_hash` (`workspace_id`, `knowledge_id`, `content_hash`),
    KEY `idx_chunk_kb_status` (`kb_id`, `status`)
) ENGINE = InnoDB COMMENT ='切片元数据（F-0402/F-0405）';

-- 索引版本与活动指针（F-0403）：同一知识多版本并存，仅一条 ACTIVE 为当前生效索引；kb_id 按库切分（D13）
CREATE TABLE IF NOT EXISTS `kb_index_version` (
    `id`              BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`    BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `kb_id`           BIGINT       NOT NULL COMMENT '所属知识库 ID（逻辑外键 kb_knowledge_base.id，按库切分）',
    `knowledge_id`    BIGINT       NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `version`         BIGINT       NOT NULL COMMENT '发布版本号（关联 cnt_knowledge.version）',
    `index_name`      VARCHAR(128) NOT NULL COMMENT '向量索引名（Milvus 集合名）',
    `embedding_model` VARCHAR(64)  NOT NULL COMMENT 'Embedding 模型名',
    `status`          VARCHAR(16)  NOT NULL COMMENT '状态：ACTIVATING 索引中 / ACTIVE 已激活 / STALE 已失效',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_index_ws_knowledge` (`workspace_id`, `knowledge_id`),
    KEY `idx_index_ws_knowledge_status` (`workspace_id`, `knowledge_id`, `status`),
    KEY `idx_index_kb_status` (`kb_id`, `status`)
) ENGINE = InnoDB COMMENT ='索引版本与活动指针（F-0403）';
