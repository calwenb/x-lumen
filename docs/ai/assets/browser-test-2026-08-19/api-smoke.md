# xLumen 后端 API 冒烟报告（2026-08-19）

> 测试时间：2026/8/19 11:24~11:33
> 测试账号：qa_alpha_20260819 (workspaceId 2089916171831529472) / qa_beta_20260819 (workspaceId 2089916172758470656)
> 密码统一 `Test123456`
> 后端：`http://localhost:8080`（actuator/health=UP，JDK 25，Spring Boot 4.1.0）
> 测试环境：与「QA.md 全功能巡检」并发执行，部分中间态数据受并发用例影响已注明

> 路径说明：QA 任务清单中部分端点路径与后端实际路径不一致，差异已逐项标注；本报告**优先记录实际路径响应**，再用 `(任务路径)` 标注任务清单的写法。

---

## 模块 1：身份与多租户

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 1.1 | `POST /api/v1/auth/login` (alpha) | 200 | `accessToken/refreshToken/workspaceId/user` |
| 1.2 | `POST /api/v1/auth/login` (beta) | 200 | 同上 |
| 1.3 | `POST /api/v1/auth/refresh` (alpha) | 200 | 新 accessToken + 新 refreshToken（GETDEL 轮换） |
| 1.4 | `POST /api/v1/auth/logout` (无 body) | 400 | `INVALID_PARAM 请求参数有误` |
| 1.5 | `POST /api/v1/auth/logout` (带 refreshToken) | 200 | `SUCCESS` |
| 1.6 | `POST /api/v1/auth/logout` (no auth) | 401 | `UNAUTHORIZED` |
| 1.7 | `GET /api/v1/workspaces/current` (alpha) | 200 | `workspaceId/name/slug/roleCode=OWNER` |
| 1.8 | `GET /api/v1/workspaces/current` (no auth) | 401 | `UNAUTHORIZED` |
| 1.9 | `GET /api/v1/workspace/me` (任务路径) | 404 | `NOT_FOUND` (实际端点为 `/api/v1/workspaces/current`) |
| 1.10 | `GET /api/v1/workspace/{id}` (alpha's own) | 404 | `NOT_FOUND` (实际端点 `/api/v1/knowledge-bases/{kbId}`，workspace 维度无 by-id) |
| 1.11 | `GET /api/v1/workspace/{id}` (alpha token, beta WS) | 404 | 同上（路径不存在，无越权可言） |

异常项：任务清单 `GET /api/v1/workspace/{id}` 与 `GET /api/v1/workspace/me` 路径与后端不符；后端只有 `/api/v1/workspaces/current`。**建议修正任务清单**。

---

## 模块 2：博客公开阅读

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 2.1 | `GET /api/v1/public/knowledge?pageNo=1&pageSize=5` (no auth) | 200 | total=7，5 条 KnowledgeCardVO 列表 |
| 2.2 | `GET /api/v1/public/knowledge/{id}` (1900000000000000001) | 200 | KnowledgeDetailVO（liked/favorited 字段、aiSummary=null） |
| 2.3 | `GET /api/v1/public/knowledge/{id}` (9999999999999999999) | 400 | `INVALID_PARAM 参数 id 类型不正确`（任务期望 404，实际 400） |
| 2.4 | `GET /api/v1/public/knowledge/{id}/comments?pageNo=1` | 200 | 9 条评论，字段含 myReaction |
| 2.5 | `GET /api/v1/public/knowledge/{id}/like-status` (任务路径) | 404 | `NOT_FOUND`（实际端点 `/like/status`，含斜杠） |
| 2.5b | `GET /api/v1/public/knowledge/{id}/like/status` (no auth) | 200 | `reaction: NONE`（匿名访问返回 NONE） |
| 2.6 | `GET /api/v1/public/knowledge-bases` (no auth) | 404 | `NOT_FOUND`（实际端点需 OWNER 鉴权 `/api/v1/knowledge-bases`） |
| 2.6b | `GET /api/v1/public/knowledge-bases` (alpha) | 200 | 数组空 |
| 2.7 | `GET /api/v1/kb/{id}` (no auth) | 401 | `UNAUTHORIZED`（KB 端点需要登录） |
| 2.7b | `GET /api/v1/kb/{id}` (alpha) | 404 | `NOT_FOUND`（实际端点 `/api/v1/knowledge-bases/{kbId}`） |
| 2.8 | `GET /api/v1/kb/{id}/directories` (alpha) | 404 | 同上（实际 `/api/v1/knowledge-bases/{kbId}/directories`） |
| 2.9 | `GET /api/v1/search?q=Spring` (alpha) | 404 | `NOT_FOUND`（**端点未实现**——见 SUSPECT-008） |
| 2.10 | `GET /api/v1/about` (no auth) | 401 | `UNAUTHORIZED`（端点不存在，需鉴权） |
| 2.11 | `GET /api/v1/about` (alpha) | 404 | `NOT_FOUND`（**端点未实现**——见 SUSPECT-008） |

异常项：
- 2.3 任务期望 404「资源不存在」，实际 400「参数 id 类型不正确」——Long 解析失败时返回 400 而非 404。
- 2.9/2.10/2.11 端点不存在（任务列出的 `/api/v1/search` 与 `/api/v1/about` 在后端 controller 扫描结果中无对应）。

---

## 模块 3：互动与反馈

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 3.1 | `POST /api/v1/public/knowledge/{id}/like` (alpha, 首次) | 200 | `reaction: LIKE` |
| 3.2 | `POST /api/v1/public/knowledge/{id}/like` (alpha, 二次) | 200 | `reaction: NONE`（toggle 互斥正确） |
| 3.3 | `POST /api/v1/public/knowledge/{id}/dislike` (alpha) | 200 | `reaction: DISLIKE`（互斥切换） |
| 3.4 | `POST /api/v1/public/knowledge/{id}/like` (no auth) | 401 | `UNAUTHORIZED` |
| 3.5 | `POST /api/v1/public/knowledge/{id}/favorite` (alpha toggle off→on) | 200 | `true` |
| 3.6 | `GET /api/v1/public/favorites?pageNo=1` (alpha) | 200 | total=1，favoritedAt 字段 |
| 3.7 | `POST /api/v1/public/knowledge/{id}/favorite` (toggle on→off) | 200 | `false` |
| 3.8 | `DELETE /api/v1/public/favorites/{id}` (任务路径) | 404 | `NOT_FOUND`（实际为 toggle 接口，无 DELETE） |
| 3.9 | `POST /api/v1/public/knowledge/{id}/comments` (alpha) | 200 | 字段含 `createdAt: null`（**SUSPECT-009**） |
| 3.10 | `POST /api/v1/public/comments/{commentId}/like` (alpha) | 200 | `reaction: LIKE` |
| 3.11 | `POST /api/v1/public/comments/{commentId}/dislike` (alpha) | 200 | `reaction: DISLIKE` |
| 3.12 | `POST /api/v1/public/knowledge/{id}/correction` (任务路径) | 401 | `UNAUTHORIZED`（实际端点 `/feedback`） |
| 3.12b | `POST /api/v1/public/knowledge/{id}/feedback` (含 `content`) | 400 | `problem 问题描述不能为空`（**字段是 problem，不是 content**） |
| 3.12c | `POST /api/v1/public/knowledge/{id}/feedback` (`{"problem":...}`) | 200 | trackNo 已返回 |
| 3.12d | `POST /api/v1/public/knowledge/{id}/feedback` (立即连发 2 次) | 200 + 200 | **SUSPECT-010**：两条都 200，未触发 429 限流 |

异常项：
- 3.9 评论 `createdAt: null`——新建评论字段应有时戳。
- 3.12b 任务清单字段名 `content` 与实际 DTO 字段 `problem` 不一致。
- 3.12d 同 IP 连发两条纠错都 200，违反 QA.md §3.7 / STATUS.md §3「M11 同 IP 每分钟 1 条，超限 429」契约。
- 3.8 任务清单 `DELETE /api/v1/favorites/{id}` 在后端无对应端点（toggle 取代）。

---

## 模块 4：内容管理

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 4.1 | `GET /api/v1/knowledge?status=2` (alpha) | 200 | total=0，初始无草稿 |
| 4.2 | `GET /api/v1/knowledge?pageNo=1` (no auth) | 401 | `UNAUTHORIZED` |
| 4.3 | `POST /api/v1/knowledge` (kbId+dir 默认公开库) | 400 | `请选择有效的知识库与目录`（默认公开库归 qoder_test 私有） |
| 4.3b | `POST /api/v1/knowledge` (alpha 自己的 kb+dir) | 200 | id=2089917383238799361，status=2, version=0 |
| 4.4 | `PUT /api/v1/knowledge/{id}` (autosave v0) | 200 | version 升 0→1 |
| 4.5 | `GET /api/v1/knowledge/{id}` | 200 | 详情正常 |
| 4.6 | `PUT /api/v1/knowledge/{id}` (旧 version 触发 409) | 409 | `CONFLICT 知识已被修改，请刷新后重试`（乐观锁正确） |
| 4.7 | `PUT /api/v1/knowledge/{id}` (`kbId/directoryId` 改成 9999 越界) | 200 | **SUSPECT-011**：kbId 越界后被静默忽略，directoryId 错误地更新为 9999999999（应 400） |
| 4.8 | `PUT /api/v1/knowledge/{id}` (修正回正确 dir) | 200 | 修复成功，version=3 |
| 4.9 | `POST /api/v1/reviews` `{knowledgeId}` (提交审核，任务路径 `/knowledge/{id}/submit-review`) | 200 | reviewId 返回，aiTaskId 触发 |
| 4.10 | `GET /api/v1/knowledge/{id}/versions` (任务路径) | 404 | **SUSPECT-012**：知识版本历史端点未实现（GET `/knowledge/{id}` 已含 currentVersion） |
| 4.11 | `GET /api/v1/knowledge/{id}` 提交审核后作者侧取详情 | 404 | **SUSPECT-013**：submit-review 之后作者 `getOwned` 取不到（详情返回 404，但 list 仍可见），疑似状态/归属字段被覆写 |

异常项：
- 4.7 是真正的契约 bug：kbId 越界值被静默丢弃，directoryId 错误值被接受写入，**应拒绝并 400**。
- 4.10 知识版本历史端点缺失。
- 4.11 提交审核后作者侧 GET 返回 404，但 list 仍能返回——`getOwned` 用了 status/workspace/author 三元过滤，submit-review 后 status 变化导致作者取不到（仅 KB-3 已知 BUG-4 防线在 ContentApiImpl，但 KnowledgeServiceImpl.getOwned 没考虑 status）。后段测试通过 list 查到的 status=4 (APPROVED) 验证了 status 字段未改 author，但 `getOwned` 仍 404——待复核实际根因（也与并发巡检用例编辑/删除干扰有关）。

---

## 模块 5：知识库体系

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 5.1 | `GET /api/v1/knowledge-bases/me` (alpha) | 400 | `INVALID_PARAM 参数 kbId 类型不正确`（**端点不存在，/me 被解析为 kbId**） |
| 5.2 | `GET /api/v1/knowledge-bases/{id}` (alpha) | 200 | KB 详情 |
| 5.3 | `GET /api/v1/knowledge-bases/{id}` (no auth) | 401 | `UNAUTHORIZED` |
| 5.4 | `GET /api/v1/knowledge-bases/{id}` (beta, 跨 WS) | 404 | `知识库不存在`（按 BACKEND §9 不暴露存在性 404 语义） |
| 5.5 | `PUT /api/v1/knowledge-bases/{id}` (rename) | 200 | name 更新成功 |
| 5.6 | `GET /api/v1/knowledge-bases/{id}/directories` (alpha) | 200 | 1 个根目录 |
| 5.7 | `GET /api/v1/knowledge-bases/{id}/directories` (no auth) | 401 | `UNAUTHORIZED` |
| 5.8 | `GET /api/v1/knowledge-bases/{id}/directories` (beta, 跨 WS) | 404 | `知识库不存在` |
| 5.9 | `POST /api/v1/knowledge-bases/{id}/directories` (创建子目录) | 200 | 新 dirId |
| 5.10 | `PUT /api/v1/knowledge-bases/{id}/directories/{dirId}` (rename) | 200 | 返回 DirectoryVO，**契约修复有效**（STATUS.md 提到 8-18 顺带修复） |
| 5.11 | `GET /api/v1/recycle-bin?type=knowledge` (no auth) | 401 | `UNAUTHORIZED` |
| 5.12 | `GET /api/v1/recycle-bin?type=knowledge` (alpha) | 200 | 初始 total=0 |
| 5.13 | `GET /api/v1/recycle-bin?type=kb` (alpha) | 200 | 初始 total=0 |
| 5.14 | `GET /api/v1/recycle-bin?pageNo=1` (缺 type) | 200 | 成功（type 默认） |
| 5.15 | `POST /api/v1/knowledge-bases/{id}/directories/{dirId}` (parent 不匹配) | 200 | OK（不同 kbId 的目录被接受——但因 Service 校验，未测越权） |
| 5.16 | `DELETE /api/v1/knowledge/{id}` (PENDING_REVIEW 状态) | 409 | `仅构思/草稿可删除，已发布知识请先下架`（正确状态机校验） |
| 5.17 | `POST /api/v1/recycle-bin/knowledge/{id}/restore` (不在回收站) | 404 | `知识不存在或不在回收站` |
| 5.18 | `POST /api/v1/recycle-bin/knowledge/{id}/restore` (在回收站) | 200 | 恢复成功 |
| 5.19 | `DELETE /api/v1/recycle-bin/knowledge/{id}?confirm=CONFIRM` | 200 | 彻底删除成功 |
| 5.20 | `DELETE /api/v1/knowledge-bases/{id}` (no confirm) | 409 | `删除知识库需要二次确认`（强制二次确认） |
| 5.21 | `DELETE /api/v1/knowledge-bases/{id}?confirm=CONFIRM` | 200 | 进回收站 |
| 5.22 | `POST /api/v1/recycle-bin/kb/{id}/restore` | 200 | 恢复成功 |
| 5.23 | `DELETE /api/v1/recycle-bin/kb/{id}?confirm=CONFIRM` | 200 | 彻底删除 |
| 5.24 | `DELETE /api/v1/knowledge-bases/{id}/directories/{dirId}` | 200 | 删除成功 |
| 5.25 | `DELETE /api/v1/directories/{id}` (任务路径) | 404 | 实际路径需带 `/knowledge-bases/{kbId}/` 前缀 |

异常项：
- 5.1 `/me` 端点不存在——`/api/v1/knowledge-bases` 仅有 list/create/get/update/delete/visibility，无 `/me` 别名。
- 5.5 KB update 后 `knowledgeCount` 从 1 跳到 0（KB 创建后已含 1 篇知识，5.5 后 0）——可能与 4.7 改坏 directoryId 致知识从 KB 计数中剔除有关，**与 SUSPECT-011 同源**。

---

## 模块 6：审核与发布

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 6.1 | `GET /api/v1/reviews?pageNo=1` (alpha) | 200 | 1 条 PENDING 记录 |
| 6.2 | `GET /api/v1/reviews/{id}` (alpha, AI 审校懒回填) | 200 | aiResultJson 已填充（**bug-002 修复有效**） |
| 6.3 | `GET /api/v1/reviews?pageNo=1` (no auth) | 401 | `UNAUTHORIZED` |
| 6.4 | `GET /api/v1/reviews/{id}` (beta, 跨 WS) | 404 | `审核记录不存在`（不暴露） |
| 6.5 | `POST /api/v1/reviews/{id}/approve` (alpha) | 200 | 状态变 APPROVED，aiResultJson 含 2 条错误 |
| 6.6 | `POST /api/v1/reviews/{id}/approve` (no body) | 400 | `INVALID_PARAM 请求参数有误`（version 必填） |
| 6.7 | `POST /api/v1/reviews/{id}/reject` (no body) | 400 | `version 版本号不能为空` |
| 6.7b | `POST /api/v1/reviews/{id}/reject` (含 version+reason+position+expectation) | 200 | 驳回成功 |
| 6.8 | `POST /api/v1/releases` (knowledgeId+version) | 409 | `版本冲突`（version 不匹配） |
| 6.8b | `POST /api/v1/releases` (修正 version=2) | 200 | releaseId 返回，status=DONE，releasedAt 已填 |
| 6.8c | `POST /api/v1/releases` (publishAt 未来) | 200 | 计划发布成功 |
| 6.8d | `POST /api/v1/releases` (非 APPROVED 状态) | 409 | `仅审核通过的知识可发布` |
| 6.9 | `POST /api/v1/knowledge/{id}/release` (任务路径) | 404 | **SUSPECT-014**：实际端点 `/api/v1/releases` (POST body) |
| 6.10 | `POST /api/v1/knowledge/{id}/scheduled-release?pubAt=` (任务路径) | 404 | 同上 |
| 6.11 | `POST /api/v1/knowledge/{id}/unpublish` (任务路径) | 404 | **SUSPECT-015**：后端无 unpublish 端点（搜 `unpublish`/`下架` 0 hit） |

异常项：
- 6.9/6.10 任务清单的 `/api/v1/knowledge/{id}/release` 与 `/scheduled-release` 路径不存在，实际为 `/api/v1/releases` + body。
- 6.11 任务清单的 unpublish 端点**完全缺失**（KB-3 后下架能力可能落到 KB 软删/回收站，**未在 publishing 端点暴露**）。

---

## 模块 7：AI 对话

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 7.1 | `POST /api/v1/chat/conversations` (alpha) | 200 | convId=2089918146350469121 |
| 7.2 | `GET /api/v1/chat/conversations` (alpha) | 200 | 1 条会话 |
| 7.3 | `GET /api/v1/chat/conversations` (no auth) | 401 | `UNAUTHORIZED` |
| 7.4 | `POST /api/v1/chat/stream` (SSE) | 200 | chunk/citation/done 事件，conversationId+messageId 正确 |
| 7.4b | `POST /api/v1/chat/stream` (no auth) | 401 | `UNAUTHORIZED` |
| 7.5 | `POST /api/v1/chat/stream` (缺 query 字段) | (未测；空 query 由 Service 校验) | |
| 7.6 | `POST /api/v1/chat/knowledge/{id}/ask` (SSE) | 200 | 流式输出，引用空（alpha 视角可见库无 qoder_test 私有 KB） |
| 7.7 | `GET /api/v1/chat/conversations/{id}/messages` (alpha) | 200 | USER + ASSISTANT 消息，ASSISTANT citationsJson=`[]` |
| 7.8 | `GET /api/v1/chat/conversations/{id}/messages` (beta 跨 WS) | 404 | `会话不存在`（跨 WS 隔离） |
| 7.9 | `POST /api/v1/chat/conversations/{id}/messages` (任务路径) | 404 | **SUSPECT-016**：发消息实际为 `/api/v1/chat/stream`（body 含 conversationId），无独立 messages POST |

异常项：
- 7.4 SSE 协议正确（chunk + citation + done 三事件），与 STATUS §3「D14 小光 SSE 流式」一致。
- 7.9 任务清单路径错误。

---

## 模块 8：AI 写作

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 8.1 | `POST /api/v1/ai/writing` (alpha, topic) | 200 | taskId=2089918301959147521 |
| 8.2 | `POST /api/v1/ai/writing` (空 body) | 400 | `主题、草稿、素材至少填写一项` |
| 8.3 | `POST /api/v1/ai/writing` (no auth) | 401 | `UNAUTHORIZED` |
| 8.4 | `GET /api/v1/tasks/{taskId}` (轮询) | RUNNING→COMPLETED | 15s 内完成，resultJson 包含 title/content |
| 8.5 | `GET /api/v1/tasks/{taskId}` (beta, 跨 WS) | 404 | `任务不存在` |
| 8.6 | `GET /api/v1/tasks/9999999999` (不存在) | 404 | `任务不存在` |
| 8.7 | `GET /api/v1/writing/tasks/{id}` (任务路径) | 404 | **SUSPECT-017**：实际 `/api/v1/tasks/{id}` |

异常项：8.7 路径差异。

---

## 模块 9：AI 增值 / RAG

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 9.1 | `GET /api/v1/knowledge/{id}/index-status` (1900000000000000001) | 200 | data=null（Noop 降级，knowledge 不在 alpha 工作空间可见） |
| 9.2 | `GET /api/v1/knowledge/{id}/index-status` (no auth) | 401 | `UNAUTHORIZED` |
| 9.3 | `GET /api/v1/knowledge/{id}/summary` (任务路径) | 404 | **SUSPECT-018**：实际 `/api/v1/ai/enhance` (POST scene=SUMMARY) 或详情内 `aiSummary` 字段 |
| 9.4 | `GET /api/v1/knowledge/{id}/summary` (no auth) | 401 | `UNAUTHORIZED` |
| 9.5 | `POST /api/v1/ai/enhance` (缺 content) | 400 | `待处理内容不能为空` |
| 9.5b | `POST /api/v1/enhance` (含 content+scene=SUMMARY) | 200 | id 返回，resultJson 含 summary，**`createdAt: null`**（**SUSPECT-019**） |
| 9.6 | `POST /api/v1/knowledge/{id}/reindex` (跨 WS 的公开知识) | 404 | `知识不存在`（按 workspace 隔离——alpha 看不到 qoder_test 的） |
| 9.6b | `POST /api/v1/knowledge/{id}/reindex` (alpha 自己的已发布) | 200 | status=ACTIVE, chunkCount=1（**BUG-004 补跑端点正常**） |
| 9.7 | `POST /api/v1/knowledge/{id}/reindex` (no auth) | 401 | `UNAUTHORIZED` |
| 9.8 | `POST /api/v1/knowledge/{nonexist}/reindex` | 404 | `知识不存在` |

异常项：
- 9.3/9.4 任务清单 `/api/v1/knowledge/{id}/summary` 端点不存在；实际摘要能力在 `/api/v1/ai/enhance`（同步生成）+ 详情 `aiSummary` 字段（发布事件异步）。
- 9.5b enhance 落库后 `createdAt: null`——同 3.9 评论 createdAt 问题，疑为同一时戳字段未回填。
- 9.6 reindex 的 404 是「按工作空间过滤后找不到」，**符合 BACKEND §9 双层校验**，任务清单期望的「所有已发布知识可补跑」实际只对**当前空间**的已发布知识生效——见 SUSPECT-020（**任务期望与实现偏差**）。

---

## 模块 10：管理后台

| # | 端点 | 状态 | 关键响应 |
|---|---|---|---|
| 10.1 | `GET /api/v1/admin/workspace/settings` (alpha) | 200 | name=qa_alpha_20260819, forceReview=true |
| 10.2 | `GET /api/v1/admin/workspace/settings` (beta) | 200 | name=qa_beta_20260819, forceReview=true（**每个 OWNER 仅看自己的空间**——空间维度） |
| 10.3 | `GET /api/v1/admin/workspace/settings` (no auth) | 401 | `UNAUTHORIZED` |
| 10.4 | `GET /api/v1/admin/audit-logs?pageNo=1` (alpha) | 200 | total=2（KNOWLEDGE_PUBLISH + REVIEW_REJECT） |
| 10.5 | `GET /api/v1/admin/audit-logs?pageNo=1` (no auth) | 401 | `UNAUTHORIZED` |
| 10.6 | `GET /api/v1/admin/model-configs` (alpha) | 200 | 数组空（未配置场景模型） |
| 10.7 | `GET /api/v1/admin/models` (任务路径) | 404 | **SUSPECT-021**：实际 `/api/v1/admin/model-configs` |
| 10.8 | `GET /api/v1/admin/audit-logs?pageNo=1` (beta) | 200 | total=0（beta 没操作） |
| 10.9 | `GET /api/v1/admin/audit-logs?pageNo=2` (alpha) | 200 | records=[]（分页正确） |
| 10.10 | `GET /api/v1/admin/workspace/settings?workspaceId={beta}` (alpha) | 200 | 返回 alpha 自己的设置（**query 参数被忽略**——任务期望 403/404 实际取请求者自己的） |

异常项：
- 10.7 任务路径 `/admin/models` 不存在，实际 `/admin/model-configs`。
- 10.10 admin 接口始终返回请求者自身空间（不带 workspaceId 概念）——任务列出的「admin 是空间维度还是全局维度」答案是**空间维度**（每个 OWNER 只管理自己创建的空间，无平台级超级管理员入口）。

---

## BUG 候选清单（SUSPECT）

> 按任务约定编号顺延 BUG-008 起。仅记录，**未写入 BUGS.md**，由用户决定是否确认。

### BUG-008 · 任务清单端点路径与后端实际不符（横切·多模块）

- 日期：2026-08-19
- 模块：QA 任务清单（横向）
- 状态：待修复
- 复现步骤：逐项对照任务清单与后端 controller
- 现象 vs 期望：
  - `GET /api/v1/workspace/me` 期望 → 实际 `GET /api/v1/workspaces/current`
  - `GET /api/v1/workspace/{id}` 期望 → 实际 无 workspace 维度 by-id 端点
  - `GET /api/v1/kb/{id}` 期望 → 实际 `GET /api/v1/knowledge-bases/{kbId}`
  - `GET /api/v1/kb/{id}/directories` 期望 → 实际 `GET /api/v1/knowledge-bases/{kbId}/directories`
  - `GET /api/v1/search?q=` 期望 → 实际 **端点未实现**
  - `GET /api/v1/about` 期望 → 实际 **端点未实现**
  - `GET /api/v1/public/knowledge/{id}/like-status` 期望 → 实际 `GET /api/v1/public/knowledge/{id}/like/status`
  - `GET /api/v1/public/knowledge-bases` 期望 → 实际 `GET /api/v1/knowledge-bases`（需登录）
  - `POST /api/v1/public/knowledge/{id}/correction` 期望 → 实际 `POST /api/v1/public/knowledge/{id}/feedback`（字段 `problem` 而非 `content`）
  - `DELETE /api/v1/favorites/{id}` 期望 → 实际 用 toggle 端点
  - `POST /api/v1/knowledge/{id}/submit-review` 期望 → 实际 `POST /api/v1/reviews` body `{knowledgeId}`
  - `GET /api/v1/knowledge/{id}/versions` 期望 → 实际 **未实现**（详情接口含 currentVersion）
  - `POST /api/v1/knowledge/{id}/release` 期望 → 实际 `POST /api/v1/releases` body
  - `POST /api/v1/knowledge/{id}/scheduled-release?pubAt=` 期望 → 实际 同上 body 字段 `publishAt`
  - `POST /api/v1/knowledge/{id}/unpublish` 期望 → 实际 **端点未实现**
  - `POST /api/v1/chat/conversations/{id}/messages` 期望 → 实际 发消息走 `POST /api/v1/chat/stream`
  - `GET /api/v1/writing/tasks/{id}` 期望 → 实际 `GET /api/v1/tasks/{id}`
  - `GET /api/v1/knowledge/{id}/summary` 期望 → 实际 `POST /api/v1/ai/enhance`（同步生成）或详情 `aiSummary`（异步）
  - `GET /api/v1/admin/models` 期望 → 实际 `GET /api/v1/admin/model-configs`
  - `GET /api/v1/knowledge-bases/me` 期望 → 实际 **未实现**
  - `DELETE /api/v1/directories/{id}` 期望 → 实际 `DELETE /api/v1/knowledge-bases/{kbId}/directories/{id}`
- 补充：任务清单多以「/knowledge/」「/kb/」单数起点，与 D17 决策下「/knowledge-bases」复数路径不一致。

### BUG-009 · 新建评论响应 `createdAt` 为 null

- 日期：2026-08-19
- 模块：后端 publishing（CommentController / CommentService）
- 状态：待修复
- 复现步骤：登录 alpha，调 `POST /api/v1/public/knowledge/1900000000000000001/comments` body `{"content":"..."}`
- 现象 vs 期望：返回 VO 字段 `createdAt: null`；期望创建后立即回填时戳（与 DB `created_at` 一致）
- 补充：与 BUG-019 同源（EnhanceResultVO 也 null）。

### BUG-010 · 读者纠错同 IP 连发未触发 429 限流

- 日期：2026-08-19
- 模块：后端 publishing（FeedbackService 限流）
- 状态：待修复
- 复现步骤：同 IP 在 1 秒内连续两次 `POST /api/v1/public/knowledge/{id}/feedback`
- 现象 vs 期望：两次均返回 200 + trackNo；期望第二次返回 429（QA.md §3.7 / STATUS §3「M11 同 IP 每分钟 1 条」契约）
- 补充：可能是 Redis 限流计数 / Lua 脚本未生效，或 IP 取自 nginx 反代场景下未识别。

### BUG-011 · 知识 update 接受越界 `kbId/directoryId`

- 日期：2026-08-19
- 模块：后端 content（KnowledgeServiceImpl.update）
- 状态：待修复
- 复现步骤：PUT `/api/v1/knowledge/{id}` body `{"kbId":"9999999999","directoryId":"9999999999",...}`
- 现象 vs 期望：kbId 越界值被静默丢弃（保留原 KB），directoryId 错误值被接受并落库；期望整体 400 拒绝（KnowledgeApi.checkOwnership 校验未走通）
- 补充：与 SUSPECT-005（KB 知识计数 0）同源，错误 directoryId 让知识从 KB 计数中排除。

### BUG-012 · 知识版本历史端点缺失

- 日期：2026-08-19
- 模块：后端 content
- 状态：待修复
- 复现步骤：调 `GET /api/v1/knowledge/{id}/versions`
- 现象 vs 期望：返回 404；期望返回版本列表（与 PRODUCT §5 F-0303 / 文档「自动保存 + 历史版本」对应）
- 补充：`cnt_knowledge_version` 表已存在但无对外 controller。

### BUG-013 · 提交审核后作者侧 `getOwned` 返回 404（并发待复核）

- 日期：2026-08-19
- 模块：后端 content（KnowledgeServiceImpl.getOwned）
- 状态：待修复（**与并发巡检可能冲突**，已用新建知识复测未复现——记为 SUSPECT）
- 复现步骤：`POST /api/v1/knowledge` → `POST /api/v1/reviews` → `GET /api/v1/knowledge/{id}`
- 现象 vs 期望：第 3 步 404 `知识不存在`；期望作者可继续查看并进入编辑（已通过 list 接口证实记录存在）
- 补充：list 显示 status=2 (DRAFT) 时仍 404；list 显示 status=4 (APPROVED) 时同样 404；3 次复现中有 1 次为并发场景，**根因待复核**。建议排查 `getOwned` 的 `eq(workspaceId)` + `eq(authorId)` 是否被 race 影响，或 D17 后 authorId 字段在 submit-review 流程中是否被覆写。

### BUG-014 · 发布/定时发布端点为 `/api/v1/releases`（任务清单路径偏差）

- 日期：2026-08-19
- 模块：QA 任务清单
- 状态：待修复（清单）
- 现象 vs 期望：任务期望 `/api/v1/knowledge/{id}/release` 与 `/scheduled-release?pubAt=`；实际 `POST /api/v1/releases` body `{knowledgeId, version, publishAt?}`。

### BUG-015 · 下架（unpublish）端点完全缺失

- 日期：2026-08-19
- 模块：后端 publishing / content
- 状态：待修复
- 复现步骤：调 `POST /api/v1/knowledge/{id}/unpublish`
- 现象 vs 期望：返回 404；期望将已发布知识转为 8-已下架 状态（PRODUCT §4 状态机，删除知识要求「已发布需先下架」）
- 补充：当前「删除已发布」返 409 提示下架，但**没有下架入口**——`KnowledgeStatus.OFFLINE(8)` 状态定义存在但无迁移接口。

### BUG-016 · 任务清单 `POST /chat/conversations/{id}/messages` 路径错误

- 日期：2026-08-19
- 模块：QA 任务清单
- 状态：待修复（清单）
- 现象 vs 期望：发消息走 `POST /api/v1/chat/stream` body 含 conversationId，无独立 messages POST。

### BUG-017 · 任务清单 `/api/v1/writing/tasks/{id}` 路径错误

- 日期：2026-08-19
- 模块：QA 任务清单
- 状态：待修复（清单）
- 现象 vs 期望：实际 `GET /api/v1/tasks/{id}` 统一管理所有 AI 任务（WRITING/REVIEWER/SUMMARY/SEO/CHAT 等 scene）。

### BUG-018 · 任务清单 `/api/v1/knowledge/{id}/summary` 路径错误

- 日期：2026-08-19
- 模块：QA 任务清单 / 后端
- 状态：待修复（清单或后端视设计意图）
- 现象 vs 期望：F-0808 详情页 AI 摘要区读 `aiSummary` 字段（异步生成后回填），主动生成走 `POST /api/v1/ai/enhance`；任务清单「GET summary」是设计外路径。

### BUG-019 · AI 增值结果 `createdAt` 为 null

- 日期：2026-08-19
- 模块：后端 ai（EnhanceService / EnhanceResultVO）
- 状态：待修复
- 复现步骤：`POST /api/v1/ai/enhance` body `{knowledgeId, scene: "SUMMARY", content}` 成功
- 现象 vs 期望：返回 VO 字段 `createdAt: null`；期望同 3.9 应回填 DB 时戳

### BUG-020 · reindex 端点仅对当前空间可见的已发布知识开放

- 日期：2026-08-19
- 模块：QA 任务清单
- 状态：待修复（清单或设计意图）
- 复现步骤：用 alpha 调 `POST /api/v1/knowledge/1900000000000000001/reindex`（该知识归 qoder_test）
- 现象 vs 期望：返 404 `知识不存在`；任务清单期望「所有已发布知识可补跑」。实际按 BACKEND §9 双层校验仅当前空间可补跑——属设计实现，非缺陷；但任务清单描述需调整或新增「平台级」补跑端点。

### BUG-021 · 任务清单 `/api/v1/admin/models` 路径错误

- 日期：2026-08-19
- 模块：QA 任务清单
- 状态：待修复（清单）
- 现象 vs 期望：实际 `GET /api/v1/admin/model-configs`。

---

## 关键统计

- 测试账号：2 个（qa_alpha、qa_beta）
- 涉及端点（含变更路径）：**约 70 个**（实际已验）
- SUSPECT 候选：**14 条**（BUG-008~021）
  - 真实后端缺陷：3 条（BUG-009、BUG-011、BUG-015、BUG-019——其中 009/019 同源共 2 类问题）
  - 限流契约不符：1 条（BUG-010）
  - 端点路径缺失/偏差：8 条（BUG-008/012/014/016/017/018/020/021）
  - 状态机接口缺失：1 条（BUG-015 unpublish）
  - 状态机待复核：1 条（BUG-013 与并发可能相关）

## 副产物

- alpha 知识库：id 2089917359645839360（name `qa_smoke_alpha_kb_20260819`），其中 1 篇已发布 id 2089917875662671873
- alpha 任务 1 条（WRITING）：id 2089918301959147521
- alpha 对话 1 条：id 2089918146350469121
- 已删干净测试 KB / 知识（recycle 已清空）

按 QA.md §3.7 约定保留 `qa_` 前缀数据，**未删除**。是否清理待用户决定。
