# xLumen 知识平台化重构 · 代码实现方案（v1.0）

> 状态：**执行依据**。后续代码修改（KB-1~KB-6）严格按本方案逐阶段完成；每阶段独立提交、独立验证，不得跨阶段混合变更。
> 依据：[设计方案](./knowledge-redesign-proposal.md)（评审定稿）+ docs 正式文档（PRODUCT/PROTOTYPE/BACKEND/FRONTEND）；任务认领见 `docs/ai/STATUS.md` §5（KB-1~KB-6）。
> 更新日期：2026/8/14

---

## 0. 总原则与环境

**执行规则**：

1. **阶段顺序**：KB-1 纯改名（零行为变更）→ KB-2 数据模型 → KB-3 后端能力 → KB-4 前端页面 → KB-5 存量迁移 → KB-6 验收。前序阶段验证通过后才开始下一阶段。
2. **提交边界**：每阶段拆 1~3 个 Conventional Commits 提交（示例：`refactor(content): 文章概念统一改名知识`）；文档与代码同一提交（决策 D7）。
3. **认领**：开工前在 STATUS.md §5 把对应 KB-x 标为 `已认领（AI 名）`，一次一项；收尾更新 STATUS/CHANGELOG。
4. **门禁**：后端 `mvn -T 1C clean verify`（**必须 `export JAVA_HOME` 指向 JDK 25**）；前端 `pnpm lint/stylelint/typecheck/test/build`；E2E 前先清理 5173/5174 残留 vite 实例（`netstat` 查端口再 kill），避免 playwright 复用旧实例。
5. **命名契约**（KB-1 起生效，全局强制）：资源词 `Knowledge`；知识接口路径 `/api/v1/knowledge`（不可数，旧 `/api/v1/articles` 废弃不保留兼容）；表 `cnt_knowledge`；审计常量 `KNOWLEDGE_PUBLISH` 等；缓存键 `xlumen:knowledge:*`。
6. **不改行为**：KB-1 只改名；KB-3 才允许改行为。禁止顺手重构、加新依赖（KB-2/KB-3 如需依赖先验证 JDK25/Boot4 兼容并记 CHANGELOG）。

**改名清单基于 2026-08-14 实际代码扫描**（后端 46 个文件、前端 27 个文件、SQL 6 个脚本，如扫描有遗漏按「grep 文章/Article/article/cnt_article 清零」原则补齐）。

---

## 1. KB-1 概念改名（文章 → 知识）——纯改名，零行为变更

### 1.1 后端改名清单（file-level）

**xlumen-common**

| 现文件/符号 | 改为 |
| --- | --- |
| `event/ArticlePublishedEvent.java` | `event/KnowledgePublishedEvent.java`（字段 `articleId`→`knowledgeId`、`title` 保留；事件类注释同步） |

**xlumen-content（15 个文件 + 内部符号）**

| 现 | 改 |
| --- | --- |
| `controller/ArticleController.java`（`/api/v1/articles`） | `KnowledgeController.java`（`/api/v1/knowledge`）；方法参数 `articleId`→`knowledgeId` |
| `service/ArticleService.java` / `impl/ArticleServiceImpl.java` | `KnowledgeService` / `KnowledgeServiceImpl`；方法名 `*Article*`→`*Knowledge*` |
| `mapper/ArticleMapper.java`、`entity/ArticleEntity.java` | `KnowledgeMapper`、`KnowledgeEntity`（映射表 `cnt_knowledge`） |
| `enums/ArticleStatus.java` | `KnowledgeStatus.java`（枚举值不变，仅类名与注释） |
| `dto/CreateArticleDTO.java`、`dto/UpdateArticleDTO.java`、`dto/ArticleListQueryDTO.java` | `CreateKnowledgeDTO`、`UpdateKnowledgeDTO`、`KnowledgeListQueryDTO` |
| `vo/ArticleVO.java`、`vo/ArticleListItemVO.java` | `KnowledgeVO`、`KnowledgeListItemVO` |
| `api/ContentApi.java`、`impl/ContentApiImpl.java` | 方法 `saveAiResult`/`listPublished` 等的 `Article*` DTO 参数类型改 `Knowledge*`；内部编排改调用新 Service 名 |
| `api/dto/ArticleDetailDTO.java`、`ArticlePublishDTO.java`、`ArticleQueryDTO.java`、`EditorArticleDTO.java`、`PublishedArticleDTO.java` | `KnowledgeDetailDTO`、`KnowledgePublishDTO`、`KnowledgeQueryDTO`、`EditorKnowledgeDTO`、`PublishedKnowledgeDTO` |

