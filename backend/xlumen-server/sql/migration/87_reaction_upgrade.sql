-- 87_reaction_upgrade.sql：互动反应升级迁移（F-0212 知识赞/踩/收藏 + F-0213 评论赞/踩）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/60_engagement.sql，无需本脚本。
-- 背景：eng_like 升级为三态互斥反应（加 reaction_type 列，1=赞 2=踩，存量行默认 1=赞，语义不变）；
--       新增 eng_favorite（知识收藏）与 eng_comment_reaction（评论赞/踩），结构与 eng_like 同构。
-- 幂等可重跑：加列前查 information_schema（MySQL 8.4 ALTER TABLE ADD COLUMN 不支持 IF NOT EXISTS，
--             沿用 85_kb_migration.sql 的存储过程先例），建表用 IF NOT EXISTS；重复执行无副作用。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- ① eng_like 增加 reaction_type 列（幂等：列存在则跳过；存量行取默认值 1=点赞，语义兼容）
DROP PROCEDURE IF EXISTS reaction_mig_add_col;
DELIMITER $$
CREATE PROCEDURE reaction_mig_add_col(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL reaction_mig_add_col('eng_like', 'reaction_type',
    'TINYINT NOT NULL DEFAULT 1 COMMENT ''反应类型：1=点赞 2=点踩'' AFTER `user_id`');

-- ② 知识收藏表 eng_favorite（与 init/60_engagement.sql 一致，IF NOT EXISTS 幂等）
CREATE TABLE IF NOT EXISTS `eng_favorite` (
    `id`           BIGINT   NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT   NOT NULL COMMENT '工作空间 ID',
    `knowledge_id` BIGINT   NOT NULL COMMENT '知识 ID（逻辑外键 cnt_knowledge.id）',
    `user_id`      BIGINT   NOT NULL COMMENT '收藏用户 ID（逻辑外键 iam_user.id）',
    `status`       TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 已收藏 0 取消',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_favorite_ws_knowledge_user` (`workspace_id`, `knowledge_id`, `user_id`),
    KEY `idx_favorite_ws_knowledge` (`workspace_id`, `knowledge_id`)
) ENGINE = InnoDB COMMENT ='知识收藏（F-0212）';

-- ③ 评论反应表 eng_comment_reaction（与 init/60_engagement.sql 一致，IF NOT EXISTS 幂等）
CREATE TABLE IF NOT EXISTS `eng_comment_reaction` (
    `id`            BIGINT   NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id`  BIGINT   NOT NULL COMMENT '工作空间 ID',
    `comment_id`    BIGINT   NOT NULL COMMENT '评论 ID（逻辑外键 eng_comment.id）',
    `user_id`       BIGINT   NOT NULL COMMENT '反应用户 ID（逻辑外键 iam_user.id）',
    `reaction_type` TINYINT  NOT NULL DEFAULT 1 COMMENT '反应类型：1=点赞 2=点踩',
    `status`        TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 活动中 0 已取消',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_reaction_ws_comment_user` (`workspace_id`, `comment_id`, `user_id`),
    KEY `idx_comment_reaction_ws_comment` (`workspace_id`, `comment_id`)
) ENGINE = InnoDB COMMENT ='评论反应：点赞/点踩（F-0213）';

-- ④ 校验：reaction_type 列存在且存量数据均为 1（点赞）
SELECT COUNT(*) AS non_like_rows FROM `eng_like` WHERE `reaction_type` <> 1;

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS reaction_mig_add_col;
