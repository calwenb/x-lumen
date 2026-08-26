-- 91_review_ai_status.sql：pub_review 增加 AI 任务状态镜像列（ai_status/ai_error），展示走表快照
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/ 脚本，无需本脚本。
-- 幂等可重跑：先查 information_schema 判断列是否存在再 ALTER，重复执行无副作用。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- ① ai_status：AI 任务状态镜像（事件驱动，COMPLETED/FAILED 等，展示用，避免每次请求实时回查 AI 模块）
SET @x := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pub_review' AND COLUMN_NAME = 'ai_status');
SET @s := IF(@x = 0,
    'ALTER TABLE pub_review ADD COLUMN ai_status VARCHAR(16) NULL COMMENT ''AI 任务状态镜像（事件驱动，COMPLETED/FAILED 等，展示用）'' AFTER ai_task_id',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ② ai_error：AI 失败原因镜像（事件驱动，展示用，可空）
SET @x := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pub_review' AND COLUMN_NAME = 'ai_error');
SET @s := IF(@x = 0,
    'ALTER TABLE pub_review ADD COLUMN ai_error VARCHAR(500) NULL COMMENT ''AI 失败原因镜像（事件驱动，展示用）'' AFTER ai_status',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;