**xlumen-publishing（12 个文件 + 内部符号）**

| 现 | 改 |
| --- | --- |
| `controller/PublicArticleController.java`（`/api/v1/public/articles`） | `PublicKnowledgeController.java`（`/api/v1/public/knowledge`） |
| `service/PublicArticleService.java` / `impl/PublicArticleServiceImpl.java` | `PublicKnowledgeService` / `PublicKnowledgeServiceImpl` |
| `service/HotArticleCacheService.java` / `impl/HotArticleCacheServiceImpl.java` | `HotKnowledgeCacheService` / `HotKnowledgeCacheServiceImpl`；缓存键 `xlumen:article:{ws}:{id}`→`xlumen:knowledge:{ws}:{id}`、失效模式 `xlumen:article:*`/`xlumen:articles:list:*`/`xlumen:categories:*`/`xlumen:tags:*`→`xlumen:knowledge:*` 前缀族（分片化改键规则见 KB-3） |
| `dto/ArticleCardVO.java`、`dto/ArticleDetailVO.java`、`dto/ArticleQueryDTO.java` | `KnowledgeCardVO`、`KnowledgeDetailVO`、`KnowledgeQueryDTO` |
| `controller/ReviewController.java`、`service/ReviewService.java` / `impl/ReviewServiceImpl.java`、`impl/ReleaseServiceImpl.java`、`impl/CommentServiceImpl.java`、`impl/FeedbackServiceImpl.java`、`impl/LikeServiceImpl.java` | 参数/字段/日志措辞 `articleId/articleTitle`→`knowledgeId/knowledgeTitle`；审计写入 `ARTICLE_PUBLISH`/`ARTICLE`→`KNOWLEDGE_PUBLISH`/`KNOWLEDGE`（历史数据保留原值，展示层兼容，见 §1.4） |

**xlumen-knowledge（7 个文件）**

| 现 | 改 |
| --- | --- |
| `job/ArticlePublishedEventListener.java` | `KnowledgePublishedEventListener.java`（消费 `KnowledgePublishedEvent`） |
| `service/IndexPipelineService.java` / `impl/IndexPipelineServiceImpl.java`、`api/KnowledgeApi.java` / `impl/KnowledgeApiImpl.java` | 方法 `indexArticle`→`indexKnowledge`、参数 `articleId`→`knowledgeId`；`IndexRequestDTO`/`SearchRequestDTO`/`SearchResultDTO` 字段与注释同步（kbId 字段在 KB-2 增加） |
| `impl/MilvusVectorStore.java`、`impl/NoopVectorStore.java`、`impl/RetrievalServiceImpl.java` | `articleId` 参数/注释→`knowledgeId` |

**xlumen-ai（5 个文件）**

| 现 | 改 |
| --- | --- |
| `controller/ChatController.java` | `POST /articles/{articleId}/ask`→`POST /knowledge/{knowledgeId}/ask` |
| `service/ChatService.java` / `impl/ChatServiceImpl.java`、`impl/EnhanceServiceImpl.java`、`impl/ReviewServiceImpl.java` | 参数/提示文案（如「未检索到任何相关文章证据」→「…相关知识证据」）改名 |

**xlumen-identity（2 个文件）**：`ActivityLogEntity` 注释、`ActivityLogService` Javadoc 中 `ARTICLE_PUBLISH`/`ARTICLE` 示例→`KNOWLEDGE_PUBLISH`/`KNOWLEDGE`（仅注释，不改枚举/表）。

**xlumen-boot（1 个文件）**：`SecurityConfig.java` 匿名放行路径 `/api/v1/public/articles/*/view`、`/api/v1/public/articles/*/feedback`→`/api/v1/public/knowledge/*/view|feedback`。

**接口路径总表（KB-1 一次性切换，不保留旧路径）**

| 现路径 | 改后路径 |
| --- | --- |
| `/api/v1/articles`（content 知识 CRUD） | `/api/v1/knowledge` |
| `/api/v1/public/articles`（公开读） | `/api/v1/public/knowledge` |
| `/api/v1/public/articles/{id}/comments|like|feedback` | `/api/v1/public/knowledge/{id}/comments|like|feedback` |
| `.../articles/{articleId}/ask`（AI 问答） | `.../knowledge/{knowledgeId}/ask` |
| `.../articles/{articleId}/index-status`（索引状态） | `.../knowledge/{knowledgeId}/index-status` |

