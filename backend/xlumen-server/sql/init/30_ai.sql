-- 30_ai.sql：xlumen-ai 模块 AI 引擎表（ai_ 前缀）
-- M06 落地模型网关（F-0501）/场景模型配置（F-0502）；M12 落地异步任务底座（F-1302）。
-- 密钥不入表：API Key 唯一来源 config/.env（决策 D8），表仅存供应商/模型/参数（管理面 A03 可改）。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- AI 任务（F-1302 异步底座）：任务事实以 MySQL 为准（决策 D6），进度写 Redis 短期状态
CREATE TABLE IF NOT EXISTS `ai_task` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`   BIGINT       NOT NULL COMMENT '工作空间 ID',
    `user_id`        BIGINT       NOT NULL COMMENT '发起用户 ID',
    `scene`          VARCHAR(32)  NOT NULL COMMENT '场景（AiScene：WRITING/REVIEWER/QA/SUMMARY/EMBEDDING）',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'QUEUED' COMMENT '状态：QUEUED/RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/CANCELLED',
    `input_json`     JSON         NULL COMMENT '任务入参快照',
    `result_json`    JSON         NULL COMMENT '任务结果（结构化输出）',
    `error_msg`      VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '失败原因（对外脱敏）',
    `retry_count`    INT          NOT NULL DEFAULT 0 COMMENT '已重试次数（有限重试上限 3）',
    `idempotency_key` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '业务幂等键（重复提交返回已有任务）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_task_ws_status` (`workspace_id`, `status`, `created_at`),
    KEY `idx_ai_task_idem` (`workspace_id`, `idempotency_key`)
) ENGINE = InnoDB COMMENT ='AI 任务（F-1302 异步底座）';

-- 场景模型配置（F-0502）：按场景分配供应商与模型；密钥不入表（决策 D8），连通性测试读 .env
CREATE TABLE IF NOT EXISTS `ai_scene_config` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT      NOT NULL COMMENT '工作空间 ID',
    `scene`        VARCHAR(32) NOT NULL COMMENT '场景（AiScene）',
    `provider`     VARCHAR(32) NOT NULL COMMENT '供应商（BAILIAN/DEEPSEEK/MOCK）',
    `model`        VARCHAR(64) NOT NULL COMMENT '模型名（如 qwen-plus/deepseek-chat）',
    `params_json`  JSON        NULL COMMENT '场景参数（temperature/max_tokens 等，可空）',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_config_ws_scene` (`workspace_id`, `scene`)
) ENGINE = InnoDB COMMENT ='场景模型配置（F-0502）';
