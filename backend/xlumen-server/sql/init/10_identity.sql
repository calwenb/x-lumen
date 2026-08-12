-- 10_identity.sql：xlumen-identity 模块（iam_ 用户/会话/工作空间/成员/角色 + plt_ 平台治理）
-- 表清单随 M02（F-0101~F-0104）落地；建表使用 IF NOT EXISTS，系统数据使用唯一键 + INSERT ... ON DUPLICATE KEY UPDATE。
-- 表命名规则：单 Schema、无外键、主键 BIGINT 雪花 ID、业务表含 workspace_id 与 (workspace_id, status) 联合索引、唯一键 uk_ 前缀。

USE `xlumen_dev`;
SET NAMES utf8mb4;
