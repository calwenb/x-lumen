-- 85_kb_migration.sql：知识平台化重构存量迁移（KB-1 概念改名 + KB-2 结构变更）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/ 脚本，无需本脚本。
-- 本脚本独立于 sql/init/ 目录（init 已有 85_platform.sql，编号冲突），由人工/CI 显式执行。
-- 幂等可重跑：所有变更前先查 information_schema / 数据状态，重复执行无副作用。
-- KB-1 范围：物理表改名 cnt_article→cnt_knowledge；article_id/article_title 列改名；索引/唯一键改名；注释同步。
-- KB-2 范围：kb_knowledge_base/kb_directory 建表；cnt_knowledge 增加 kb_id/directory_id/recycle_status/deleted_at、
--          删除 category/visibility；kb_chunk/kb_index_version 增加 kb_id；存量知识归默认库、category 平铺目录。

USE `xlumen_dev`;
SET NAMES utf8mb4;

-- ============================================================
-- 第一部分：KB-1 概念改名（幂等）
-- ============================================================

-- ① 物理表改名（幂等：仅当旧表存在且新表不存在时执行）
SET @old_tbl = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cnt_article');
SET @new_tbl = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cnt_knowledge');
SET @ddl = IF(@old_tbl = 1 AND @new_tbl = 0, 'RENAME TABLE `cnt_article` TO `cnt_knowledge`', 'SELECT ''skip rename cnt_article''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② 列改名存储过程（幂等：仅当旧列存在且新列不存在时执行）
DROP PROCEDURE IF EXISTS kb_mig_rename_col;
DELIMITER $$
CREATE PROCEDURE kb_mig_rename_col(IN p_table VARCHAR(64), IN p_old_col VARCHAR(64), IN p_new_col VARCHAR(64), IN p_col_def VARCHAR(500))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_old_col)
       AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_new_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` CHANGE COLUMN `', p_old_col, '` `', p_new_col, '` ', p_col_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ③ 索引改名存储过程（幂等：仅当旧索引存在且新索引不存在时执行）
DROP PROCEDURE IF EXISTS kb_mig_rename_index;
DELIMITER $$
CREATE PROCEDURE kb_mig_rename_index(IN p_table VARCHAR(64), IN p_old_idx VARCHAR(64), IN p_new_idx VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_old_idx)
       AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_new_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` RENAME INDEX `', p_old_idx, '` TO `', p_new_idx, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ④ 逐表执行（kb_chunk / kb_index_version）
CALL kb_mig_rename_col('kb_chunk', 'article_id', 'knowledge_id', 'BIGINT NOT NULL COMMENT ''知识 ID（逻辑外键 cnt_knowledge.id）''');
CALL kb_mig_rename_index('kb_chunk', 'idx_chunk_article_version', 'idx_chunk_knowledge_version');
CALL kb_mig_rename_index('kb_chunk', 'idx_chunk_article_hash', 'idx_chunk_knowledge_hash');
CALL kb_mig_rename_col('kb_index_version', 'article_id', 'knowledge_id', 'BIGINT NOT NULL COMMENT ''知识 ID（逻辑外键 cnt_knowledge.id）''');
CALL kb_mig_rename_index('kb_index_version', 'idx_index_ws_article', 'idx_index_ws_knowledge');
CALL kb_mig_rename_index('kb_index_version', 'idx_index_ws_article_status', 'idx_index_ws_knowledge_status');

-- ⑤ pub_review / pub_release
CALL kb_mig_rename_col('pub_review', 'article_id', 'knowledge_id', 'BIGINT NOT NULL COMMENT ''知识 ID（逻辑外键 cnt_knowledge.id）''');
CALL kb_mig_rename_col('pub_review', 'article_title', 'knowledge_title', 'VARCHAR(200) NOT NULL DEFAULT '''' COMMENT ''知识标题（冗余展示字段）''');
CALL kb_mig_rename_col('pub_release', 'article_id', 'knowledge_id', 'BIGINT NOT NULL COMMENT ''知识 ID（逻辑外键 cnt_knowledge.id）''');
CALL kb_mig_rename_col('pub_release', 'article_title', 'knowledge_title', 'VARCHAR(200) NOT NULL DEFAULT '''' COMMENT ''知识标题（冗余展示字段）''');
CALL kb_mig_rename_index('pub_release', 'uk_release_ws_article_version', 'uk_release_ws_knowledge_version');

-- ⑥ eng_comment / eng_like / eng_feedback
CALL kb_mig_rename_col('eng_comment', 'article_id', 'knowledge_id', 'BIGINT NOT NULL COMMENT ''知识 ID（逻辑外键 cnt_knowledge.id）''');
CALL kb_mig_rename_index('eng_comment', 'idx_comment_ws_article', 'idx_comment_ws_knowledge');
CALL kb_mig_rename_col('eng_like', 'article_id', 'knowledge_id', 'BIGINT NOT NULL COMMENT ''知识 ID（逻辑外键 cnt_knowledge.id）''');
CALL kb_mig_rename_index('eng_like', 'uk_like_ws_article_user', 'uk_like_ws_knowledge_user');
CALL kb_mig_rename_index('eng_like', 'idx_like_ws_article', 'idx_like_ws_knowledge');
CALL kb_mig_rename_col('eng_feedback', 'article_id', 'knowledge_id', 'BIGINT NOT NULL COMMENT ''知识 ID（逻辑外键 cnt_knowledge.id）''');
CALL kb_mig_rename_index('eng_feedback', 'idx_feedback_ws_article_status', 'idx_feedback_ws_knowledge_status');

-- ⑦ ai_enhance_result
CALL kb_mig_rename_col('ai_enhance_result', 'article_id', 'knowledge_id', 'BIGINT NULL COMMENT ''知识 ID（可空，供独立增强）''');
CALL kb_mig_rename_index('ai_enhance_result', 'idx_enhance_ws_article', 'idx_enhance_ws_knowledge');

-- ⑧ 表注释同步（幂等：仅当表存在且旧注释含「文章」时执行 ALTER）
DROP PROCEDURE IF EXISTS kb_mig_table_comment;
DELIMITER $$
CREATE PROCEDURE kb_mig_table_comment(IN p_table VARCHAR(64), IN p_new_comment VARCHAR(255))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
                 AND TABLE_COMMENT LIKE '%文章%') THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` COMMENT = ''', p_new_comment, '''');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL kb_mig_table_comment('cnt_knowledge', '知识主体（F-0201）');
