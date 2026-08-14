-- 20_knowledge.sql：xlumen-knowledge 模块（kb_ 知识索引 RAG，发布即索引，决策 D13）
-- M05 落地 F-0402（发布即索引）、F-0403（索引多版本）、F-0404（检索测试）、F-0405（引用溯源）、F-0407（权限过滤）。
-- 表清单：kb_chunk（切片元数据）、kb_index_version（索引版本与活动指针）。
-- 主键由应用侧雪花生成（IdUtil），本脚本不设置 AUTO_INCREMENT。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- 切片元数据（F-0402/F-0405）：正文按标题边界切片落库，vector_id 指向向量库条目（Noop 降级时留空）
CREATE TABLE IF NOT EXISTS `kb_chunk` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`   BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
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
    KEY `idx_chunk_knowledge_hash` (`workspace_id`, `knowledge_id`, `content_hash`)
) ENGINE = InnoDB COMMENT ='切片元数据（F-0402/F-0405）';

-- 索引版本与活动指针（F-0403）：同一知识多版本并存，仅一条 ACTIVE 为当前生效索引
CREATE TABLE IF NOT EXISTS `kb_index_version` (
    `id`              BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`    BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `knowledge_id`    BIGINT       NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `version`         BIGINT       NOT NULL COMMENT '发布版本号（关联 cnt_knowledge.version）',
    `index_name`      VARCHAR(128) NOT NULL COMMENT '向量索引名（Milvus 集合名）',
    `embedding_model` VARCHAR(64)  NOT NULL COMMENT 'Embedding 模型名',
    `status`          VARCHAR(16)  NOT NULL COMMENT '状态：ACTIVATING 索引中 / ACTIVE 已激活 / STALE 已失效',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_index_ws_knowledge` (`workspace_id`, `knowledge_id`),
    KEY `idx_index_ws_knowledge_status` (`workspace_id`, `knowledge_id`, `status`)
) ENGINE = InnoDB COMMENT ='索引版本与活动指针（F-0403）';
