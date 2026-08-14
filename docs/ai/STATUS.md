# xLumen 开发状态与交接文档（AI 必读）

> 更新日期：2026/8/14 16:04
> **本仓库专属**。
> 本仓库由多个 AI 工具协作开发，**本文件是唯一的上下文交接中心**：开始工作前通读，结束时更新。变更历史另见 [CHANGELOG.md](./CHANGELOG.md)。

## 1. 工作流（强制规则）

1. **阅读**：开工前先确认仓库路径为项目根目录；通读本文件 → 任务相关规范文档：产品 [PRODUCT](../product/PRODUCT.md)（唯一功能事实源，第 5 节总表）、后端 [BACKEND](../backend/BACKEND.md)、前端 [FRONTEND](../frontend/FRONTEND.md)、页面 [PROTOTYPE](../frontend/PROTOTYPE.md)、运行命令 [GLOBAL](../global/GLOBAL.md)。
2. **认领**：在第 5 节待办中把任务标为 `已认领（AI 名）`，一次一项；未认领任务可能被其他 AI 同时开始，冲突以先提交者为准。
3. **实现与验证**：只做任务范围内变更；目录结构与功能范围不得偏离 docs（决策 D7）；跑通相关质量门禁（命令见 GLOBAL.md，后端编译需 JDK 25）。
4. **收尾**（不得省略）：更新本文件状态与待办；按 [CHANGELOG.md](./CHANGELOG.md) 头部模板追加一条；代码与文档同一提交。
5. **最高规则**：文档必须与代码实际状态一致，禁止虚构进度；禁止顺手重构、记录密钥。

## 2. 当前里程碑

**MVP 全部 13 个里程碑已完成**：M01~M03（骨架/身份/公开页）、M04（内容管理）、M05（RAG 索引）、M06+M12（AI 基座）、M07（AI 创作）、M08（AI 对话）、M09（AI 增值）、M10（审核发布）、M11（读者纠错）、M13（管理后台）与 F-1301 缓存均已交付并通过运行时验证。**待环境**：本机 Docker/Milvus 未安装，向量检索以 NoopVectorStore 降级运行（索引元数据正常，向量写入待 Milvus 就绪后自动生效）。

**知识平台化重构（2026-08-14 定稿）**：产品定位升级为多用户知识平台（决策 D9 改写），知识库→目录→知识三层体系 + 库级可见性 + 文章概念统一改名知识，设计经用户逐项确认（完整方案 `tmp/knowledge-redesign-proposal.md`）；**阶段 0 文档先行已交付**（PRODUCT/PROTOTYPE/BACKEND/FRONTEND/GLOBAL/README 同步）；阶段 1（概念改名）~阶段 6（验收）见第 5 节待办，按认领机制逐项实施。

> 里程碑完成标准：代码骨架以 M01 定义为准（目录结构与 docs 一致，决策 D7）；MVP 模块以功能总表对应功能验收（完成定义见 PRODUCT.md 第 12 节）。

## 3. 已完成

### 知识平台化重构 · 阶段 0 文档先行（2026-08-14）

- 产品级变更设计定稿并经用户逐项确认（9 项决策 + 4 个开放问题全部拍板：cnt_article 物理改名 cnt_knowledge、/api/v1/articles 全改 /api/v1/knowledge 不保留兼容期、私有库授权 V2、回收站 30 天）。
- PRODUCT.md 重写：定位/角色/三主线闭环/状态机可见性规则/功能总表 73→77 项（MVP 39/V2 26/V3 12；新增 F-0106/F-0208/F-0308/F-0309、F-0305 提 MVP、F-0307 重定义、模块四更名知识索引（RAG））/行为规则（库级可见性/单库单目录/删库回收站/排序规则）/安全（库级越权）。
- PROTOTYPE.md 重写：导航头「知识/知识库/AI小光」+ 8 屏原型（B01 知识流身份聚合、B20 库页、B21 发现页、B22 库管理、B16 回收站提 MVP、B10/B13 发布选库目录）+ 页面清单调整。
- BACKEND.md：模块职责（knowledge 扩库/目录管理、content→knowledge 依赖方向）、核心表清单（cnt_knowledge/kb_knowledge_base/kb_directory/kb_kb_grant）、§13 重写按库切分检索、REST 路径规范（/api/v1/knowledge，决策 D17）。
- FRONTEND.md：模块映射与知识措辞；GLOBAL.md/README.md：定位、模块概览、统计同步；tmp/knowledge-redesign-proposal.md 评审稿存档（状态已确认）。
- 验证：docs 全仓「文章」措辞清零核验（仅保留历史 CHANGELOG 条目与兼容说明）；**代码未动**（阶段 1 起实施）。