CALL kb_mig_table_comment('eng_comment', '知识评论（F-0203）');
CALL kb_mig_table_comment('eng_like', '知识点赞（F-0203）');
CALL kb_mig_table_comment('pub_review', '知识审核记录（F-0902/F-0903）');
CALL kb_mig_table_comment('pub_release', '知识发布记录（F-0904/F-0905）');

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS kb_mig_rename_col;
DROP PROCEDURE IF EXISTS kb_mig_rename_index;
DROP PROCEDURE IF EXISTS kb_mig_table_comment;

-- 校验：确认不再存在 article_id 列与 cnt_article 表
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME LIKE 'article_%';
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cnt_article';

-- ============================================================
-- 第二部分：KB-2 数据模型（幂等）
-- ============================================================

-- ① 新表 kb_knowledge_base / kb_directory（与 init/20_knowledge.sql 一致，IF NOT EXISTS 幂等）
CREATE TABLE IF NOT EXISTS `kb_knowledge_base` (
    `id`           BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `workspace_id` BIGINT       NOT NULL COMMENT '工作空间 ID（逻辑外键 iam_workspace.id）',
    `name`         VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `intro`        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '简介',
    `cover`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '封面 URL（V2 接入 MinIO 前可空）',
    `visibility`   TINYINT      NOT NULL DEFAULT 0 COMMENT '可见性：0 私有 1 公开（库级统一决定知识可见范围）',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 回收站',
    `deleted_at`   DATETIME     NULL COMMENT '进回收站时间（超期清理依据，默认保留 30 天）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_ws_name` (`workspace_id`, `name`),
    KEY `idx_kb_ws_vis` (`workspace_id`, `visibility`)
) ENGINE = InnoDB COMMENT ='知识库（F-0308）';