### 1.2 SQL 改名清单（双轨：更新 init 脚本 + 提供迁移脚本）

- **更新初始化脚本（干净安装路径）**：
  - `40_content.sql`：`cnt_article`→`cnt_knowledge`（表名+注释；索引 `idx_article_*`→`idx_knowledge_*`）；列注释「文章」→「知识」。
  - `20_knowledge.sql`：`kb_chunk.article_id`→`knowledge_id`、`kb_index_version.article_id`→`knowledge_id`（含索引名 `idx_chunk_article_*`→`idx_chunk_knowledge_*`）；注释同步。
  - `50_publishing.sql`：`pub_review.article_id/article_title`→`knowledge_id/knowledge_title`、`pub_release` 同；唯一键 `uk_release_ws_article_version`→`uk_release_ws_knowledge_version`。
  - `60_engagement.sql`：`eng_comment.article_id`、`eng_like.article_id`、`eng_feedback.article_id`→`knowledge_id`（含索引/唯一键名）；注释同步。
  - `80_ai_enhance.sql`：`ai_enhance_result.article_id`→`knowledge_id`（以实际扫描为准）；注释同步。
- **新增迁移脚本 `85_kb_migration.sql`**（存量开发库/测试库路径，编号 85 介于 80/90，幂等可重跑）：`RENAME TABLE cnt_article TO cnt_knowledge` + 各表 `ALTER TABLE ... CHANGE COLUMN article_id knowledge_id`（article_title→knowledge_title）+ 索引改名 + 注释更新（KB-1 只做改名；KB-2 的结构变更追加在同一脚本后续小节或独立 86 脚本，见 §2）。

### 1.3 前端改名清单（xlumen-frontend-blog；admin 无 article 引用，审计展示兼容见 §1.4）

| 现 | 改 |
| --- | --- |
| 路由 `router/index.ts` | `/articles/:id`→`/knowledge/:id`；`/studio/articles`、`/studio/articles/new`、`/studio/articles/:id/edit`→`/studio/knowledge`、`/studio/knowledge/new`、`/studio/knowledge/:id/edit`（其余路由不动） |
| `modules/content/api/article.ts`、`pages/ArticleEditorPage.vue`、`pages/ArticleListPage.vue` | `knowledge.ts`、`KnowledgeEditorPage.vue`、`KnowledgeListPage.vue`（组件/类型/文案同步） |
| `modules/publishing/pages/ArticleDetailPage.vue`、`api/public.ts`、`api/release.ts`、`api/review.ts` | `KnowledgeDetailPage.vue`；api 内 URL 常量与 `articleId`→`knowledgeId` |
| `modules/chat/components/ArticleQaDialog.vue`、`api/chat.ts`、`CitationCard.vue`、`pages/ChatPage.vue` | `KnowledgeQaDialog.vue`；`/articles/{id}/ask` URL；引用卡片措辞 |
| `modules/blog/pages/HomePage.vue`、`SearchPage.vue`、`modules/ai/pages/AiWritePage.vue`、`modules/ai-enhance/*`、`modules/engagement/*`、`modules/knowledge/*`、`modules/workbench/pages/WorkbenchPage.vue`、`modules/content/composables/useAutoSave.ts` | 类型名 `Article*`→`Knowledge*`、URL 常量、界面文案「文章/写文章/发布文章/文章管理/文章列表」→「知识/写知识/发布知识/知识管理/知识列表」（**共 26 个含中文文案的文件**逐个替换） |
| `types/` 与 `stores/` | 会话/领域类型中 article 字段改名 |

- **admin 前端**：无 article 文件；审计日志页如存在 `ARTICLE_PUBLISH` 动作文案映射，KB-1 追加 `KNOWLEDGE_PUBLISH` 映射（旧值 `ARTICLE_PUBLISH` 保留展示，不回溯改写历史数据）。

### 1.4 KB-1 兼容与验收

