-- 92_ai_chat_v2.sql：AI 对话增强落地 chat_memory（长期记忆）+ ai_question_gap（问答知识缺口）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/30_ai.sql（建表已含）。
-- 幂等可重跑：CREATE TABLE IF NOT EXISTS，重复执行无副作用。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- AI 对话长期记忆：按工作空间+用户维度保存跨会话记忆摘要（JSON 数组文本），供问答 System Prompt 注入。
CREATE TABLE IF NOT EXISTS `chat_memory` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT      NOT NULL COMMENT '工作空间 ID',
    `user_id`      BIGINT      NOT NULL COMMENT '用户 ID（记忆仅登录用户，访客跳过）',
    `memory_json`  TEXT        NOT NULL COMMENT '记忆条目 JSON 数组（["YYYY-MM-DD HH:mm 主题：要点", ...]，上限 20 条）',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_memory_ws_user` (`workspace_id`, `user_id`)
) ENGINE = InnoDB COMMENT ='AI 对话长期记忆';

-- AI 问答知识缺口：未被知识覆盖的问题（应答无引用）与系统建议的追问，供运营补库与问答洞察。
CREATE TABLE IF NOT EXISTS `ai_question_gap` (
    `id`              BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`    BIGINT      NOT NULL COMMENT '工作空间 ID',
    `user_id`         BIGINT      NOT NULL COMMENT '发起用户 ID（访客不落缺口）',
    `conversation_id` BIGINT      NULL COMMENT '会话 ID（可空）',
    `question`        TEXT        NOT NULL COMMENT '问题文本',
    `source`          VARCHAR(32) NOT NULL COMMENT '来源：ANSWER_UNMATCHED|FOLLOWUP_SUGGESTED',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'UNHANDLED' COMMENT '状态：UNHANDLED|HANDLED',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_question_gap_ws_status` (`workspace_id`, `status`, `created_at`)
) ENGINE = InnoDB COMMENT ='AI 问答知识缺口';