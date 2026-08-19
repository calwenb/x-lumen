# xLumen 待修问题清单（BUG Backlog）

> 更新日期：2026/8/19
> **本仓库专属**。
> 记录用户自测发现的、尚未修复的问题。**修复仅在用户明确要求时进行**：AI 不自动认领、不随会话收尾顺手修复；每修复一条即从本清单移除，并在 [CHANGELOG.md](./CHANGELOG.md) 按模板记录。

## 记录约定

- 编号：`BUG-001` 起顺延，不得重复；已移除的编号不回收。
- 模块：写清归属（如 前端 blog / 前端 admin / 后端 content / 后端 knowledge / 数据库 / 构建门禁）。
- 每个问题一个 `##` 小节，字段按下方模板；截图可存放后填入相对路径，报错信息/接口响应可直接粘贴。
- 状态流转：待修复（默认）→ 修复中（用户要求修复后、AI 认领时标）→ 已修复（从清单移除，编号不回收）。

## 模板（复制后填写）

## BUG-001 · 一句话描述问题

- 日期：2026-08-16
- 模块：后端 content / 前端 blog / 数据库 ...
- 状态：待修复
- 复现步骤：1. ...；2. ...；3. ...
- 现象 vs 期望：实际是 X，期望是 Y
- 补充：截图路径 / 报错信息 / 相关接口响应

## 待修复清单

- BUG-015 待复核（2026/8/19 全功能测试·后端·SUSPECT，2026/8/19 复核未复现，见下方小节）

> 2026/8/19 12:05 修复批次完成：BUG-007/008/009/010/012/013/014/016/017 共 9 条已修复并验证（详见 CHANGELOG 2026/8/19 12:05 条目），从清单移除、编号不回收。BUG-015（getOwned 偶发 404）复核未复现，保留 SUSPECT 待观察。

---

## BUG-015 · 提交审核后作者侧 `getOwned` 偶发 404（SUSPECT·复核未复现）

- 日期：2026-08-19
- 模块：后端 content（KnowledgeServiceImpl.getOwned）
- 状态：待复核（2026/8/19 12:05 复核未复现：qa_alpha 实测 create → submit-review → GET /api/v1/knowledge/{id} 全程 200，status 2/3/4 均可读；3 次复现 1 次并发的原 SUSPECT 未再触发）
- 复现步骤：`POST /api/v1/knowledge` → `POST /api/v1/reviews` → `GET /api/v1/knowledge/{id}`
- 现象 vs 期望：现象（一次报告）= 第 3 步 404 `知识不存在`；期望作者可继续查看并进入编辑（已通过 list 接口证实记录存在）
- 代码面排查结论：`getOwned` 查询条件 = `eq(id) + eq(workspaceId) + eq(authorId)`，**无 status 条件**；authorId 来自 JWT，create 写库后不经 submitReview/approve 覆写——按代码静态分析不存在「审核后 404」的必然路径。原 SUSPECT 疑为子代理 curl 脚本账号/workspace 上下文错位或并发乐观锁 409 被误读。保留观察，不再复现则下次测试批次关闭。
- 补充：若再次复现，建议在 `getOwned` 加 debug 日志输出实际 workspaceId/authorId 后再定位

---

（历史清空说明：2026/8/18 20:47 清空——BUG-006 知识详情页排版错乱经用户要求修复并验证通过，编号不回收，详见 CHANGELOG 2026/8/18 20:47 条目。2026/8/19 12:05 移除 BUG-007~010/012~014/016/017（已修复），详见 CHANGELOG 2026/8/19 12:05 条目。遗留运维事项：①后端已重启运行新代码；②`cnt_knowledge_version` 表经 migration 88 建表，干净安装走 sql/init/40_content.sql；③qa_alpha_20260819 按 QA §3.8 于 8-19 22:00 清理）