### 后端代码风格优化（2026-08-14）

- 移除 19 处冗余 `@PathVariable("xxx")` 显式名称（参数名与路径模板一致时省略，根 POM 显式开启 `-parameters` 保障隐式名称绑定；`ChatController` 会话消息接口路径模板 `{id}` 改名 `{conversationId}` 与参数名对齐）。
- 新增分页基类 `common.dto.PageQueryDTO`（`pageNo=1`/`pageSize=20` 默认值，`@SuperBuilder` 继承）：`ArticleListQueryDTO`、publishing/content 两处 `ArticleQueryDTO`、`CommentQueryDTO` 全部继承（公开文章列表默认 pageSize 由 10 统一为 20，前端始终显式传参无实际影响）。
- 业务类内部类清零：`WorkspaceContext.Scope`→`WorkspaceScope`、`RefreshTokenService.RefreshSession`→`RefreshSession`、`ModelGatewayImpl.CircuitState`、`ChunkingServiceImpl.Section` 全部提取为顶层类型。
- 继续落实参数封装（2026-08-14 下午）：Controller 全部去除 `@RequestParam`——新增 `ReviewQueryDTO`（status 筛选）、`AuditLogQueryDTO`（action 筛选）继承 `PageQueryDTO`；`ReleaseController` 直接绑定基类 `PageQueryDTO`；`ArticleController.list` 复用 `ArticleListQueryDTO` 自动绑定（删除手动 builder）；对应 Service 接口与实现签名统一改 DTO 入参。
- 验证：`mvn verify` BUILD SUCCESS（新增 ArticleQueryDTOTest 4 个断言分页继承默认值）；运行时 API 全端点验证隐式绑定（详情/评论/阅读量/文章 CRUD/任务/审核/模型配置 `{scene}`）+ DTO 查询参数绑定（审核列表/发布列表/审计日志/文章列表，非法参数类型 400）；双前端 E2E 9 通过（blog 8 + admin 1）。

### 内容管理与可见性（M04，2026-08-13）

- F-0301/F-0302 文章 CRUD 与自动保存：cnt_article 新增 version 列（乐观锁插件 @Version）+ idx_article_ws_status 索引；创建/编辑/删除（仅构思/草稿）；自动保存幂等（content 不变跳过写库）；版本校验冲突 409；8 状态机一次定版（1 构思 2 草稿 3 待审核 4 已通过 5 定时发布 6 已发布 7 更新中 8 已下架，存量迁移 1→2、2→6、3→8）。
- F-0307 可见性：公开 1/私有 0，私有文章仅作者可见；公开读过滤 status=6。
- 前端：B10 文章列表页（筛选/分页）、B11 编辑器（MarkdownEditor + useAutoSave 自动保存）、工作台页；路由 /studio/articles、/studio/editor。
- 验证：接口全链路（创建/编辑/删除/自动保存幂等/私有过滤）+ 门禁全绿。

### AI 基座（M06+M12，2026-08-13）

- F-0501 模型网关：ModelGateway 供应商解析（BAILIAN/DEEPSEEK/MOCK）+ 场景模型（表 ai_scene_config 优先、.env 回退）+ 简单熔断（连续 5 次失败熔断 60s）+ 连通性测试接口。
- F-0502 任务底座：ai_task（幂等键去重）+ AiTaskDispatcher 线程池 + 进度 Redis（xlumen:task:progress）+ SSE 任务事件；F-0503 流式 SSE（SseService chunk/citation/done 协议）。
- 关键修复：Spring Boot 4 的 Binder 对 .env 导入的大写属性不做 relaxed binding，AiProperties/KnowledgeAiProperties 全部字段改 @Value 显式占位符绑定（数据库连接正常证明 .env 加载成功，是 @ConfigurationProperties 绑定失效）。
- 验证：AI 审校任务真实百炼调用 COMPLETED（结构化输出过 Schema 校验）、SSE 流式对话、任务幂等。

### AI 创作（M07，2026-08-13）

- F-0601 写作：topic/draft/content 至少一项，异步任务返回 taskId，结构化 title+content 输出。
- F-0604 审校：写作与审校模型异源校验（checkHeterogeneous，默认写作 qwen-plus / 审校 qwen-max），结构化 severity/position/evidence/suggestion 输出。
- 前端：B11 编辑器集成 AiWritePage 写作面板（/studio/writing）、审校结果展示。
- 验证：写作/审校任务真实百炼 COMPLETED；审校检测出错别字（“开发着”）。

### RAG 索引（M05，2026-08-13）

