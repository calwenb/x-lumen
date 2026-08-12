-- 50_publishing.sql：xlumen-publishing 模块 review/release 域（pub_ 审核状态机/双闸门/发布幂等/公开读）
-- 表清单随 M10（F-0901~F-0905）与 M03（F-0201~F-0203）落地：pub_review（审核记录）、pub_release（发布记录）。

USE `xlumen_dev`;
SET NAMES utf8mb4;
