-- 00_database.sql：创建开发数据库与通用设置（BACKEND.md §7 编号契约第 1 个）
-- 库名固定 xlumen_dev（个人开发库）；测试环境独立 xlumen_test（同套脚本）。
-- 修改库名须同步 backend/xlumen-server/config/.env 的 XLUMEN_DB_NAME 与 XLUMEN_DB_URL。
-- 服务器已预建数据库或账号无建库权限时，本脚本允许跳过（init-db.ps1 容错执行）。

CREATE DATABASE IF NOT EXISTS `xlumen_dev`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `xlumen_dev`;

-- 单实例单 Schema 通用会话设置
SET NAMES utf8mb4;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';