- F-0402~F-0405 发布即索引（决策 D13）：ArticlePublishedEvent（正文快照）→ 清洗 → 切片 → 幂等（SHA-256）→ Embedding（百炼 text-embedding-v4，32 片/批）→ Milvus/Noop 写向量 → kb_chunk 元数据 + kb_index_version（ACTIVATING→ACTIVE→STALE）。
- F-0407 检索：Embedding(query) → VectorStore.search；Noop 降级返回空列表（明确无向量依据，不产生无依据输出）。
- 降级：Milvus 不可达自动 NoopVectorStore（启动 WARN + 元数据照常落库），待 Docker/Milvus 就绪自动生效。
- 验证：发布→索引 ACTIVE（2 chunk）、定时发布→索引 ACTIVE（1 chunk）、检索接口、索引状态接口。

### AI 对话（M08，2026-08-13）

- F-0701 通用问答（SSE 流式）：chat_conversation/chat_message 落库，chunk/citation/done 事件协议，AI 统一称呼“小光”（决策 D14）。
- F-0702 文章级问答：单篇检索限定；Noop 降级时明确回复“未检索到任何相关文章证据”。
- 前端：B00 菜单入口 /studio/chat、ArticleQaDialog 详情页问答弹窗、CitationCard 引用卡片。
- 验证：SSE 流式全链路（会话自动创建/消息持久化/引用溯源事件）。

### AI 增值（M09，2026-08-13）

- F-0801 摘要 / F-0802 SEO：结构化输出（summary / title+keywords+description）落库 ai_enhance_result。
- 前端：EnhancePanel 编辑器增值面板。
- 验证：摘要与 SEO 真实百炼调用成功并落库。

### 审核与发布（M10，2026-08-13）

- F-0902/F-0903 审核：双闸门（AI 审校结果关联 + 人工审核；force_review=0 自动通过人工闸门）；驳回三要素（原因/位置/期望）必填 + 版本校验 409。
- F-0904/F-0905 发布：立即/定时发布（pub_release 幂等键 + uk=article_id+version）；定时发布 PublishJob 每分钟扫描到期执行；发布成功发 ArticlePublishedEvent + 写审计 ARTICLE_PUBLISH + 失效热点缓存。
- 前端：B12 审核中心（正文/AI 审校并排 + 驳回表单）、B13 发布页（立即/定时 + 二次确认）。
- 验证：提交→AI 审校→approve→发布→索引→公开可见全链路；reject 回草稿；定时发布 13:42:22 到期自动执行。

### 读者纠错（M11，2026-08-13）

- F-1001 匿名纠错反馈：无需登录提交（SecurityConfig permitAll），返回追踪号；Redis 限流防刷（同 IP 每分钟 1 条，超限 429）。
- 前端：FeedbackDialog 详情页纠错弹窗。
- 验证：匿名提交成功 + 追踪号；连续提交第 2 次起 429。

### 缓存与管理后台（F-1301 + M13，2026-08-13）

- F-1301 热点缓存：公开详情 cache-aside（xlumen:article:{ws}:{id}，5min TTL + 空值哨兵）、分类/标签聚合缓存、发布/下架 evictAll；Redis 异常降级回源。
- M13 管理后台：工作区设置（intro/forceReview，审计 WORKSPACE_SETTINGS_UPDATE）、模型场景配置（增改查 + 连通性测试）、审计日志查询；admin 前端（LoginPage/WorkspaceSettingsPage/ModelConfigPage/AuditLogPage + 侧边栏布局）。
- 验证：详情/分类/标签缓存键写入 Redis；模型配置更新 + 真实 ping 百炼；审计日志记录 ARTICLE_PUBLISH/REVIEW_REJECT/WORKSPACE_SETTINGS_UPDATE。

### 博客前台公开页（M03，2026-08-12）

