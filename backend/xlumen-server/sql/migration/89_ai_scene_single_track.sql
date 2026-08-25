-- 89_ai_scene_single_track.sql：AI 双轨合单轨迁移（QA/写作/审校一律走 Agent 路径）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/ 脚本，无需本脚本。
-- 本脚本独立于 sql/init/ 目录，由人工/CI 显式执行。
-- 幂等可重跑：所有变更前先查 information_schema，重复执行无副作用。
-- 范围：①删除已废弃的 ai_scene_config.agent_enabled 列（存量已加列库才有）；②清理已移除的 EMBEDDING 场景残留行。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- ============================================================
-- ① 删除 ai_scene_config.agent_enabled 列（双轨合并后废弃；未加列的库跳过）
-- ============================================================
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_scene_config' AND COLUMN_NAME = 'agent_enabled'
);
SET @ddl_drop_col := IF(@col_exists > 0,
    'ALTER TABLE ai_scene_config DROP COLUMN agent_enabled',
    'SELECT 1 AS noop');
PREPARE stmt_drop_col FROM @ddl_drop_col;
EXECUTE stmt_drop_col;
DEALLOCATE PREPARE stmt_drop_col;

-- ============================================================
-- ② 清理 EMBEDDING 场景残留行（AiScene.EMBEDDING 已移除，向量化配置改由 knowledge 模块 .env 读）
-- ============================================================
DELETE FROM ai_scene_config WHERE scene = 'EMBEDDING';