- **审计历史兼容**：`plt_activity_log` 旧 `ARTICLE_PUBLISH`/`ARTICLE` 值保留原样；查询与展示层同时认识新旧值；新写入一律 `KNOWLEDGE_*`。
- **缓存兼容**：KB-1 改名即换键（`xlumen:knowledge:*`），旧键自然过期，不做跨版本读旧键。
- **验收门槛**：
  1. `export JAVA_HOME=<JDK25>` 后 `mvn -T 1C clean verify` BUILD SUCCESS（含改名后单测，如 `KnowledgeQueryDTOTest`）。
  2. `pnpm lint`/`stylelint`/`typecheck`/`test`/`build` 全绿。
  3. 现有 E2E 9 个用例（blog 8 + admin 1）中的路径/文案断言同步后全部通过（跑前清 5173/5174 残留进程）。
  4. grep 验收：后端 Java 无 `Article*` 类名/`/articles` 路径（历史注释除外）；前端无 `article` 路由；SQL 无 `cnt_article`/`article_id` 列定义（迁移脚本内旧名仅存在于 RENAME 语句本身）。
  5. 运行时抽查：知识 CRUD、公开详情、问答、索引状态 4 条链路返回业务码正常（隐式路径绑定仍生效）。

---

## 2. KB-2 数据模型

### 2.1 新表（并入 `20_knowledge.sql`，kb_ 前缀归 knowledge 模块）