- F-0201 文章列表/详情：公开读走默认空间（决策 D9，identity WorkspaceApi 提供）；列表卡片含阅读时间/互动统计（批量 IN 统计防 N+1）；详情为已发布正文快照，Markdown 渲染（markdown-it + DOMPurify XSS 清洗，PRODUCT §10）+ 标题目录导航；草稿/私有文章 404（F-0307 由 ContentApi 保证）。
- F-0202 分类/标签/搜索：cnt_article.category + tags(JSON) 公开筛选维度；关键词标题/摘要 LIKE（MVP 先 LIKE 后 ES）；分类 GROUP BY 聚合、标签 JSON_TABLE 聚合；搜索页组合筛选 + 命中高亮 + 服务端分页；顶栏搜索框。
- F-0203 评论/点赞/阅读量：eng_comment（parent_id 回复 + user_name 冗余）/ eng_like（唯一键幂等切换）；评论与点赞需登录（SecurityConfig 仅 GET 与 view POST 匿名）；阅读量 Redis setIfAbsent 24h 防刷 + cnt_article.view_count 原子自增。
- 页面：B01 首页（文章卡片 + 分类/标签侧栏 + 骨架/空态/重试）、B02 详情（目录导航 + Markdown 渲染 + 点赞/评论）、B03 搜索（组合筛选 + 高亮 + 分页）、B04 关于；App 顶栏升级（导航 + 搜索框）。
- **雪花 ID 精度修复（重要）**：1.9e18 超出 JS Number 安全整数（2^53），后端 Long 统一序列化为 String（JacksonConfig，Boot 4 = Jackson 3 tools.jackson 包 + JacksonModule），前端 ID 类字段 string、统计数值 API 层 Number() 还原（BACKEND.md §5.3 已约定）。
- 表结构：40_content.sql（cnt_article，公开读字段 M03 落地）/ 60_engagement.sql（eng_comment/eng_like）入库；测试文章（公开 3 + 草稿 1 + 私有 1）为验证数据，不进脚本。
- 验证：接口全链路（列表/搜索/分类/标签/详情/404 过滤/阅读量防刷/评论/点赞切换/未登录 401/落库核实）+ E2E 8 个全部通过（public 6 + auth 1 + smoke 1）+ 前端 lint/stylelint/typecheck/test/build 全绿 + 浏览器实测（.browser-check/m03-*.png）。

### 身份与多租户（M02，2026-08-12）

- F-0101 注册/登录/登出/刷新：JWT（HS256，15 分钟短时效）+ 刷新令牌（SHA-256 哈希存 Redis、GETDEL 轮换防重放、登出撤销）；注册即建空间（决策 D9）；登录失败统一提示 + 300ms 统一延迟防枚举（PRODUCT §10）。
- F-0102 工作空间：MVP 单空间（iam_workspace + iam_workspace_member，注册自动创建默认空间与 OWNER 绑定）；`GET /api/v1/workspaces/current` 返回空间与角色。
- F-0103 多角色：iam_role 五种角色（OWNER/ADMIN/EDITOR/AUTHOR/VISITOR）入库，JWT roles claim → ROLE_xxx 权限映射。
- F-0104 双层校验：SecurityConfig 接口权限（方法级安全）+ Service 资源归属校验（WorkspaceContext 来自 JWT claims，不信任 URL/Header/DTO）；401/403 统一 JSON。
- 表结构：10_identity.sql 新增 iam_role/iam_user/iam_workspace/iam_workspace_member（已执行入库）。
- 前端（blog）：B08 登录/注册页（/login）、会话 Store 原子操作（establish/clear/setTokens）、401 单飞刷新（/auth/ 豁免）、路由守卫（guest/authenticated meta）、顶栏登录态与登出。
- 验证：后端单测 3 个通过（注册冲突/登录失败/令牌签发）+ 接口全链路（注册/登录/刷新轮换/重放 401/登出撤销 401/未认证 401）+ E2E 2 个通过（冒烟、注册登录登出流程）+ 浏览器实测（.browser-check/m02-*.png 截图）；前端 lint/stylelint/typecheck/test/build 全绿。

### 代码骨架（M01，2026-08-12）

- `backend/xlumen-server/`：父 POM + 7 个 Maven 模块（common 基座：ApiResponse/ErrorCode/BizException/RequestId(+Filter)/WorkspaceContext；identity/content/publishing/knowledge/ai 按 BACKEND §4 依赖 DAG；boot 装配：启动类、全局异常处理、MyBatis-Plus 分页、系统探活接口 `/api/v1/system/ping`）。
- 配置体系（决策 D8）：`config/.env.example`（模板入库）+ `config/.env`（真实值不入库）；`application.yml` 经 `spring.config.import` 加载 .env；`logback-spring.xml` Appender 显式激活、级别经 `XLUMEN_LOG_LEVEL` 控制。
- SQL 初始化链路：`sql/init/` 00_database.sql ~ 80_ai_enhance.sql（编号契约，90/95 随 V2/V3 创建）+ `scripts/init-db.ps1`（`-EnvFile` 解析 .env，`-Reset` 限 xlumen_dev/xlumen_test 且二次确认）。
- 前端：pnpm Monorepo 双应用 `frontend/xlumen-frontend-blog`（:5173，10 个模块目录）与 `frontend/xlumen-frontend-admin`（:5174，5 个模块目录），Vue 3.5 + Vite 7 + TS strict（含 noUncheckedIndexedAccess 等四项）+ Element Plus + Pinia 3 + ESLint 9 flat + Stylelint + Vitest + Playwright，Design Token 落地 styles/tokens.css。
- 根工程：`package.json`（`pnpm --dir` 代理 lint/stylelint/typecheck/test/build/test:e2e）、`pnpm-workspace.yaml`、`.editorconfig`、`.gitignore`。
- 验证：后端 `mvn -T 1C clean verify` BUILD SUCCESS（JDK 25 / Spring Boot 4.1.0）；应用启动连接 MySQL（159.75.6.183，库 xlumen_dev 已初始化）与 Redis，`/actuator/health` UP；前端 lint/stylelint/typecheck/test/build 全部通过，E2E 冒烟见 CHANGELOG。

