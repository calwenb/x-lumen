-- 70_chat.sql：xlumen-ai 模块 chat 域（chat_ AI 对话/知识级问答/访客助手）
-- M08（F-0701~F-0702）落地：chat_conversation（会话）+ chat_message（消息）。
-- 消息 citations_json 为检索证据快照（SearchResultDTO 数组），支持引用溯源跳转原文。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- AI 对话会话（F-0701）：一个工作空间下的多轮对话容器，标题取首条提问截断。
CREATE TABLE IF NOT EXISTS `chat_conversation` (
    `id`           BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT       NOT NULL COMMENT '工作空间 ID',
    `user_id`      BIGINT       NULL COMMENT '用户 ID（访客会话为 NULL）',
    `title`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '会话标题（首条提问截断）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_conv_ws_user` (`workspace_id`, `user_id`, `updated_at`)
) ENGINE = InnoDB COMMENT ='AI 对话会话（F-0701）';

-- AI 对话消息（F-0701/F-0702）：role=USER|ASSISTANT；citations_json 为引用证据快照。
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`              BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `conversation_id` BIGINT       NOT NULL COMMENT '会话 ID（逻辑外键 chat_conversation.id）',
    `workspace_id`    BIGINT       NOT NULL COMMENT '工作空间 ID',
    `user_id`         BIGINT       NULL COMMENT '用户 ID（访客消息为 NULL）',
    `role`            VARCHAR(16)  NOT NULL COMMENT '角色：USER|ASSISTANT',
    `content`         MEDIUMTEXT   NOT NULL COMMENT '消息内容',
    `citations_json`  JSON         NULL COMMENT '引用证据（SearchResultDTO 数组快照，可空）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_msg_conv` (`conversation_id`, `created_at`)
) ENGINE = InnoDB COMMENT ='AI 对话消息（F-0701/F-0702）';
