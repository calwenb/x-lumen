-- 86_orphan_cleanup.sql：清理无归属孤儿知识（KB-3 修复配套，决策 D16）
-- 适用：存量开发库/测试库（xlumen_dev / xlumen_test）。干净安装无需本脚本。
-- 背景：编辑器未适配 KB 重构期间，自动保存可产生 kb_id=0 的无归属草稿，且曾可走完审核/发布
--       （发布后被公开读可见库集合过滤，无泄漏）。创建/自动保存/审核/发布已加归属校验，本脚本
--       清理历史遗留：kb_id 为 0/空/指向不存在库的知识及其审核/发布/索引关联数据。
-- 幂等可重跑：按子查询收集孤儿 id，重复执行无副作用；事务内执行。
-- 注意：仅清理"知识本身无有效归属"的记录；正常知识不受影响。执行前建议备份。

USE `xlumen_dev`;
SET NAMES utf8mb4;
START TRANSACTION;

-- ① 孤儿知识 id：kb_id 为 0/空，或指向不存在的知识库
DROP TEMPORARY TABLE IF EXISTS tmp_orphan_knowledge;
CREATE TEMPORARY TABLE tmp_orphan_knowledge AS
SELECT k.id
FROM cnt_knowledge k
LEFT JOIN kb_knowledge_base kb ON k.kb_id = kb.id
WHERE k.kb_id IS NULL OR k.kb_id = 0 OR kb.id IS NULL;

-- ② 删除关联审核记录
DELETE rv FROM pub_review rv
JOIN tmp_orphan_knowledge t ON rv.knowledge_id = t.id;

-- ③ 删除关联发布记录
DELETE r FROM pub_release r
JOIN tmp_orphan_knowledge t ON r.knowledge_id = t.id;

-- ④ 删除索引元数据（按 knowledge_id 与 kb_id 双路径）
DELETE c FROM kb_chunk c
JOIN cnt_knowledge k ON c.knowledge_id = k.id
JOIN tmp_orphan_knowledge t ON k.id = t.id;
DELETE v FROM kb_index_version v
JOIN tmp_orphan_knowledge t ON v.knowledge_id = t.id;

-- ⑤ 删除孤儿知识本体
DELETE k FROM cnt_knowledge k
JOIN tmp_orphan_knowledge t ON k.id = t.id;

DROP TEMPORARY TABLE tmp_orphan_knowledge;
COMMIT;