### 文档体系（2026-08-12 交付）

- `docs/ai/STATUS.md`（本文件）、`docs/ai/CHANGELOG.md`、`docs/product/PRODUCT.md`。
- `docs/global/GLOBAL.md`、`docs/backend/BACKEND.md`、`docs/frontend/FRONTEND.md`、`docs/frontend/PROTOTYPE.md`、根 `README.md`。
- 交付规范：新文档以实际链路为准；功能清单唯一来源为 PRODUCT.md 第 5 节总表，其他文档引用不复制。

## 4. 进行中

知识平台化重构：阶段 0 已交付，阶段 1~6 待认领（见第 5 节待办表末尾）。

## 5. 待办

按 MVP 模块拆分；每项依赖的文档章节以 PRODUCT.md 第 5 节功能总表为准，实现时同步完成对应后端模块与初始化 SQL（模块职责见 BACKEND.md，页面见 PROTOTYPE.md）。

| 编号 | 阶段/任务 | 依赖文档 | 状态 | 认领人 |
| --- | --- | --- | --- | --- |
| M01 | 代码骨架（backend/xlumen-server 模块划分、frontend 双应用脚手架、SQL 初始化链路） | PRODUCT §5、BACKEND、FRONTEND、GLOBAL §4 | 已完成 | Qoder 代理 |
| M02 | 身份与多租户（F-0101~F-0104） | PRODUCT §5 模块一 | 已完成 | Qoder 代理 |
| M03 | 博客前台公开页（F-0201~F-0203） | PRODUCT §5 模块二、PROTOTYPE B01~B04 | 已完成 | Qoder 代理 |
| M04 | 内容管理与可见性（F-0301~F-0302、F-0307） | PRODUCT §5 模块三、PROTOTYPE B10 | 已完成 | Qoder 代理 |
| M05 | 文章知识索引 RAG：发布即索引（F-0402~F-0405、F-0407） | PRODUCT §5 模块四、BACKEND §13 | 已完成（Noop 降级，Milvus 待环境） | Qoder 代理 |
| M06 | AI 核心引擎（F-0501~F-0503） | PRODUCT §5 模块五 | 已完成 | Qoder 代理 |
| M07 | AI 内容创作（F-0601、F-0604） | PRODUCT §5 模块六、PROTOTYPE B11 | 已完成 | Qoder 代理 |
| M08 | AI 对话：菜单页与文章级问答（F-0701~F-0702） | PRODUCT §5 模块七、PROTOTYPE B00/D01/D02 | 已完成 | Qoder 代理 |
| M09 | AI 内容增值（F-0801~F-0802） | PRODUCT §5 模块八 | 已完成 | Qoder 代理 |
| M10 | 审核与发布（F-0901~F-0905） | PRODUCT §5 模块九、PROTOTYPE B12/B13 | 已完成 | Qoder 代理 |
| M11 | 互动与反馈闭环（F-1001） | PRODUCT §5 模块十 | 已完成 | Qoder 代理 |
| M12 | 技术基础设施（F-1301~F-1303） | PRODUCT §5 模块十三 | 已完成（F-1301 缓存；F-1302/F-1303 门禁/备份随工程实践落地） | Qoder 代理 |
| M13 | 管理后台配置管理（空间/成员/角色 F-1201、审计 F-1202、模型配置 F-0501/F-0502 管理面） | PROTOTYPE A01~A04 | 已完成 | Qoder 代理 |
| KB-1 | 知识平台化重构·阶段 1：概念改名「文章→知识」（全仓 grep 清单：文档/后端类名接口事件审计常量/前端路由文案组件/测试断言；物理表 cnt_article→cnt_knowledge、路径 /api/v1/articles→/api/v1/knowledge；纯改名零行为变更） | tmp/knowledge-redesign-proposal.md §3/§12 | 待认领 | — |
| KB-2 | 知识平台化重构·阶段 2：数据模型（kb_knowledge_base/kb_directory 新表 DDL、cnt_knowledge 改造 kb_id/directory_id 去 category/visibility、kb_chunk/kb_index_version 加 kb_id、迁移脚本幂等） | 方案 §5/§10、BACKEND §7/§8 | 待认领 | — |
| KB-3 | 知识平台化重构·阶段 3：后端能力（库 CRUD/回收站/目录树/排序/可见性过滤/检索按库/发布选库，F-0305/F-0307/F-0308/F-0309/F-0407） | 方案 §7/§9、BACKEND §13 | 待认领 | — |
| KB-4 | 知识平台化重构·阶段 4：前端页面（导航头/知识流 B01/库页 B20/发现页 B21/库管理 B22/回收站 B16/发布弹窗/AI 对话范围选择器，F-0208/F-0308） | PROTOTYPE §7 | 待认领 | — |
| KB-5 | 知识平台化重构·阶段 5：存量迁移执行与数据校验（默认公开库/默认私有库、category 平铺目录） | 方案 §10 | 待认领 | — |
| KB-6 | 知识平台化重构·阶段 6：全量验收（完成定义 PRODUCT §12 + 双前端 E2E + 文档一致性核验） | PRODUCT §12 | 待认领 | — |