CREATE TABLE IF NOT EXISTS `kb_directory` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花 ID）',
    `kb_id`      BIGINT      NOT NULL COMMENT '所属知识库 ID（逻辑外键 kb_knowledge_base.id）',
    `parent_id`  BIGINT      NOT NULL DEFAULT 0 COMMENT '父目录 ID（0=根目录）',
    `name`       VARCHAR(64) NOT NULL COMMENT '目录名称',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dir_kb_parent` (`kb_id`, `parent_id`)
) ENGINE = InnoDB COMMENT ='目录树（F-0309）';

-- ② cnt_knowledge 新增列（幂等：以列是否存在为前置条件）；删除列延后到数据迁移之后（⑤⑥ 依赖 visibility/category）
DROP PROCEDURE IF EXISTS kb_mig_add_col;
DELIMITER $$
CREATE PROCEDURE kb_mig_add_col(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL kb_mig_add_col('cnt_knowledge', 'kb_id', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''所属知识库 ID（逻辑外键 kb_knowledge_base.id，单库单目录，决策 D16）'' AFTER author_name');
CALL kb_mig_add_col('cnt_knowledge', 'directory_id', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''所属目录 ID（逻辑外键 kb_directory.id，0=库根目录）'' AFTER kb_id');
CALL kb_mig_add_col('cnt_knowledge', 'recycle_status', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''回收站状态：0 正常 1 回收站（F-0305，独立软删标记，不扩状态机）'' AFTER view_count');
CALL kb_mig_add_col('cnt_knowledge', 'deleted_at', 'DATETIME NULL COMMENT ''进回收站时间（超期 30 天清理依据）'' AFTER recycle_status');

-- ③ kb_chunk / kb_index_version 增加 kb_id（幂等）
CALL kb_mig_add_col('kb_chunk', 'kb_id', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''所属知识库 ID（逻辑外键 kb_knowledge_base.id，检索按库过滤，决策 D13）'' AFTER workspace_id');
CALL kb_mig_add_col('kb_index_version', 'kb_id', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''所属知识库 ID（逻辑外键 kb_knowledge_base.id，按库切分，决策 D13）'' AFTER workspace_id');

-- ④ 每空间创建两个默认库：「默认公开库」（公开）与「默认私有库」（私有），幂等（uk_kb_ws_name 冲突跳过）
--    ID 从 MAX(id)+1 递增（同一空间两库不得共用确定性 ID，避免主键冲突）
SET @kb_id := (SELECT IFNULL(MAX(`id`), 2090000000000000000) FROM `kb_knowledge_base`);
INSERT INTO `kb_knowledge_base` (`id`, `workspace_id`, `name`, `visibility`, `status`, `created_at`, `updated_at`)
SELECT (@kb_id := @kb_id + 1), `id`, '默认公开库', 1, 0, NOW(), NOW()
FROM `iam_workspace`
WHERE NOT EXISTS (SELECT 1 FROM `kb_knowledge_base` kb WHERE kb.`workspace_id` = `iam_workspace`.`id` AND kb.`name` = '默认公开库');

INSERT INTO `kb_knowledge_base` (`id`, `workspace_id`, `name`, `visibility`, `status`, `created_at`, `updated_at`)
SELECT (@kb_id := @kb_id + 1), `id`, '默认私有库', 0, 0, NOW(), NOW()
FROM `iam_workspace`
WHERE NOT EXISTS (SELECT 1 FROM `kb_knowledge_base` kb WHERE kb.`workspace_id` = `iam_workspace`.`id` AND kb.`name` = '默认私有库');

-- ⑤⑥ 存量知识归库 + category 平铺目录（幂等：仅在 visibility/category 列存在时执行，KB-2 首次迁移；
--    重跑时列已被 ⑧ 删除自动跳过，避免 Unknown column 错误）
DROP PROCEDURE IF EXISTS kb_mig_backfill_kb;
DELIMITER $$
CREATE PROCEDURE kb_mig_backfill_kb()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cnt_knowledge' AND COLUMN_NAME = 'visibility') THEN
        -- ⑤ 按原 visibility 值归入对应默认库（1→公开库、0→私有库）
        UPDATE `cnt_knowledge` k
        JOIN `kb_knowledge_base` kb
          ON kb.`workspace_id` = k.`workspace_id`
         AND kb.`name` = CASE WHEN k.`visibility` = 1 THEN '默认公开库' ELSE '默认私有库' END
        SET k.`kb_id` = kb.`id`
        WHERE k.`kb_id` = 0;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cnt_knowledge' AND COLUMN_NAME = 'category') THEN
        -- ⑥ category 平铺为同名一级目录并回填 directory_id（目录 ID 从 MAX(id)+1 递增避免主键冲突）
        SET @dir_id := (SELECT IFNULL(MAX(`id`), 2091000000000000000) FROM `kb_directory`);
        INSERT INTO `kb_directory` (`id`, `kb_id`, `parent_id`, `name`, `created_at`, `updated_at`)
        SELECT (@dir_id := @dir_id + 1), d.`kb_id`, 0, d.`name`, NOW(), NOW()
        FROM (SELECT DISTINCT k.`kb_id`, k.`category` AS `name` FROM `cnt_knowledge` k
              WHERE k.`category` IS NOT NULL AND k.`category` <> '' AND k.`kb_id` <> 0) d
        WHERE NOT EXISTS (SELECT 1 FROM `kb_directory` dir WHERE dir.`kb_id` = d.`kb_id` AND dir.`name` = d.`name`);

        UPDATE `cnt_knowledge` k
        JOIN `kb_directory` dir
          ON dir.`kb_id` = k.`kb_id` AND dir.`name` = k.`category`
        SET k.`directory_id` = dir.`id`
        WHERE k.`directory_id` = 0 AND k.`category` IS NOT NULL AND k.`category` <> '';
    END IF;
END$$
DELIMITER ;
CALL kb_mig_backfill_kb();

-- ⑦ 回填 kb_chunk / kb_index_version 的 kb_id（从 cnt_knowledge 带过来）
UPDATE `kb_chunk` c
JOIN `cnt_knowledge` k ON k.`id` = c.`knowledge_id`
SET c.`kb_id` = k.`kb_id`
WHERE c.`kb_id` = 0;

UPDATE `kb_index_version` v
JOIN `cnt_knowledge` k ON k.`id` = v.`knowledge_id`
SET v.`kb_id` = k.`kb_id`
WHERE v.`kb_id` = 0;

-- ⑧ 删除列 category / visibility（幂等：列存在才删，已无数据依赖）；索引同步调整
DROP PROCEDURE IF EXISTS kb_mig_drop_col;
DELIMITER $$
CREATE PROCEDURE kb_mig_drop_col(IN p_table VARCHAR(64), IN p_col VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_col, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL kb_mig_drop_col('cnt_knowledge', 'category');
CALL kb_mig_drop_col('cnt_knowledge', 'visibility');

-- 索引改名（KB-1 的 RENAME TABLE 不自动改索引名，补 cnt_knowledge 的 idx_article_*→idx_knowledge_*）
DROP PROCEDURE IF EXISTS kb_mig_rename_index;
DELIMITER $$
CREATE PROCEDURE kb_mig_rename_index(IN p_table VARCHAR(64), IN p_old_idx VARCHAR(64), IN p_new_idx VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_old_idx)
       AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_new_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` RENAME INDEX `', p_old_idx, '` TO `', p_new_idx, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL kb_mig_rename_index('cnt_knowledge', 'idx_article_ws_pub', 'idx_knowledge_ws_pub');
CALL kb_mig_rename_index('cnt_knowledge', 'idx_article_ws_status', 'idx_knowledge_ws_status');
CALL kb_mig_rename_index('cnt_knowledge', 'idx_article_ws_category', 'idx_knowledge_ws_category');
CALL kb_mig_rename_index('cnt_knowledge', 'idx_article_author', 'idx_knowledge_author');

-- 索引调整（幂等）：删除旧索引（随列删除或废弃）；新增 idx_knowledge_kb_dir / kb 维度索引
DROP PROCEDURE IF EXISTS kb_mig_drop_index;
DELIMITER $$
CREATE PROCEDURE kb_mig_drop_index(IN p_table VARCHAR(64), IN p_idx VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_idx, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL kb_mig_drop_index('cnt_knowledge', 'idx_knowledge_ws_pub');
CALL kb_mig_drop_index('cnt_knowledge', 'idx_knowledge_ws_category');

DROP PROCEDURE IF EXISTS kb_mig_add_index;
DELIMITER $$
CREATE PROCEDURE kb_mig_add_index(IN p_table VARCHAR(64), IN p_idx VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD KEY `', p_idx, '` (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL kb_mig_add_index('cnt_knowledge', 'idx_knowledge_kb_dir', 'workspace_id, kb_id, directory_id, status');
CALL kb_mig_add_index('kb_chunk', 'idx_chunk_kb_status', 'kb_id, status');
CALL kb_mig_add_index('kb_index_version', 'idx_index_kb_status', 'kb_id, status');

-- 修复 cnt_knowledge 表注释（KB-1 的 LIKE '%文章%' 判断对历史乱码注释不命中，此处按新结构直接覆盖，幂等）
ALTER TABLE `cnt_knowledge` COMMENT = '知识主体（F-0201）';

-- ⑨ 校验（应全部返回空/相等）：
--    每篇知识必须归属一个库
SELECT COUNT(*) AS orphan_knowledge FROM `cnt_knowledge` WHERE `kb_id` = 0;
--    目录必须归属存在的库
SELECT COUNT(*) AS orphan_directory FROM `kb_directory` d
WHERE NOT EXISTS (SELECT 1 FROM `kb_knowledge_base` kb WHERE kb.`id` = d.`kb_id`);
--    公开库知识数 = 原公开知识数（kb_id 已回填，直接验证公开库知识存在）
SELECT COUNT(*) AS public_kb_knowledge FROM `cnt_knowledge` k
JOIN `kb_knowledge_base` kb ON kb.`id` = k.`kb_id` WHERE kb.`visibility` = 1;

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS kb_mig_add_col;
DROP PROCEDURE IF EXISTS kb_mig_drop_col;
DROP PROCEDURE IF EXISTS kb_mig_add_index;
