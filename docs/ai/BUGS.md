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

- BUG-007 待修复（2026/8/19 记录，见下方小节）
- BUG-008 待修复（2026/8/19 全功能测试·UI）
- BUG-009 待修复（2026/8/19 全功能测试·UI）
- BUG-010 待修复（2026/8/19 全功能测试·后端）
- BUG-012 待修复（2026/8/19 全功能测试·后端）
- BUG-013 待修复（2026/8/19 全功能测试·后端）
- BUG-014 待修复（2026/8/19 全功能测试·后端）
- BUG-015 待修复（2026/8/19 全功能测试·后端·待复核）
- BUG-016 待修复（2026/8/19 全功能测试·后端）
- BUG-017 待修复（2026/8/19 全功能测试·UI）

> 2026/8/19 11:40 全功能测试发现新候选 9 条（BUG-008~017，跳号 011 留给 BUG-007 同源增强候选）。本批次 BUG 中：BUG-010 与 BUG-011 同根因（createdAt 时戳字段不回填），记录一条；UI 端 BUG-008/009/017 来自 browser-use 实测，后端 BUG-012~016 来自子代理 API 冒烟（70 端点）。**仅记录，按用户明确要求修复**。

---

## BUG-007 · 知识「已通过但未发布」中间态在公开读路径不可见

- 日期：2026-08-19
- 模块：后端 content（ContentApiImpl 公开读路径强制 status=6 过滤）
- 状态：待修复
- 复现步骤：1. 编辑知识 2089895161090592769 → 提交审核；2. 管理员在审核中心「通过」（status 4=APPROVED，未触发出版）；3. 该知识仍可在编辑页查看（`KnowledgeServiceImpl.get` 无 status 过滤）；4. 但在「默认公开库」公开读列表 / 默认 kbId=2090000000000000001 详情页 / 用户主页均不可见
- 现象 vs 期望：现象 = 知识存在且可编辑，但 `ContentApiImpl.listPublished` / `getPublished` 强制等值过滤 `status=6`（已发布），挡掉所有「APPROVED + published_at=NULL」的「已通过未发布」中间态记录；期望 = 列表允许展示已通过未发布（status=4）或审核通过后自动触发出版（status=6 + published_at=now）
- 根因证据（bug-007-repro.md）：
  - `xlumen-content/src/main/java/com/calwen/xlumen/content/service/impl/ContentApiImpl.java` 第 35、51-56、94-105 行：列表/详情公开读路径 `eq(Knowledge::getStatus, 6)`
  - 2089895161090592769 DB 实际 `status=4` + `published_at=NULL`（SQL 验证）
  - owner / workspace / kb_id / visibility 全部正确：kb_id=2090000000000000001 = qoder_test 空间「默认公开库」visibility=1 status=0
  - `VisibilityServiceImpl.resolveVisibleKbIds` 推导无 bug——与本 BUG 原推测的「按 owner_user_id 推导可见库集合」方向不符
  - 修复候选三方向（subagent 已交付，未实施）：①公开读路径接受 status=4 + 6；②审核通过后自动发布（更新 status=6 + published_at=now）；③前端「默认公开库」调用非鉴权接口（跨用户聚合）
- 补充：本 BUG 与 BUG-008（HomePage 公开知识库语义错）症状重叠但根因不同——BUG-008 是前端取数语义错（仅看「我的公开库」），BUG-007 是后端公开读路径过滤过严

---

## BUG-008 · HomePage 左栏「公开知识库」与 B21 语义不一致