> 说明：数据分析与知识保鲜（模块十一）为 V2/V3 功能，平台治理（模块十二）MVP 部分（空间设置/审计）随 M13 落地、其余 V2/V3 随依赖模块迭代实现；阶段调整须经 CHANGELOG 记录（决策 D10）。

## 6. 文档一致性核验

| 编号 | 核验项 | 状态 |
| --- | --- | --- |
| W6 | 文档体系一致性核验 | 2026-08-12 通过（二次核验），会话 #6 前后端职责重组与目录变更后已同步 8 份文档，待代码骨架阶段复验 |
| W7 | 会话 #6 变更同步核验 | 2026-08-12 完成：功能总表 73 项（MVP 37/V2 24/V3 12）与 PROTOTYPE 页面清单（B00~B19、A01~A07、D01/D02）、GLOBAL/README 命令路径、BACKEND 模块表交叉一致 |

## 7. 最近变更

> 历史记录已按用户要求清空，CHANGELOG 仅保留最新一条；完整变更以 [CHANGELOG.md](./CHANGELOG.md) 为准。

- 2026/8/14 16:04 · ZCode：知识平台化重构设计定稿 + 阶段 0 文档先行——多用户知识平台定位（D9 改写）、知识库→目录→知识三层体系（D16）、文章概念统一改名知识（D17）、RAG 按库切分（D13 改写）；PRODUCT 功能总表 73→77 项、PROTOTYPE 8 屏原型、BACKEND/FRONTEND/GLOBAL/README 同步；4 个开放问题全部确认（cnt_knowledge 物理改名、/api/v1/knowledge 不保留兼容、授权 V2、回收站 30 天）；阶段 1~6 已列入待办待认领。

- 2026/8/14 14:00 · ZCode：后端代码风格优化——①移除 19 处冗余 `@PathVariable("xxx")` 显式名称（根 POM 显式开启 `-parameters`；ChatController 路径模板 `{id}`→`{conversationId}` 对齐参数名）；②新增分页基类 `common/dto/PageQueryDTO`（默认 1/20，`@SuperBuilder` 继承），4 个分页 QueryDTO 全部继承并去除重复字段；③业务类内部类清零（WorkspaceScope/RefreshSession/CircuitState/Section 提取为顶层类）；④Controller 全部去除 `@RequestParam`：新增 ReviewQueryDTO/AuditLogQueryDTO，ReleaseController 直接绑定 PageQueryDTO，ArticleController 复用 ArticleListQueryDTO 自动绑定，对应 Service 签名改 DTO 入参；BACKEND §5.1 新增分页基类/隐式命名/禁内部类规范；验证：mvn verify 全绿（新增 ArticleQueryDTOTest 4 断言）、运行时 API 全端点隐式绑定验证通过（公开读/文章 CRUD/任务/审核/模型配置/审核列表/发布列表/审计日志，非法参数类型 400）、双前端 E2E 9 通过（blog 8 + admin 1）。