```sql
-- 知识库（F-0308）：可见性库级决定；回收站用 status+deleted_at（不扩 8 状态机，已确认）
CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id            BIGINT PRIMARY KEY,
    workspace_id  BIGINT NOT NULL,
    name          VARCHAR(64) NOT NULL,
    intro         VARCHAR(500) NOT NULL DEFAULT '',
    cover         VARCHAR(255) NOT NULL DEFAULT '',
    visibility    TINYINT NOT NULL DEFAULT 0 COMMENT '0 私有 / 1 公开',
    status        TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 / 1 回收站',
    deleted_at    DATETIME NULL,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    UNIQUE KEY uk_kb_ws_name (workspace_id, name),
    KEY idx_kb_ws_vis (workspace_id, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 目录树（F-0309）：parent_id 多级；列表按 name 排序（数据库排序规则，不设拼音列）
CREATE TABLE IF NOT EXISTS kb_directory (
    id         BIGINT PRIMARY KEY,
    kb_id      BIGINT NOT NULL,
    parent_id  BIGINT NOT NULL DEFAULT 0,
    name       VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_dir_kb_parent (kb_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

V2 表（`kb_kb_grant`、`kb_kb_follow`、slug/pinned 列）仅注释占位，**不在本阶段建表**（决策 D5/编号契约）。

### 2.2 `cnt_knowledge` 结构变更（KB-2 起）

- 新增：`kb_id BIGINT NOT NULL`、`directory_id BIGINT NOT NULL DEFAULT 0`（0=根，可空目录）、`recycle_status TINYINT NOT NULL DEFAULT 0`（0 正常/1 回收站）、`deleted_at DATETIME NULL`。
- 删除：`category`、`visibility`。
- 保留：`tags` JSON、`status` 8 状态机、`version`、`published_at`、`view_count`。
- 索引：`idx_knowledge_ws_status` 保留；新增 `idx_knowledge_kb_dir (workspace_id, kb_id, directory_id, status)`；删 `idx_article_ws_category`。

### 2.3 `kb_chunk` / `kb_index_version` 变更

- 新增 `kb_id BIGINT NOT NULL`（KB-1 已改名 `knowledge_id`，本阶段加库维度）；`kb_index_version` 同；注释更新（向量在 Milvus，过滤字段含 kb_id）。

### 2.4 Entity/Mapper/迁移

- 新增 `KbKnowledgeBaseEntity`/`KbKnowledgeBaseMapper`、`KbDirectoryEntity`/`KbDirectoryMapper`（knowledge 模块，类注释按 BACKEND §16.3）。
- 迁移脚本（`85_kb_migration.sql` 追加小节，幂等）：①每空间建「默认公开库」「默认私有库」；②`cnt_knowledge` 结构变更；③存量知识按原 `visibility` 归入对应默认库（`1→公开库`、`0→私有库`）；④原 `category` 值在每个库内平铺为同名一级目录并回填 `directory_id`（无 category 挂根）；⑤回填 `kb_chunk`/`kb_index_version` 的 `kb_id`；⑥校验：每篇知识有且仅有一个库/目录归属、公开库知识数=原公开知识数。

### 2.5 验收

- 开发库执行 init（或 85 迁移）后表结构正确、迁移幂等（重跑无副作用）。
- 单测：Entity 映射、索引命名、迁移校验断言。

---

## 3. KB-3 后端能力（F-0305/F-0307/F-0308/F-0309/F-0407 + 发布选库）

### 3.1 knowledge 模块新增

| 组件 | 内容 |
| --- | --- |
| `controller/KnowledgeBaseController.java` | `GET/POST /api/v1/knowledge-bases`、`PUT/DELETE /api/v1/knowledge-bases/{id}`（删除=二次确认参数+连带进回收站）、`PUT /api/v1/knowledge-bases/{id}/visibility`（公开↔私有，审计+缓存失效+检索范围即时生效） |
| `controller/DirectoryController.java` | `/api/v1/knowledge-bases/{kbId}/directories`：树 CRUD、按名称排序、删除目录时知识上挂父目录（根目录删除时挂库根） |
| `controller/RecycleBinController.java` | `GET /api/v1/recycle-bin?type=kb|knowledge`、`POST /api/v1/recycle-bin/{type}/{id}/restore`、`DELETE /api/v1/recycle-bin/{type}/{id}`（彻底删除+二次确认）；批量操作 V2 预留 |
| `service/KnowledgeBaseService(+Impl)`、`DirectoryService(+Impl)`、`RecycleBinService(+Impl)`、`VisibilityService(+Impl)` | 库 CRUD/回收站/目录树/可见库集合推导（见 3.2）；删除连带取消定时发布任务与作废审核（幂等） |
| `api/KnowledgeApi.java` 扩展 | `listKnowledgeBases`、`getDirectoryTree`、`checkOwnership(kbId, directoryId)`、`resolveVisibleKbIds()`；检索入参增加 `kbIds` |
| `dto/vo` | `CreateKnowledgeBaseDTO`/`UpdateKnowledgeBaseDTO`/`KnowledgeBaseVO`/`DirectoryDTO`/`DirectoryVO`/`RecycleBinItemVO` 等（class+Lombok，继承 `PageQueryDTO` 处用 `@SuperBuilder`） |

### 3.2 可见库集合推导（单一实现，已确认）

- `VisibilityService.resolveVisibleKbIds(user)` = 全平台公开库 ∪ 自己空间私有库（V2 加授权库）；公开读、搜索、RAG 检索、知识列表共用，禁止散落重复过滤。

### 3.3 content 模块改造

- 知识 CRUD 增加 `kbId`/`directoryId` 入参并经 `KnowledgeApi.checkOwnership` 校验（单库单目录）；同库换目录允许、跨库移动不提供；新增「复制到新库」（新知识重新走状态机）。
- 发布流程：发布入参改 `kbId + directoryId`（不再传 visibility）；发布成功事件 `KnowledgePublishedEvent` 携带 `kbId`。
- 知识软删/恢复（`recycle_status`/`deleted_at`，F-0305）。

### 3.4 publishing 模块改造

- 公开读改造（D9 改写）：`PublicKnowledgeController` 按身份聚合——访客=全平台公开库已发布；登录=+自己私有库已发布（🔒 标记）；排序：未选目录 `updated_at DESC`、选中目录 `created_at ASC`（服务端强制）；筛选：kbId/directoryId/tag/关键词（LIKE，MVP）。
- 列表统计批量 IN 防 N+1；卡片含库名 badge 数据。
- 审计：`KNOWLEDGE_PUBLISH`/`KNOWLEDGE_DELETE`/`KB_VISIBILITY_CHANGE`/`KB_RECYCLE_RESTORE`；旧 `ARTICLE_PUBLISH` 值展示兼容。
- 缓存（已确认分片）：键 `xlumen:knowledge:{kbId}:{directoryId}`（+`{ws}` 前缀），发布/下架/可见性变更按维度失效，禁止单键缓存全站流。

### 3.5 RAG 与 AI 检索

- `IndexRequestDTO`/`SearchRequestDTO` 增加 `kbId`；索引落库与 Milvus 过滤字段增加 kb_id（Milvus 未装时 Noop 降级行为不变）。
- `RetrievalService.search` 按「可见库集合 + 知识状态」过滤（F-0407 单一实现）。
- ai 模块问答接口增加检索范围参数（`allVisible`/`kbId`），详情页问答默认当前库。

### 3.6 验收

- 单测覆盖：库级可见性矩阵、越权 404、排序三规则、回收站恢复冲突、删库连带、幂等、可见性切换即时生效。
- 集成测试：接口全链路（建库→建目录→发布→首页可见/私有过滤→检索按库→删库进回收站→恢复→彻底删除）。
- 越权专项：他人私有库/私有知识 404，公开流无私有内容。

---

## 4. KB-4 前端页面（blog 应用）

| 页面 | 路由 | 要点 |
| --- | --- | --- |
| 导航头（layouts） | 全局 | 知识/知识库/AI小光 + 搜索（联想分组）+「＋写知识」+ 头像菜单（创作中心/我的知识库/回收站/个人设置/退出）；当前项洋红高亮；移动端汉堡 |
| B01 首页知识流 | `/knowledge`（首页重定向或直接作为 `/`） | 左栏导航（三态：全部知识库=公开库列表+我的知识库；选中库=目录树+标签云）+ 右栏知识列表（库 badge、🔒 私有标记、更新倒序/选目录后创建正序、三种空态、骨架、分页）；API `GET /api/v1/knowledge?kbId=&directoryId=&tag=&pageNo=&pageSize=` |
| B20 库页 | `/kb/:id` | 库头部（简介/可见性徽标/统计）+ 目录树（名称排序）+ 库内列表（进入目录创建正序，置顶 V2） |
| B21 发现页 | `/knowledge-bases` | 公开库卡片墙（默认最近更新倒序）+ 关注按钮 V2 + 我的知识库入口 |
| B22 知识库管理 | `/studio/knowledge-bases` | 卡片墙 + 新建/编辑/可见性切换/删除（连带确认）+ 目录管理（树 CRUD） |
| B16 回收站 | `/studio/recycle-bin` | 库/知识双 Tab、恢复/彻底删除（红色二次确认）、30 天提示、批量 V2 |
| B10 知识管理/编辑器 | `/studio/knowledge*` | 列表（库/目录/状态筛选、更新时间倒序、复制到新库、移入回收站）；发布弹窗改「选库+选目录」 |
| B00 对话页 / D02 | `/studio/chat` | 检索范围选择器（全部可见库/仅当前库） |
| B13 发布 / B12 审核 | 现有路由 | 展示目标库与目录、提示文案随库类型变化 |

- API 层与后端 OpenAPI 一致（决策 D4）；新增模块文件按 FRONTEND §5 目录边界（`index.ts` 出口）。
- 视觉：纸刊编辑部方向（Swiss 浅色 + 洋红），Element Plus + Design Token。
- E2E 新增用例：建库→建目录→写知识→发布→首页可见/访客不可见私有→删库进回收站→恢复（浏览器实测截图存档 `.browser-check/`）。

---

## 5. KB-5 存量迁移

1. 执行 `85_kb_migration.sql`（开发库 `xlumen_dev`；测试库 `xlumen_test` 同脚本）。
2. 数据校验：迁移前后知识总数一致；公开库知识数=原公开知识数；无 kb_id/directory_id 缺失知识；category 目录去重正确。
3. 种子/演示数据调整（公开 3 + 草稿 1 + 私有 1）归入默认库，不进 init 脚本。
4. 缓存清空（旧键族 `xlumen:article:*` 全删）。

---

## 6. KB-6 全量验收

1. PRODUCT §12 完成定义逐条核对（含「文章」措辞清零 grep）。
2. `mvn -T 1C clean verify` + `pnpm` 全门禁 + 双前端 E2E 全量（blog + admin）。
3. 浏览器实测：B01/B20/B21/B22/B16/B10/B13/B00 截图存档；私有库越权深链接验证 404。
4. 文档一致性核验（PRODUCT/PROTOTYPE/BACKEND/FRONTEND 与代码实际一致，决策 D7）。
5. CHANGELOG/STATUS 收尾更新；`tmp/` 评审稿标注完成状态。

---

## 7. 风险与注意

1. **KB-1 波及面大**：E2E 断言、审计常量、缓存键、事件类型全联动——严格零行为变更，靠全门禁+E2E 回归兜底；发现行为差异立即回退该提交。
2. **Milvus 未装**：按库过滤先落元数据与查询构造，Noop 降级行为不变，Milvus 就绪自动生效。
3. **跨空间公开读性能**：公开流聚合按 `(kb_id, status, updated_at)` 走索引 + 缓存分片；深分页用游标（BACKEND §18）。
4. **审计历史**：旧 `ARTICLE_PUBLISH` 记录保留原值展示，不回溯改写。
5. **并行协作**：STATUS §5 认领机制，一次一项，避免与 Qoder 等其他 AI 冲突。
