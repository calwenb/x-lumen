-- 90_ai_v2_infra.sql：V2 AI 基建迁移（存量库执行；新装库 30_ai.sql 已含，会因幂等判断自动跳过）
-- ① ai_scene_config 加 prompt/daily_quota ② 新建 ai_call_log
-- 幂等模式：information_schema 判存在后再 ALTER/CREATE，可重复执行。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- ① ai_scene_config.prompt
SET @x := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = 'xlumen_dev' AND TABLE_NAME = 'ai_scene_config' AND COLUMN_NAME = 'prompt');
SET @s := IF(@x = 0,
    'ALTER TABLE `ai_scene_config` ADD COLUMN `prompt` TEXT NULL COMMENT ''场景提示词（覆盖默认；WRITING 为 JSON，其余纯文本，空=回退常量）'' AFTER `params_json`',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ② ai_scene_config.daily_quota
SET @x := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = 'xlumen_dev' AND TABLE_NAME = 'ai_scene_config' AND COLUMN_NAME = 'daily_quota');
SET @s := IF(@x = 0,
    'ALTER TABLE `ai_scene_config` ADD COLUMN `daily_quota` INT NOT NULL DEFAULT 0 COMMENT ''每日调用配额（0=不限）'' AFTER `prompt`',
    'SELECT 1');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ③ ai_call_log 表
CREATE TABLE IF NOT EXISTS `ai_call_log` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`   BIGINT       NOT NULL COMMENT '工作空间 ID',
    `user_id`        BIGINT       NULL COMMENT '发起用户 ID（访客为空）',
    `scene`          VARCHAR(32)  NOT NULL COMMENT '场景（AiScene）',
    `task_id`        BIGINT       NULL COMMENT '关联任务 ID（可空）',
    `request_type`   VARCHAR(24)  NOT NULL DEFAULT 'CHAT' COMMENT '调用形态：CHAT/CHAT_STREAM/CHAT_TOOLS/CHAT_STREAM_TOOLS',
    `provider`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '供应商（BAILIAN/DEEPSEEK/空=脚本模型）',
    `model`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '模型名',
    `prompt_hash`    VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '生效 System Prompt 的 SHA1（前 12 位）',
    `tokens_in`      INT          NOT NULL DEFAULT 0 COMMENT '输入 Token 数',
    `tokens_out`     INT          NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
    `est_cost`       DECIMAL(10,6) NOT NULL DEFAULT 0 COMMENT '费用估算（元）',
    `success`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功',
    `degraded`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否降级（脚本模型回退）',
    `latency_ms`     INT          NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `error_msg`      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因（脱敏截断）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_call_log_ws_time` (`workspace_id`, `created_at`),
    KEY `idx_call_log_ws_scene` (`workspace_id`, `scene`)
) ENGINE = InnoDB COMMENT ='AI 调用追踪（F-0505）';