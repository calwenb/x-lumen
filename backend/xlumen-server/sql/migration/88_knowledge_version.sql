-- 88_knowledge_version.sql：知识版本快照表迁移（F-0303 历史版本，BUG-014 补全）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/40_content.sql，无需本脚本。
-- 背景：cnt_knowledge_version 此前在 BUGS.md 中被误记为「8-12 M04 已建表」，实际全仓无该表 DDL；
--       本次补全建表 + 发布/保存时快照写入 + GET /api/v1/knowledge/{id}/versions 查询端点。
-- 幂等可重跑：CREATE TABLE IF NOT EXISTS，重复执行无副作用。

USE `xlumen_dev`;
SET NAMES utf8mb4;

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