- 2026/8/13 14:00 · Qoder 代理：MVP 全部里程碑交付（M04~M13 + F-1301）——内容管理（CRUD+自动保存+8 状态机）、AI 基座（网关+任务底座+SSE）、AI 创作（写作+审校异源校验）、RAG 索引（发布即索引+Noop 降级）、AI 对话（小光+引用溯源）、AI 增值（摘要+SEO）、审核发布（双闸门+定时发布 PublishJob）、读者纠错（匿名+限流）、热点缓存与管理后台；修复 Spring Boot 4 .env 大写属性 relaxed binding 失效（@Value 显式绑定）、GlobalExceptionHandler 404/JSON 解析异常处理、ai 与 publishing 同名 Bean 冲突、审校默认模型 qwen-max（异源）；运行时全链路验证通过（真实百炼调用：审校/写作/摘要/SEO/Embedding/连通性测试），Milvus 待环境（Noop 降级）。

- 2026/8/13 01:20 · Qoder 代理：后端代码风格统一重构——**去“业务域”概念（传统 MVC 扁平包结构）**：publishing 的 engagement 域拆为 Comment/Like 资源（CommentController/LikeController + CommentService/LikeService，URL 不变）；identity 去 iam 域（扁平化 + WorkspaceApiImpl 移位 + TokenVO/UserProfileVO/WorkspaceVO 改 class+Lombok）；content 去 editor 域且 DTO 改 class+Lombok、ContentApi.listPublished 参数封装（ArticleQueryDTO）；修复历史遗留：identity 单行乱码损坏文件从 git 恢复重建、(wenhailong) 垃圾目录清理、PublicArticleServiceImpl 未适配新签名；BACKEND §3/§4/§5/§7/§8 同步去域措辞 + §5.1 新增编码风格规范（参数封装/DTO·VO class+Lombok/命名规则）；mvn verify BUILD SUCCESS + 3 单测通过。
- 2026/8/12 21:40 · Qoder 代理：M03 博客前台公开页交付——F-0201 列表/详情（Markdown 渲染 + XSS 清洗 + 目录导航）、F-0202 分类/标签/搜索（组合筛选 + 命中高亮 + 分页）、F-0203 评论/点赞/阅读量（Redis 24h 防刷）；cnt_article/eng_comment/eng_like 入库；B01~B04 四页 + 顶栏导航搜索；修复雪花 ID 精度（Long→String 全局序列化）；后端接口全链路/E2E 8 个/门禁/浏览器实测全部通过（详见第 3 节与 CHANGELOG）。
- 2026/8/12 19:50 · Qoder 代理：M02 身份与多租户交付——F-0101 注册/登录/登出/刷新（JWT + 刷新令牌 GETDEL 轮换防重放、防枚举统一延迟）、F-0102 注册即建空间、F-0103 五角色体系、F-0104 双层校验（接口权限 + Service 资源归属）；10_identity.sql 四张表入库；blog 前端 B08 登录注册页 + 401 单飞刷新 + 路由守卫；后端单测/接口全链路/E2E/浏览器实测全部通过（详见第 3 节与 CHANGELOG）。
- 2026/8/12 19:05 · Qoder 代理：M01 代码骨架交付——后端 7 模块骨架与 common 基座类型、.env 配置体系、SQL 初始化链路（init-db.ps1 + sql/init 编号脚本，开发库 xlumen_dev 已在 159.75.6.183 初始化）、前端 blog/admin 双应用脚手架与根工程配置；后端编译/启动验证与前端质量门禁全部通过（详见第 3 节与 CHANGELOG）。
- 2026/8/12 18:03 · Qoder 代理：Maven 模块压缩 12→7（新增决策 D15）——按未来微服务边界合并：identity(+platform)、content(+analytics)、publishing(+engagement)、ai(+chat+ai-enhance)，模块内按业务域分包、表前缀不变；BACKEND 模块表/依赖 DAG/分包规则、GLOBAL 结构树、FRONTEND 模块映射、README 同步。
- 2026/8/12 17:58 · Qoder 代理：前台导航 F-0701 菜单标签 [AI 对话]→[AI 助理]，位置调整为首页右侧、分类左侧；PROTOTYPE/FRONTEND 同步。
- 2026/8/12 17:51 · Qoder 代理：AI 命名确定——产品内 AI 统一称呼为**小光**（新增决策 D14），PRODUCT §8 命名规则、PROTOTYPE B00/B07/D01/D02 文案同步。
- 2026/8/12 17:46 · Qoder 代理：产品与工程重大变更——前后端职责重组（blog 承载创作/阅读/互动/AI 对话全链路，admin 仅管理员配置管理：空间/成员/角色、模型配置、审计）、主页恢复文章展示页（B00 降为菜单入口）、发布即索引（取消外部资料导入 F-0401/F-0406）、新增文章可见性 F-0307（私有文章亦建索引、检索按身份过滤）、F-1201/F-1202 提升 MVP、仓库目录重组（backend/xlumen-server、frontend/xlumen-frontend-blog :5173、frontend/xlumen-frontend-admin :5174，工程文件留根目录）；功能总表 74→73 项（MVP 37/V2 24/V3 12）；新增决策 D11~D13；技术约定补充（界面生成用 frontend-design Skill、优先复用工具类/开源框架、必要注释）；8 份文档同步更新。

