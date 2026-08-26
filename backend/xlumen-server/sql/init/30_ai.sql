-- 30_ai.sql：xlumen-ai 模块 AI 引擎表（ai_ 前缀）
-- M06 落地模型网关/场景模型配置；M12 落地异步任务底座。
-- 密钥不入表：API Key 唯一来源 config/.env（决策 D8），表仅存供应商/模型/参数（管理面 A03 可改）。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- AI 任务：任务事实以 MySQL 为准（决策 D6），进度写 Redis 短期状态
CREATE TABLE IF NOT EXISTS `ai_task` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`   BIGINT       NOT NULL COMMENT '工作空间 ID',
    `user_id`        BIGINT       NOT NULL COMMENT '发起用户 ID',
    `scene`          VARCHAR(32)  NOT NULL COMMENT '场景（AiScene：WRITING/REVIEWER/QA/SUMMARY/SEO）',
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

-- 场景模型配置：按场景分配供应商与模型；密钥不入表（决策 D8），连通性测试读 .env。
-- 双轨合并后 agent_enabled 列已废弃（不在此建）；存量库删列见 sql/migration/89_ai_scene_single_track.sql。
-- prompt 列承载场景提示词（WRITING 为多槽位 JSON：outline/chapter/self_review/revise，其余为纯文本，空=回退常量默认）；
-- daily_quota 为工作空间按场景的每日调用配额（0=不限）。
CREATE TABLE IF NOT EXISTS `ai_scene_config` (
    `id`            BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`  BIGINT      NOT NULL COMMENT '工作空间 ID',
    `scene`         VARCHAR(32) NOT NULL COMMENT '场景（AiScene）',
    `provider`      VARCHAR(32) NOT NULL COMMENT '供应商（BAILIAN/DEEPSEEK/MOCK）',
    `model`         VARCHAR(64) NOT NULL COMMENT '模型名（如 qwen-plus/deepseek-chat）',
    `params_json`   JSON        NULL COMMENT '场景参数（temperature/max_tokens 等，可空）',
    `prompt`        TEXT        NULL COMMENT '场景提示词（覆盖默认；WRITING 为 JSON，其余纯文本，空=回退常量）',
    `daily_quota`   INT         NOT NULL DEFAULT 0 COMMENT '每日调用配额（0=不限）',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_config_ws_scene` (`workspace_id`, `scene`)
) ENGINE = InnoDB COMMENT ='场景模型配置（F-0502）';

-- AI 调用追踪：记录每次 LLM 调用的模型/Prompt 版本/Token/费用/耗时/成败/降级，供管理面排障与用量统计。
CREATE TABLE IF NOT EXISTS `ai_call_log` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`   BIGINT       NOT NULL COMMENT '工作空间 ID',
    `user_id`        BIGINT       NULL COMMENT '发起用户 ID（访客为空）',
    `scene`          VARCHAR(32)  NOT NULL COMMENT '场景（AiScene，EMBEDDING 不入此表）',
    `task_id`        BIGINT       NULL COMMENT '关联任务 ID（异步任务路径，可空）',
    `request_type`   VARCHAR(24)  NOT NULL DEFAULT 'CHAT' COMMENT '调用形态：CHAT/CHAT_STREAM/CHAT_TOOLS/CHAT_STREAM_TOOLS',
    `provider`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '供应商（BAILIAN/DEEPSEEK/空=脚本模型）',
    `model`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '模型名',
    `prompt_hash`    VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '生效 System Prompt 的 SHA1（前 12 位，版本溯源）',
    `tokens_in`      INT          NOT NULL DEFAULT 0 COMMENT '输入 Token 数（供应商未返回时为 0）',
    `tokens_out`     INT          NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
    `est_cost`       DECIMAL(10,6) NOT NULL DEFAULT 0 COMMENT '费用估算（元，tokens × 单价/1000）',
    `success`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功',
    `degraded`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否降级（脚本模型/无密钥回退）',
    `latency_ms`     INT          NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `error_msg`      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因（脱敏截断）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_call_log_ws_time` (`workspace_id`, `created_at`),
    KEY `idx_call_log_ws_scene` (`workspace_id`, `scene`)
) ENGINE = InnoDB COMMENT ='AI 调用追踪（F-0505）';

-- 存量库执行（新装库无需，建表已不含）：agent_enabled 列于双轨合并后废弃，删列见 sql/migration/89_ai_scene_single_track.sql。
