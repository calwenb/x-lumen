-- 85_kb_migration.sql：知识平台化重构存量迁移（KB-1 概念改名；KB-2 结构变更将追加小节）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装走 sql/init/ 脚本，无需本脚本。
-- 本脚本独立于 sql/init/ 目录（init 已有 85_platform.sql，编号冲突），由人工/CI 显式执行。
-- 幂等可重跑：所有变更前先查 information_schema，重复执行无副作用。
-- KB-1 范围：物理表改名 cnt_article→cnt_knowledge；article_id/article_title 列改名；索引/唯一键改名；注释同步。

USE `xlumen_dev`;
SET NAMES utf8mb4;

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
