-- 80_ai_enhance.sql：xlumen-ai 模块 enhance 域（ai_enhance_ 摘要/SEO）
-- M09（F-0801~F-0802）落地：ai_enhance_result（结构化增值结果，同步生成后落库）。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- AI 增值结果（F-0801/F-0802）：摘要/SEO 结构化结果，同步生成后落库供前台复用。
CREATE TABLE IF NOT EXISTS `ai_enhance_result` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT      NOT NULL COMMENT '工作空间 ID',
    `article_id`   BIGINT      NULL COMMENT '文章 ID（可空，供独立增强）',
    `scene`        VARCHAR(32) NOT NULL COMMENT '场景：SUMMARY|SEO',
    `result_json`  JSON        NOT NULL COMMENT '结构化结果',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_enhance_ws_article` (`workspace_id`, `article_id`, `created_at`)
) ENGINE = InnoDB COMMENT ='AI 增值结果（F-0801/F-0802）';
