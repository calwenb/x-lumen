-- 93_comment_ai_reply.sql：eng_comment 增加 AI 回复标识与引用溯源列（is_ai / citations_json）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/ 脚本，无需本脚本。
-- 幂等可重跑：先查 information_schema 判断列是否存在再 ALTER，重复执行无副作用。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- ① is_ai：AI 自动回复标识（1 小光回复 0 人工，存量行默认 0）
SET @x := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eng_comment' AND COLUMN_NAME = 'is_ai');
SET @s := IF(@x = 0,
    'ALTER TABLE eng_comment ADD COLUMN is_ai TINYINT NOT NULL DEFAULT 0 COMMENT ''AI 自动回复标识：1 小光回复 0 人工'' AFTER content',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ② citations_json：AI 回复引用溯源（JSON 数组：命中片段 knowledgeId/title/headingAnchor/chunkText/score）
SET @x := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eng_comment' AND COLUMN_NAME = 'citations_json');
SET @s := IF(@x = 0,
    'ALTER TABLE eng_comment ADD COLUMN citations_json JSON NULL COMMENT ''AI 回复引用溯源（JSON 数组：命中片段 knowledgeId/title/headingAnchor/chunkText/score）'' AFTER is_ai',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;