## 8. 关键决策摘要（详见规范文档，勿推翻）

| 编号 | 决策 |
| --- | --- |
| D1 | 模块化单体，非微服务；`xlumen-boot` 是唯一装配入口（GLOBAL/BACKEND） |
| D2 | 模块内部传统 MVC，不引入六边形/DDD 等复杂分层（GLOBAL/BACKEND） |
| D3 | 跨模块反向流程用 RocketMQ + Outbox 事件解耦，不新增反向 Maven 依赖（BACKEND） |
| D4 | OpenAPI 是前后端接口契约唯一来源，前端据此生成类型（PRODUCT §7） |
| D5 | 当前直接维护初始化 SQL，不建升级机制（BACKEND） |
| D6 | Redis 只存短期状态，业务事实以 MySQL 为准（PRODUCT §9） |
| D7 | **文档先行**：目录结构以 docs 为唯一事实源，代码骨架不得偏离（PRODUCT §5） |
| D8 | **配置唯一载体 .env**：禁止第二种配置载体（GLOBAL） |
| D9 | **多用户知识平台**：默认单空间使用；任何注册用户可创建知识库并公开分享，访客可浏览所有公开库；团队模式（成员/角色/空间切换）V2 可选启用（PRODUCT §2） |
| D10 | **阶段标注（MVP/V2/V3）为规划非承诺**：调整须经 CHANGELOG 记录（PRODUCT §5） |
| D11 | **应用职责划分**：blog（:5173）承载知识创建/编辑/发布/阅读/互动与 AI 对话全链路；admin（:5174）仅管理员配置管理（空间/成员/角色、模型、审计），不参与内容流转（PROTOTYPE §2） |
| D12 | **仓库目录**：后端 backend/xlumen-server，前端 frontend/xlumen-frontend-blog 与 frontend/xlumen-frontend-admin，scripts 与根工程配置留仓库根（GLOBAL §4） |
| D13 | **发布即索引（按知识库切分）**：知识发布自动建 RAG 索引（按 kb_id 切分），取消外部资料导入与 URL 抓取；私有库知识亦建索引，检索按可见库集合过滤（访客仅公开库已发布、库主全部含私有）（PRODUCT §6、BACKEND §13） |
| D14 | **AI 命名**：产品内所有面向用户的 AI 能力（对话/问答/访客助手/写作与审校反馈等）统一称呼为**小光**，前台导航标签为「AI小光」，界面文案不得使用其他 AI 名称（PRODUCT §8） |
| D15 | **Maven 模块压缩 12→7**：按未来微服务拆分边界合并——identity（+platform）、content（+analytics）、publishing（+engagement）、knowledge、ai（+chat+ai-enhance）+ common/boot；**模块内统一传统 MVC 扁平结构（不引入业务域包，去 engagement/editor/iam 等域概念）**，表前缀保持独立，拆分边界以模块 + 表前缀为准（BACKEND §4/§5） |
| D16 | **知识库体系**：知识=文章（概念统一）；空间→知识库→多级目录→知识三层；单库单目录；可见性库级决定（公开/私有/授权名单 V2），删除文章级可见性；目录树替代分类（标签保留）；删库连带回收站（默认 30 天）；知识不可跨库移动（仅复制或重新发布）（PRODUCT §4/§5/§6，BACKEND §13） |
| D17 | **概念统一（文章→知识）**：全项目「文章」统一改名「知识」——文档措辞、后端类名/接口/事件/审计常量、前端路由/文案/组件、测试断言全量替换；物理表名 `cnt_article`→`cnt_knowledge`、接口路径 `/api/v1/articles`→`/api/v1/knowledge` 同步全改，**不保留兼容期**（前后端同仓同 PR 切换）（BACKEND §10） |

## 9. 环境速查

- 后端：`JAVA_HOME` 必须指向 JDK 25；Maven 3.9 构建（命令见 GLOBAL.md）。
- 前端：Node 20+ 与 pnpm 9+（命令见 GLOBAL.md）。
- 中间件：MySQL 8.4 / Redis 为远程实例，配置唯一载体为 `backend/xlumen-server/config/.env`（决策 D8，参数与模板见 GLOBAL.md）。
- SQL 初始化：链路由 M01 代码骨架阶段按 BACKEND.md 建立（编号以实际为准），脚本与代码同一提交。