- 日期：2026-08-19
- 模块：前端 blog HomePage（左栏 H2 文案 + computed 取数）
- 状态：待修复
- 复现步骤：1. 登录后访问首页 `/`；2. 看左栏 H2 标题「公开知识库」+ 列表内容（实际是「我的公开库」过滤结果，非全平台公开库）
- 现象 vs 期望：现象 = 标题「公开知识库」但实际数据 = `myKnowledgeBases.filter(visibility===1)`（HomePage.vue:41-43），等于「当前用户自己的公开库」；期望 = 标题与数据语义一致——若要展示「全平台公开库」需新接口（PROTOTYPE 备注 V2 提供），若展示「我的公开库」则 H2 应改为「我的公开库」
- 补充：PROTOTYPE B21 `/knowledge-bases` 页面自说明「**全平台公开知识库聚合将在 V2 提供**」并 H2 用「我的知识库」；B01 HomePage 与 B21 文案/语义错位；alpha 无公开库时显示「暂无公开知识库」（空态），但 alpha 在其他用户公开库下应是全平台可见；本 BUG 与 BUG-007 症状重叠但根因不同（BUG-007 是后端公开读路径过滤过严，本 BUG 是前端取数语义错）

---

## BUG-009 · 知识详情页已登录态仍显示「登录后可点赞、收藏与评论」

- 日期：2026-08-19
- 模块：前端 blog KnowledgeDetailPage
- 状态：待修复
- 复现步骤：1. 登录 qa_alpha_20260819；2. 访问 `/knowledge/1900000000000000002` 详情页；3. 看「收藏」按钮下方仍有「**登录后可点赞、收藏与评论**」提示
- 现象 vs 期望：现象 = 已登录态显示「登录后可点赞、收藏与评论」（KnowledgeDetailPage 应已具备点赞/收藏/评论能力）；期望 = 登录态下隐藏该提示
- 补充：赞踩/收藏/评论按钮本身可点击且功能正常，提示语只是显示逻辑错

---

## BUG-010 · 评论/AI 增值结果 `createdAt` 字段为 null

- 日期：2026-08-19
- 模块：后端 publishing（CommentService）+ ai（EnhanceServiceImpl/EnhanceResultVO）
- 状态：待修复
- 复现步骤：①前端 UI：登录 qa_alpha_20260819，访问 `/knowledge/1900000000000000002`，在评论区输入并点击「发表评论」→ 新评论显示「**20684 天前**」≈ 56 年前；②后端 API：`POST /api/v1/public/knowledge/1900000000000000001/comments` body `{"content":"..."}` → 返回 VO 字段 `createdAt: null`；`POST /api/v1/ai/enhance` scene=SUMMARY → 同样 `createdAt: null`
- 现象 vs 期望：现象 = 新建评论/AI 增值结果落库后 `createdAt` 字段为 null，前端「xx 天前」格式化函数把 null 当 1970-01-01 → 显示 20684 天前；期望 = 落库后立即回填 DB 时戳
- 补充：浏览器测试先观察到「20684 天前」UI 异常，API 冒烟子代理定位根因为后端时戳字段不回填。涉及 `eng_comment.created_at` 与 `ai_enhance_result.created_at` 两个表，可能为同一 mapper 自动填充机制遗漏；`cnt_knowledge`、`pub_review` 等其他表的 createdAt 字段正常（QA 时已抽查）

---

## BUG-012 · 读者纠错同 IP 连发未触发 429 限流

- 日期：2026-08-19
- 模块：后端 publishing（FeedbackService 限流）
- 状态：待修复
- 复现步骤：同 IP 在 1 秒内连续两次 `POST /api/v1/public/knowledge/{id}/feedback` body `{"problem":"..."}`
- 现象 vs 期望：现象 = 两次均返回 200 + trackNo；期望第二次返回 429（QA.md §3.7 / STATUS §3「M11 同 IP 每分钟 1 条，超限 429」契约）
- 补充：可能是 Redis 限流计数 / Lua 脚本未生效，或 IP 取自 nginx 反代场景下未识别 X-Forwarded-For

---

## BUG-013 · 知识 update 接受越界 `kbId/directoryId` 静默写入

- 日期：2026-08-19
- 模块：后端 content（KnowledgeServiceImpl.update）
- 状态：待修复
- 复现步骤：`PUT /api/v1/knowledge/{id}` body `{"kbId":"9999999999","directoryId":"9999999999",...}`
- 现象 vs 期望：现象 = kbId 越界值被静默丢弃（保留原 KB），directoryId 错误值被接受并落库；期望整体 400 拒绝（KnowledgeApi.checkOwnership 校验未走通）
- 补充：与 KB 知识计数 0 同源，错误 directoryId 让知识从 KB 计数中排除。8-16 修复对 create 路径加了 checkOwnership，但对 update 路径遗漏（status=PENDING 之前 update 没走同校验）

---

## BUG-014 · 知识版本历史端点缺失

- 日期：2026-08-19
- 模块：后端 content
- 状态：待修复
- 复现步骤：调 `GET /api/v1/knowledge/{id}/versions`
- 现象 vs 期望：现象 = 返回 404；期望返回版本列表（与 PRODUCT §5 F-0303「自动保存 + 历史版本」对应）
- 补充：`cnt_knowledge_version` 表已存在（8-12 M04 创建）但无对外 controller

---

## BUG-015 · 提交审核后作者侧 `getOwned` 偶发 404（待复核）

- 日期：2026-08-19
- 模块：后端 content（KnowledgeServiceImpl.getOwned）
- 状态：待修复（**与并发巡检可能冲突**，已用新建知识复测未复现——记为 SUSPECT）
- 复现步骤：`POST /api/v1/knowledge` → `POST /api/v1/reviews` → `GET /api/v1/knowledge/{id}`
- 现象 vs 期望：现象 = 第 3 步 404 `知识不存在`；期望作者可继续查看并进入编辑（已通过 list 接口证实记录存在）
- 补充：list 显示 status=2 (DRAFT) 时仍 404；list 显示 status=4 (APPROVED) 时同样 404；3 次复现中有 1 次为并发场景，**根因待复核**。建议排查 `getOwned` 的 `eq(workspaceId)` + `eq(authorId)` 是否被 race 影响，或 D17 后 authorId 字段在 submit-review 流程中是否被覆写

---

## BUG-016 · 下架（unpublish）端点完全缺失

- 日期：2026-08-19
- 模块：后端 publishing / content
- 状态：待修复
- 复现步骤：调 `POST /api/v1/knowledge/{id}/unpublish`（任意 id）
- 现象 vs 期望：现象 = 返回 404；期望将已发布知识转为 8-已下架 状态（PRODUCT §4 状态机）
- 补充：当前「删除已发布」返 409 提示「已发布需先下架」，但**没有下架入口**——`KnowledgeStatus.OFFLINE(8)` 状态定义存在但无迁移接口；与 B07 KB-3 设计「删除已发布需先下架」配套缺失

---

## BUG-017 · 编辑态提示「归属库与目录不可修改」但目录可改

- 日期：2026-08-19
- 模块：前端 blog KnowledgeEditorPage（提示文案 vs 实际行为错位）
- 状态：待修复
- 复现步骤：1. 登录；2. 打开已存在草稿的编辑页 `/studio/knowledge/{id}/edit`（非新建）；3. 看「**当前状态：草稿 · 归属库与目录不可修改（单库单目录，决策 D16）**」+「所属目录」select
- 现象 vs 期望：现象 = 提示文案说「归属库与目录不可修改」，但「所属知识库」select 禁用（✓ 一致）而「所属目录」select **未禁用**（✗ 不一致）；期望 = 文案与行为一致——若目录允许改则文案去目录字样，若不允许则 select 禁用
- 补充：模板 line 327 `:disabled="!kbId"`（kbId 有值即不锁）—— 设计本就允许目录调整，提示文案与行为错位

---

（历史清空说明：2026/8/18 20:47 清空——BUG-006 知识详情页排版错乱经用户要求修复并验证通过，编号不回收，详见 CHANGELOG 2026/8/18 20:47 条目。遗留运维事项：①后端已重启使本批新接口生效；②存量知识 AI 摘要需等下次发布事件自动生成，已发布知识暂不展示摘要区块）
