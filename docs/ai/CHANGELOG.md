# xLumen AI 变更日志

> 更新日期：2026/8/19
> **本仓库专属**。
> 按时间倒序记录（最新在顶部），每次 AI 会话结束必须追加一条；代码与文档更新同一提交，禁止虚构进度。

条目模板（追加新条目时复制以下骨架，置于本说明之下、所有旧条目之上）：

```
## yyyy/M/d HH:mm · 工具名（一句话主题）

> 影响文档：受影响的文档相对路径 · 决策摘要：相关决策编号（D1~D17，见 STATUS.md 第 8 节），无则写"无"

变更内容正文（模块/文件/接口级别的主要变更，自由分点书写，不再放入表格单元格）。时间精确到分钟（yyyy/M/d HH:mm）。
```

## 2026/8/19 11:50 · ZCode（2026-08-19 全功能测试结论）

> 影响文档：docs/ai/BUGS.md、docs/ai/assets/browser-test-2026-08-19/{api-smoke,e2e-baseline,bug-007-repro,docs-audit}.md · 决策摘要：D9、D13、D16、D17

按用户 `/goal 做一轮全功能测试` 执行 xLumen MVP 全功能测试，遵守 QA.md 三条铁律（不替代质量门禁 / 缺陷不自动修 / 环境假缺陷先排除）。**范围**：PRODUCT §5（87 项/MVP 44）+ STATUS §3 能力基线 = 12 模块（身份/阅读/互动/内容/KB 体系/审核/AI 对话/AI 写作/AI 增强/RAG/Admin/多用户可见性），分 4 路并行——主代理 browser-use 实测 6 模块（互动/内容/KB 体系/AI 对话/AI 写作/Admin/多用户可见性），3 子代理分别承担 API 冒烟 70 端点、Playwright E2E 10/10 回放、BUG-007 根因复现+文档一致性审计。**测试账号**：`qa_alpha_20260819`（qa_ 前缀独立空间，零污染真实数据，账号已自动获 OWNER 角色可测 admin）。**结论**：
- **互动/内容/KB 体系/AI 对话/Admin 模块全部通过**——F-0212 赞踩互斥、收藏 toggle、B23 收藏页；F-0213 评论反应；F-0214 创作中心导航；F-0312 目录树右键菜单（新增子目录/重命名/删除）；F-0808 AI 摘要；D9 跨用户可见性；D17 文章→知识；审核中心审批 + AI 审校回填；Admin 空间设置/模型配置/审计日志（3 页可见，审计记录含 KNOWLEDGE_PUBLISH/REVIEW_REJECT）。
- **新发现 10 个 BUG 候选**（BUG-007 根因修正 + BUG-008~010、012~017，跳号 011 留给 createdAt 同源增强），全部按 BUGS.md 模板登记，**仅记录未修**——
  - BUG-007 修正根因：`ContentApiImpl` 公开读路径强制 `eq(status,6)` 把「APPROVED + published_at=NULL」中间态挡掉，与原推测「可见库推导缺陷」不符（subagent 复现 SQL + 行号 + 修复候选三方向已交付 `bug-007-repro.md`）
  - BUG-008 HomePage 左栏「公开知识库」computed 取数错（仅显示「我的公开库」），与 BUG-007 症状重叠但根因不同
  - BUG-009 详情页已登录态仍显示「登录后可点赞、收藏与评论」提示
  - BUG-010 评论/AI 增值结果 `createdAt: null`（前端现象「20684 天前」的真根因）——subagent API 冒烟定位
  - BUG-012 读者纠错同 IP 限流失效（QA §3.7 M11 契约违反）
  - BUG-013 知识 update 接受越界 kbId/directoryId 静默写入（create 路径已加 checkOwnership，update 路径遗漏）
  - BUG-014 知识版本历史端点缺失（`cnt_knowledge_version` 表已建无 controller）
  - BUG-015 提交审核后作者侧 getOwned 偶发 404（SUSPECT 待复核）
  - BUG-016 下架（unpublish）端点完全缺失（`KnowledgeStatus.OFFLINE(8)` 状态无迁移接口）
  - BUG-017 编辑态提示「归属库与目录不可修改」但目录可改（文案 vs 行为错位）
- **文档一致性审计**（docs-audit.md）：7/10 一致，4 项差异——
  - D1（中）README.md:19 / GLOBAL.md:14 引用 PRODUCT 旧值「82 项 / MVP 39」，现行 PRODUCT §5「87 项 / MVP 44」
  - D2-D3（低）STATUS.md:101 §6 W7 行「73 项 / MVP 37」与 PROTOTYPE 范围「B00~B19、A01~A07」已过时
  - D4（低）README.md:17 文本「11 份」与下方 10 项链接清单未对齐（11=10 docs+1 README 自身）
- **遗留运维事项**：①测试期间未重启后端（QA 铁律），本批次 BUG 中仅 BUG-010 时戳类建议先修；②新账号 `qa_alpha_20260819` 无默认知识库，需 API 建库才能进首页 KB 切换；③qa_ 账号 8-19 22:00 自动清理（QA §3.8）；④dev server 端口残留 PIDs 44080/34312 已清，仅 5173/5174 主实例；⑤BUG-007 修复候选三方向待用户裁决。
- **顺带澄清**：BUG-007 与 BUG-008 同症状异根；BUG-010「20684 天前」= 评论/AI 增值 `createdAt` 后端时戳不回填（不是前端格式化 bug）；subagent 报告「BUG-008~021」中 008/014/016~018/020/021 共 7 条是任务清单的端点路径假设过时（非代码缺陷）已剔除，仅真实后端缺陷并入本批次 BUGS。

> 影响文档：ai/QA.md（新增）、global/GLOBAL.md、README.md、ai/STATUS.md、ai/CHANGELOG.md · 决策摘要：无

按用户要求建立 QA 测试文档，规范「用户发起、AI 代理用会话内置 browser-use 能力操作真实浏览器」的测试工作流。**新增 `docs/ai/QA.md`**：①定位与分工（单元/集成、Playwright E2E、AI 浏览器测试三层手段表；不替代质量门禁、缺陷不自动修复、环境假缺陷先排除三条铁律）；②三种发起模式（全功能巡检按 PRODUCT §5+STATUS §3 推导 / 指定模块或 F-xxxx、Bxx 页面 / 缺陷复现 BUG-xxxx）；③环境自检 8 项（后端健康检查、双前端可达、Vite 端口自增与残留 dev server 陷阱、Redis 无密码、Noop 向量降级的假缺陷判定、AI 真实调用注意、`qa_` 测试账号与数据安全（注册即 OWNER 可测 admin，不动真实账号数据）、先读 BUGS 已知问题）；④browser-use 操作规范踩坑备忘（登录态会话内存态/整页刷新重登、fullPage 截图平铺伪影、点击超时降级 Playwright、SSE 流式等待放宽、雪花 ID 完整复制）；⑤12 模块入口速查表（blog/admin 路由 + 核心链路骨架，验收基准统一引用 PRODUCT §12）；⑥结果流转（缺陷记 BUGS、会话结论记 CHANGELOG、修复时沉淀 E2E 回归用例、测试数据 `qa_` 前缀约定）。**导航同步**：GLOBAL §2 导航表新增 QA 行、§4 结构树补 `ai/QA.md` 并计数 10->11 份、标注说明补 IDEAS/QA 新增日期；README 文档清单补 IDEAS（2026/8/18 新增时的遗漏）与 QA 两行、计数 9->11 份；STATUS §1 阅读清单补 QA、§7 追加本条目并维持最近 3 条。纯文档变更，代码与质量门禁零影响。

## 2026/8/18 20:47 · ZCode（IDEAS 批次立项实施 + BUG-006 修复）

> 影响文档：ai/IDEAS.md、ai/BUGS.md、ai/STATUS.md、product/PRODUCT.md、frontend/PROTOTYPE.md · 决策摘要：D9、D16、D17

按用户要求执行 IDEAS.md 全部 5 条想法（登记 F-0212/F-0213/F-0214/F-0312/F-0808，PROTOTYPE 新增 B23 收藏页）并修复 BUG-006。**BUG-006 知识详情页排版错乱**--根因：`extractToc` 仅提取 h2~h4，短文无二级以上标题时目录栏不渲染，但 `.detail__layout` 的 grid 仍为 `200px minmax(0,760px)` 两栏定义，正文被自动布局塞进 200px 的目录列（正文每行 3~5 字、操作区按钮竖排、「登录后可点赞与评论」竖排、评论区飘出卡片、标题因正文首个 H1 重复渲染）；修复 = TOC 为空时加 `detail__layout--single` 退回单栏 `minmax(0,760px)` + `stripLeadingTitle` 去掉与页头标题重复的正文首个 `# 标题`。验证：1280 视口下正文卡片 760px 精确居中（x=260）、标题唯一、视觉复核五项全通过。**F-0212 知识点赞/点踩/收藏**--`eng_like` 加 `reaction_type` 列升级为三态互斥反应（无→激活/同型取消/异型切换），`eng_like` 存量行默认 1=赞语义不变；新表 `eng_favorite`（uk_favorite_ws_knowledge_user）；LikeController 拆 `/like` `/dislike`（返回 `{reaction:LIKE|DISLIKE|NONE}`）+ 收藏 toggle + `GET /api/v1/public/favorites` 收藏分页（复用公开卡片 VO + favoritedAt，按可见性过滤）；KnowledgeDetailVO 增 dislikeCount/favoriteCount/favorited。前端 ReactionBar（赞踩互斥）+ FavoriteButton + B23 收藏页 `/favorites`（取消收藏即时移除）+ 头像下拉「我的收藏」。**F-0213 评论点赞/点踩**--新表 `eng_comment_reaction`；`POST /api/v1/public/comments/{commentId}/like|dislike` 三态互斥（评论不存在/已删 404）；CommentVO 增 likeCount/dislikeCount/myReaction（listComments 批量聚合防 N+1）；CommentList 每条评论底部 👍/👎 互斥。**F-0214 创作中心一级导航**--App.vue 主导航「知识库」与「AI小光」间新增「创作中心」（登录态显示，路由 workbench），移动端汉堡菜单同步。**F-0312 目录树右键菜单**--新增共用组件 `DirectoryTreeContextMenu.vue`（Teleport 固定定位菜单，视口钳位，mousedown/Escape/scroll 关闭）接入 B01 首页与 B20 库页：树根右键新增根目录、节点右键 新增子目录/重命名/删除（ElMessageBox 二次确认，连带规则文案=子树删除+知识上挂父目录，仅库主）；B20 走 el-tree @node-contextmenu、B01 走按钮 @contextmenu（库切换器数据源为鉴权接口等价库主）。**F-0808 知识详情 AI 摘要**--`EnhanceServiceImpl` 抽出 `generateAndStoreSummary()`（enhance() 复用）；ai 模块新增 `KnowledgePublishedSummaryListener`（@EventListener 接发布事件 → aiTaskExecutor 异步生成，失败仅 warn 降级）；`AiApi.findLatestSummary`（scene=SUMMARY 取最新）→ KnowledgeDetailVO.aiSummary → 详情页 header 与正文间「AI 摘要」区块（公开读者可见）。**顺带修复契约缺陷**--`DirectoryController.PUT /directories/{id}` 原返回 `data:null` 而前端 `updateDirectory` 按 POST 契约 `mapDirectory(unwrap(null))` 抛 TypeError 致右键重命名后树不刷新（DB 已改名、UI 不刷新）；修复 = 后端 update 返回更新后 DirectoryVO（含 knowledgeCount）。**DB 迁移**--`sql/migration/87_reaction_upgrade.sql`（幂等：信息架构校验加列 + IF NOT EXISTS 建表）已在 xlumen_dev 执行并校验（列/两表就位、存量行 reaction_type 全 1）。**验证**：`mvn -pl xlumen-publishing,xlumen-ai -am clean verify` BUILD SUCCESS（38 测试全过）+ 全量 `mvn clean package` 通过；前端 typecheck/lint/stylelint/test 双应用全绿；新增 `e2e/enhancements.spec.ts`（注册→创作中心→收藏页→赞踩互斥→收藏/取消→评论点赞→右键菜单增删改全链路）与既有 9 条 E2E 全部通过；后端已重启运行新代码，详情/收藏/反应接口 curl 冒烟通过（未登录 401 正确）。

## 2026/8/18 · ZCode（新建功能想法池 IDEAS.md）

> 影响文档：ai/IDEAS.md（新增）、global/GLOBAL.md、ai/CHANGELOG.md · 决策摘要：无

- 新增 `docs/ai/IDEAS.md` 功能想法池：记录用户尚未评估的新功能想法，编号 `IDEA-001` 起顺延；状态流转为 待评估 -> 已采纳（转入 STATUS.md 第 5 节待办 + PRODUCT.md 第 5 节功能总表登记 F-xxxx）/ 已否决；采纳前不得开工。
- GLOBAL.md 第 2 节文档导航表新增「功能想法池」行、第 4 节目录树补 `ai/IDEAS.md` 条目、文档体系数量 9 -> 10 份。

## 2026/8/17 17:50 · ZCode（design/ 方案随实施完成删除）

> 影响文档：STATUS、GLOBAL、README · 决策摘要：无

按用户约定「设计方案随实施完成即移除」：git rm 删除 docs/design/ 两份方案（knowledge-redesign-proposal.md / code-implementation-plan.md，对应 KB-1~KB-6 已全部交付验收）；STATUS §2/§5/§7、GLOBAL §4 结构树与标注说明、README 文档清单中的引用同步清理（git 历史可回溯方案全文）。

## 2026/8/17 17:30 · ZCode（文档体系治理：评审问题六项统一修复）

> 影响文档：README、GLOBAL、BACKEND、STATUS、CHANGELOG、docs/design/* · 决策摘要：无

解决文档评审发现的六项问题（摘要另见 STATUS §7 同时间条目）：①README「已实现」刷新（原文误标 M02~M13 待办，与实际进度矛盾）、补 BUGS/design 导航、去除快速开始 6.x 编号残留；②STATUS §3 压缩为能力基线摘要 + 踩坑备忘（241->132 行，历史细节收敛到本文件单处维护）、§2/§4/§5 状态修正（KB-6 补记已完成）、§7 精简为最近 3 条并补 8/17 16:30 遗漏条目；③GLOBAL §2 导航表补 BUGS.md、§4 结构树同步（docs 9 份 + design/）；④tmp/ 两份方案（knowledge-redesign-proposal / code-implementation-plan）git mv 迁入 docs/design/，STATUS 与方案内部引用同步（本文件历史条目按记录原貌保留 tmp/ 字样）；⑤本文件条目格式由单行大表格单元格改版为「标题 + 元信息行 + 正文」，存量条目全量机械转换（内容未改动）；⑥BACKEND §10 补索引补跑端点（POST /api/v1/knowledge/{knowledgeId}/reindex，8/17 BUG-004 修复引入、此前未回写文档）。纯文档变更，代码与质量门禁零影响。

## 2026/8/17 16:30 · ZCode（小光回答 Markdown 渲染）

> 影响文档：CHANGELOG · 决策摘要：无

小光 AI 助理回答改用 Markdown 渲染（人设约定「对话输出始终用 Markdown」，此前聊天气泡为纯文本插值展示）：`ChatPage.vue`（会话页）与 `KnowledgeQaDialog.vue`（知识级问答弹窗）的助手消息改 `v-html` 渲染 `renderMarkdown()`（复用 publishing 模块 markdown-it + DOMPurify 清洗工具，与 AiWritePage/知识详情同一通道），用户消息保持纯文本插值防 XSS；流式打字光标移出文本节点为兄弟元素；新增气泡内 `markdown-body` scoped `:deep()` 样式（段落/标题/列表/代码块/行内代码/引用/表格/链接，与设计 token 对齐），Markdown 消息体取消 `pre-wrap`。验证：`pnpm lint`（0 errors）+ `pnpm typecheck` 双前端全绿

## 2026/8/17 15:10 · ZCode（BUG-002~005 统一修复）

> 影响文档：BUGS、STATUS · 决策摘要：D13、D16

用户要求统一修复 BUGS.md 全部待修项（BUG-002/003/004/005）：**BUG-002 前端 chat 流式整段渲染**--根因 = `ChatPage.vue`/`KnowledgeQaDialog.vue` 的 `onChunk` 回调直接修改 push 前的裸对象引用（`assistant.content += text`），绕过 Vue 3 Proxy 响应式，流式期间零重渲染、`sending=false` 时一次性渲染全量文本；修复 = 占位消息改 `reactive()` 代理后再入列（两处组件同修，chat.ts/sse.ts/后端 SSE 复核均正常）。**BUG-003 审核中心 AI 审校问题恒 0**--根因 = `pub_review.ai_result_json` 仅 `approve()` 回填，PENDING 恒 NULL；修复 = `ReviewServiceImpl` 新增懒回填 `backfillAiResult()`（读取/驳回时若结果为空且任务 COMPLETED 则拉取 `ai_task.result_json` 持久化，幂等、失败不阻断），`getReview()`/`reject()` 挂接，符合「AI 不反向依赖调用方、调用方轮询」既有架构（前端零改动）。**BUG-004 RAG 检索恒空（Milvus 探测缺陷 + 存量补跑）**--①探测改打 `POST {host}:19530/v2/vectordb/collections/has`（与 MilvusVectorStore 数据面同协议，实测 200+code:0；原 `/healthz` 在 9091 打 19530 恒 404 永远降级 Noop）；②`MilvusProperties` 绑定修复：`.env` 经 `spring.config.import` 导入的 `XLUMEN_MILVUS_*` 为大写字面属性，Boot Binder 不做 relaxed binding，字段从未取到 .env 值（一直用写死默认 IP）；改 `@Value("${XLUMEN_MILVUS_HOST:159.75.6.183}")` 显式占位符绑定（对齐 AiProperties/KnowledgeAiProperties 既有模式）；②索引流水线重构：步骤 4-9 提取 `writeIndex()`，新增 `reindex()` 强制重建通道（先失效旧切片/版本绕过 `alreadyIndexed` hash 幂等命中）；③`KnowledgeApi` 新增 `reindexKnowledge`/`getIndexStatus` 跨模块通道，publishing 新增 `IndexBackfillService` + `POST /api/v1/knowledge/{knowledgeId}/reindex` 补跑端点（仅已发布知识，正文经 ContentApi 获取，落 publishing 因 knowledge 模块依赖方向受限无法自取正文）。**BUG-005 提交审核后不跳转**--`KnowledgeEditorPage.handleSubmitReview` 成功分支补 `ElMessage.success` + `router.push({name:'knowledge-list'})`，失败分支改 `ElMessage.error`（与列表页一致）。**验证**：`mvn -pl xlumen-knowledge,xlumen-publishing -am clean verify` BUILD SUCCESS（JDK25）、前端 typecheck/lint（0 errors）/test 全绿、Milvus 探测端点实测可达。**遗留运维事项**（记 BUGS.md 清单备注）：后端需重启使探测修复生效；存量 3 版本索引需逐篇调用 reindex 端点补跑向量

## 2026/8/16 16:30 · ZCode（全功能测试缺陷统一修复）

> 影响文档：STATUS、BACKEND、FRONTEND · 决策摘要：D16、D9、D13

统一修复 2026-08-16 自动化测试发现的缺陷（BUG-3~BUG-11 及观察项，详见 STATUS §7）：**后端**——①content 模块 pom 补 `xlumen-knowledge` 依赖（BACKEND §4 依赖 DAG 约定 content→knowledge 落地，KB-2 遗漏）；②`KnowledgeServiceImpl` 创建/自动保存经 `KnowledgeApi.checkOwnership` 校验 kbId/目录归属（拦截无归属与跨空间孤儿，防 kb_id=0 脏数据）＋知识列表查询补 `recycle_status=0` 过滤（软删不再残留列表）；③publishing 提交审核/发布入口补归属兜底校验（历史孤儿无法再走审核发布）；④知识数统计闭环：knowledge 模块定义 `KnowledgeCountApi`（反向 SPI），content 模块实现按 kb_id/directory_id 聚合非回收站计数，库卡片/目录树 knowledgeCount 不再恒 0；⑤`GlobalExceptionHandler` 新增 405/类型不匹配映射（原落 500）、JSON 解析失败透出首个根因；⑥`PublicKnowledgeServiceImpl.getKnowledge` 点赞状态重算改用 `WorkspaceContext.workspaceId()`（原传 null 被 MyBatis-Plus 转 `IS NULL` 条件导致 liked 恒 false，BUG-8 根因）。**前端**——⑦`KnowledgeEditorPage` 重做：删分类/文章级可见性（D16），新增所属知识库/目录选择器（编辑态禁改库，单库单目录）、未选库保存拦截、草稿态「提交审核」按钮；⑧`AiWritePage` 保存前选库；⑨知识管理列表删可见性筛选、草稿行新增「提交审核」；⑩双前端 http 拦截器透出后端业务 message（不再显示裸 "Request failed with status code xxx"）；⑪双前端 session store 持久化快照+accessToken 到 localStorage（Refresh Token 不持久化，符合 FRONTEND §7 白名单），整页刷新不再登出；⑫`LikeButton` 状态以服务端返回为准、切换知识才重置本地状态。**数据**——⑬新增 `sql/migration/86_orphan_cleanup.sql` 幂等清理无归属孤儿知识（含审核/发布/索引关联）并已在 xlumen_dev 执行（清理 4 条 kb_id=0 孤儿），测试产生的评论/点赞一并清除。**验证**：后端 `mvn verify` BUILD SUCCESS（单测全绿）、前端 typecheck/lint/test 全绿、API 全链路复核（创建/自动保存/越权拦截 400、孤儿提交审核 400、库计数、detail liked 登录 true/访客 false、405 映射）、浏览器回归（登录持久化/编辑器选库保存/提交审核入审核中心/删除列表过滤/点赞 3 次状态一致）

## 2026/8/16 14:45 · ZCode（初始化待修问题清单 BUGS）

> 影响文档：STATUS、BUGS（新增） · 决策摘要：无

新增 docs/ai/BUGS.md 待修问题清单（编号约定 BUG-001 起顺延、字段模板：日期/模块/状态/复现步骤/现象 vs 期望/补充；**修复仅按用户明确要求进行，AI 不自动认领**，修复后从清单移除并回写 CHANGELOG）；STATUS.md §1 工作流规则 1 增加「通读 BUGS.md」与不自动认领约束

## 2026/8/14 18:55 · ZCode（KB-5 迁移收尾 + KB-6 全量验收）

> 影响文档：STATUS、BACKEND · 决策摘要：D5、D16、D17

KB-5 存量迁移收尾：开发库 xlumen_dev 迁移数据校验全绿（知识总数 10、0 无归属、公开库 8 篇、category 平铺目录 5 个「后端/AI/前端/随笔/测试」、回收站 0、kb_chunk/kb_index_version kb_id 全部回填）；Redis 缓存清空（xlumen:* 旧键族全删，refresh token 自然重建）；种子数据（公开 3+草稿 1+私有 1）已随迁移归入默认公开库/默认私有库，不进 init 脚本（决策 D5）。KB-6 全量验收通过：PRODUCT §12 完成定义 8 条逐条核对（界面流程/后端权限/接口一致/边界处理/测试构建/文档同步/总表同步/行为变更）；后端 mvn -T 1C clean verify BUILD SUCCESS（10 测试全绿）；前端 typecheck/lint/stylelint/test/build 全绿；双前端 E2E 9/9（blog 8 + admin 1）；全仓「文章」措辞清零（仅 ai 模块「完整文章」写作素材语义与历史说明保留，合法）；文档一致性核验通过并补充 BACKEND §10 回收站聚合层说明（publishing 承载，kb 侧 KnowledgeApi + knowledge 侧 ContentApi，恢复冲突判定）；知识平台化重构 KB-1~KB-6 全部交付完成

## 2026/8/14 18:50 · ZCode（KB-4 前端页面交付）

> 影响文档：STATUS · 决策摘要：D16、D9

KB-4 前端页面交付（F-0208/F-0305/F-0308/F-0309 + B01/B13/B16/B20/B21/B22/B00 改造）：①导航头（App.vue）——品牌 xLumen、主导航 知识/知识库/AI小光（移除分类/标签/关于，D16）、搜索、登录态「＋写知识」洋红 CTA + 头像下拉（我的知识库/创作中心/回收站/退出登录）、移动端汉堡；②B01 首页知识流（HomePage.vue）——左栏库导航（登录态：库切换器「全部知识库+我的库」→选中库切换目录树+标签云；未登录：公开读说明卡）+ 右栏知识列表（库 badge 跳 /kb/:id、🔒 私有标记、范围标题「全部知识库/[库名]/[目录名]」、排序说明、骨架/三态空态/分页）；③B20 库页（KnowledgeBaseDetailPage）——库头部（名/可见性徽标/简介/知识·目录数）+ 库主编辑/新建目录 + 左目录树右列表（目录筛选）；④B21 发现页（KnowledgeBasesPage）——我的知识库卡片墙（封面占位/🔓🔒/简介/知识数/编辑/删除），公开库聚合 V2 说明；⑤B22 库管理（KnowledgeBasesManagePage）——卡片墙 + 新建/编辑/可见性 switch/删除二次确认「库内 N 篇知识将一并移入回收站」+ 展开目录管理（新建/改名/删除上挂父目录）；⑥B16 回收站（RecycleBinPage）——全部/知识库/知识三 Tab + 30 天提示 + 剩余天数 + 恢复（409 文案透出）/彻底删除红色二次确认；⑦B13 发布弹窗（ReleasePage）——删可见性选择，显示目标库/目录（kbId 反查名称），未归属禁用发布，发布记录状态对齐后端枚举；⑧B00 对话页+KnowledgeQaDialog——检索范围选择器（全部可见库/指定库，ChatRequestDTO kbId/allVisible）；⑨B03 搜索页——删分类改知识库+目录筛选（登录态），卡片 kbName 徽标，移除 fetchCategories；⑩新增 knowledgeBase.ts API（库/目录/回收站全量）；验证：pnpm typecheck/lint/stylelint/test/build 全绿、E2E 8+1 全过（断言同步新 UI：标题「全部知识库」/头像菜单登出/标签筛选）、浏览器实测 7 页截图存档 .browser-check/kb4-*.png（B01 访客+登录/B20/B21/B22/B16/B03）

## 2026/8/14 18:30 · ZCode（KB-3 后端能力交付）

> 影响文档：STATUS · 决策摘要：D9 改写、D13、D16

KB-3 后端能力交付（F-0305/F-0307/F-0308/F-0309/F-0407）：①knowledge 模块新增——知识库 CRUD+可见性切换（KnowledgeBaseController，删库二次确认 CONFIRM 连带回收站）、目录树 CRUD（DirectoryController，按名称排序、删除时知识上挂父目录、子树收集）、回收站聚合（publishing RecycleBinController 统一编排：kb 侧委托 KnowledgeApi、knowledge 侧委托 ContentApi，知识恢复含冲突判定「原目录已删→挂库根/原库已删→409」）、可见库集合推导单一实现（VisibilityService.resolveVisibleKbIds：访客=全平台公开库、登录=+自己空间私有库，WorkspaceApi 新增 getWorkspaceIdByOwner 按 owner_user_id 查空间，修复原实现用默认空间导致登录用户越权看到博主私有库的严重漏洞）；②content 模块——知识 CRUD 增加 kbId/directoryId（单库单目录，决策 D16）、发布流程改库+目录（KnowledgePublishDTO 删 visibility）、软删/恢复（recycle_status+deleted_at 独立软删列）、公开读按可见库集合过滤（跨空间聚合，workspaceId 可空=全平台）、listCategories 删除（category 废弃）、标签聚合跨空间；③publishing——公开读按身份聚合跨空间（多用户 D9 改写：listPublished/getPublished/互动统计/标签聚合均支持 workspaceId=null）、排序规则（未选目录 updated_at DESC、选中目录 created_at ASC）、缓存分片 xlumen:knowledge:detail:{id}（跨空间共享，登录态直查回源防串读）、审计 KB_VISIBILITY_CHANGE、CreateReleaseDTO 删 visibility（发布记录可见性快照取知识库）；④RAG/AI——IndexRequestDTO/SearchRequestDTO 增加 kbId/kbIds（删 visibilityScope）、VectorStore.search 按 kb_id IN 过滤（Milvus filter 改 kb_id in [...]，Noop 降级不变）、IndexPipeline 落库 kb_id、AI 问答 ChatRequestDTO 增加 kbId/allVisible 检索范围参数；⑤跨模块事件联动——knowledge 发布 KbRecycleStatusEvent/KbDirectoryDeletedEvent/KbPurgedEvent（content KnowledgeBaseLinkEventListener 监听：连带软删/恢复/目录上挂/物理级联删）与 KbVisibilityChangedEvent（publishing 监听按库失效缓存）；⑥验证：mvn verify BUILD SUCCESS（10 测试全绿）、接口全链路实测（建库→建目录→写知识→审核→发布→访客/库主/他人三身份公开读矩阵→私有 404 越权 404→删库连带回收站→整体恢复→目录删除知识上挂→彻底删除二次确认→可见性切换即时生效→发布即索引 ACTIVE 且 kb_id 正确落库）、修复索引状态路径重复（/api/v1/knowledge/{id}/index-status）、confirm 参数缺省 409 语义

## 2026/8/14 17:40 · ZCode（KB-1 概念改名交付）

> 影响文档：STATUS、BACKEND（§7 编号契约修正 85_platform.sql + sql/migration 说明） · 决策摘要：D17、D5

KB-1「文章→知识」纯改名交付（零行为变更）：①后端 81 个 Java 文件——27 个重命名（ArticleController→KnowledgeController、ArticlePublishedEvent→KnowledgePublishedEvent（字段 articleId→knowledgeId）、PublicArticleController→PublicKnowledgeController、HotArticleCacheService→HotKnowledgeCacheServiceImpl（键 xlumen:article:%d:%d→xlumen:knowledge:%d:%d、失效模式 xlumen:article:*→xlumen:knowledge:*）、ArticleEntity/Mapper/Status/Service/DTO/VO 全量 Knowledge*、ArticlePublishedEventListener→KnowledgePublishedEventListener 等），接口路径全量切换（/api/v1/articles→/api/v1/knowledge、/api/v1/public/articles→/api/v1/public/knowledge、/chat/articles/{id}/ask→/chat/knowledge/{id}/ask、/articles/{id}/index-status→/knowledge/{id}/index-status，不保留旧路径），审计常量 ARTICLE_PUBLISH→KNOWLEDGE_PUBLISH、targetType ARTICLE→KNOWLEDGE，AI 提示文案「未检索到任何相关文章证据」→「…相关知识证据」；②前端 blog 38 个文件——路由 /articles/:id→/knowledge/:id、/studio/articles*→/studio/knowledge*，组件 ArticleListPage/ArticleEditorPage/ArticleDetailPage/ArticleQaDialog 改名，API URL 常量、类型 Article*→Knowledge*、26 处界面文案「文章」→「知识」、E2E 3 个 spec 断言同步；③SQL 双轨——init 6 脚本物理改名（40_content.sql cnt_article→cnt_knowledge + 索引 idx_article_*→idx_knowledge_*、20_knowledge.sql kb_chunk/kb_index_version article_id→knowledge_id + 索引、50_publishing.sql article_id/article_title→knowledge_id/knowledge_title + uk_release_ws_knowledge_version、60_engagement.sql 三表 article_id→knowledge_id + 唯一键/索引、80_ai_enhance.sql、85_platform.sql/70_chat.sql 注释）、新建幂等迁移脚本 sql/migration/85_kb_migration.sql（information_schema 前置检查 + 存储过程，可重跑；开发库 xlumen_dev 已执行）；④验证全绿：mvn -T 1C clean verify BUILD SUCCESS（8/8 模块）、前端 typecheck/lint/stylelint/test/build 全过、E2E 9/9（blog 8 + admin 1）、grep 清零（后端 Article* 类名/路径/cnt_article/ARTICLE_PUBLISH/xlumen:article 全 0；前端「文章」与 /articles 路径全 0，仅剩 HTML5 `<article>` 语义标签）、运行时 4 链路抽查通过（公开列表/详情、登录 CRUD、SSE 问答、索引状态）；⑤环境处理：本地 Redis 需以无密码实例启动（与 .env XLUMEN_REDIS_PASSWORD 空一致，redis.conf 密码为历史手动配置）；发现开发库表/列注释历史乱码（iam_* 全表 + eng_* 部分列，8/14 事故遗留、本次迁移未触碰，已记录待修）

## 2026/8/14 17:04 · ZCode（生成完整代码实现方案）

> 影响文档：STATUS · 决策摘要：D7/D17

生成 `tmp/code-implementation-plan.md`（v1.0，后续代码修改的执行依据）：基于实际代码扫描（后端 46 文件/前端 27 文件/6 个 SQL 脚本）输出 KB-1 改名 file-level 清单（common/content/publishing/knowledge/ai/identity/boot 逐文件表 + 接口路径总表 + SQL 双轨：init 脚本更新 + 85_kb_migration.sql 迁移）、KB-2 数据模型（kb_knowledge_base/kb_directory 建表 DDL 并入 20_knowledge.sql、cnt_knowledge 结构变更含 recycle_status/deleted_at、kb_chunk/kb_index_version +kb_id、V2 表仅占位不建）、KB-3 后端组件清单（库/目录/回收站/可见性 Controller+Service、可见库集合推导单一实现、内容/公开读/审计/缓存分片/RAG 改造）、KB-4 前端页面路由表（B01/B20/B21/B22/B16/导航头/发布弹窗）、KB-5 迁移、KB-6 验收、风险清单；STATUS §5 已挂接该方案为 KB-1~KB-6 实施细则

## 2026/8/14 16:59 · ZCode（知识平台化重构：方案自检修正 + V2 规划扩充）

> 影响文档：PRODUCT/PROTOTYPE/BACKEND/GLOBAL/README/STATUS · 决策摘要：D9/D13/D16/D17

方案自检与优化收口：①统计数修正（总表实际 MVP 39/V2 26/V3 12，原「41/24」为沿用旧文档错误基数，脚本核验 77 行后修正 6 处）；②B01 布局定稿「左栏导航+右栏内容」（用户要求内容放右边），选中目录后按创建时间正序（与「目录下知识」规则对齐）；③三建议落实（用户确认）：回收站独立软删列 recycle_status+deleted_at 不扩 8 状态机、目录按名称走数据库排序规则（废弃拼音首字母/sort_key 列）、热点缓存按库/目录维度分片；④补实现约束：删库连带取消定时发布任务与作废审核、可见库集合推导收敛为单一服务（F-0407 单一实现）、多用户跨空间公开读（BACKEND §9）；⑤V2 规划扩充（用户确认）：F-0209 作者主页、F-0210 库/知识 URL slug、F-0211 知识库关注、F-0310 知识置顶、F-0311 回收站批量、F-1101 统计按库维度、发现页默认最近更新倒序；总表 77→82 项（MVP 39/V2 31/V3 12），PROTOTYPE 新增 B24 作者主页；代码仍未动

## 2026/8/14 16:04 · ZCode（知识平台化重构：设计定稿 + 阶段 0 文档先行）

> 影响文档：PRODUCT/PROTOTYPE/BACKEND/FRONTEND/GLOBAL/README/STATUS · 决策摘要：D9 改写、D13 改写、D16/D17 新增

产品级变更「知识平台化重构」设计定稿并经用户逐项确认（完整方案见 `tmp/knowledge-redesign-proposal.md` 评审稿）：①产品定位由个人博客升级为**多用户知识平台**（任何注册用户可建库、访客可浏览所有公开库）；②全项目概念「文章」统一改名「知识」（物理表名 cnt_article→cnt_knowledge、接口路径 /api/v1/articles→/api/v1/knowledge 同步全改，不保留兼容期）；③新增三层组织：空间→知识库（公开/私有/授权 V2）→多级目录→知识，单库单目录；④可见性上移库级（文章级 visibility 废止）；⑤目录树替代 category、标签保留；⑥删库连带回收站（30 天）扩展 F-0305 至 MVP、不可跨库移动（仅复制/重新发布）；⑦排序定稿：列表按更新时间倒序、目录按名称排序（数据库排序规则）、目录内按创建时间正序；⑧首页知识流按身份聚合（登录含自己私有库 🔒）、导航「知识/知识库/AI小光」；⑨RAG 索引按库切分、检索按可见库集合过滤。阶段 0 文档先行已落地：PRODUCT 重写（定位/角色/三主线闭环/状态机/功能总表 73→77 项（MVP 39 / V2 26 / V3 12），新增 F-0106/F-0208/F-0308/F-0309、F-0305 提 MVP、F-0307 重定义/行为规则/安全）、PROTOTYPE 重写（导航头+8 屏原型+新增 B16/B20~B22）、BACKEND（模块职责/表清单 cnt_knowledge+kb_knowledge_base/kb_directory/kb_kb_grant/§13 重写/知识路径规范）、FRONTEND（模块映射/知识措辞）、GLOBAL/README 同步；后续阶段 1 纯改名→阶段 2 数据模型→阶段 3~6 功能实现见 STATUS 待办

## 2026/8/14 14:00 · ZCode（后端代码风格优化）

> 影响文档：STATUS/BACKEND · 决策摘要：无

后端代码风格优化三件套：①移除 19 处冗余 `@PathVariable("xxx")` 显式名称（参数名与路径模板一致时省略；根 POM maven-compiler-plugin 显式开启 `<parameters>true</parameters>` 保障隐式名称绑定；ChatController `/conversations/{id}/messages` 路径模板改名 `{conversationId}` 与参数名对齐）；②新增分页基类 `common/dto/PageQueryDTO`（`pageNo=1`/`pageSize=20`，`@Builder.Default` + `@SuperBuilder` 继承，子类无字段时省略 `@AllArgsConstructor` 防构造器冲突），`ArticleListQueryDTO`/publishing·content 两处 `ArticleQueryDTO`/`CommentQueryDTO` 全部继承并删除重复分页字段；③业务类内部类清零：`WorkspaceContext.Scope`→`WorkspaceScope`、`RefreshTokenService.RefreshSession`→`RefreshSession`（identity service 包顶层 record）、`ModelGatewayImpl.CircuitState`、`ChunkingServiceImpl.Section`（record，字段访问改访问器）均提取为顶层类型；BACKEND §5.1 新增分页基类/隐式命名/禁内部类三条规范；验证：mvn verify BUILD SUCCESS + 7 单测（新增 ArticleQueryDTOTest 4 断言分页继承默认值与覆盖）、运行时 API 全端点验证 @PathVariable 隐式绑定（公开详情/评论/阅读量、文章 CRUD、任务 retry、审核、模型配置 {scene}，均返回业务码非绑定错误）、双前端 E2E 9 通过（blog 8 + admin 1，期间清理 5 个残留 vite dev server 实例）

## 2026/8/13 19:10 · Qoder 代理（修复测试数据乱码）

> 影响文档：CHANGELOG · 决策摘要：无

修复开发库测试数据乱码：cnt_article 中 id=2087770970748989441 的文章标题「M04 ??????」与分类「??」（验证脚本插入的坏数据，非字符集问题），按确认方案改为「M04 发布验证文章」/「测试」；同步 pub_review（2 行）与 pub_release（1 行）的 article_title；验证：全库乱码残留 0、前端首页正常显示

## 2026/8/13 18:45 · Qoder 代理（前端 UI 升级：接入 Element Plus 统一双端风格）

> 影响文档：FRONTEND · 决策摘要：无

双前端 UI 精细打磨：①修复博客端 tokens.css 缺失 5 个 token（--xl-radius/--xl-radius-sm/--xl-bg-secondary/--xl-color-danger/success/warning）导致的 8 处样式失效（工作台直角、AI 气泡透明、徽标消失等）；②tokens.css 扩展阴影体系（--xl-shadow-sm/md/lg）与过渡 token（--xl-transition）；③Element Plus 全量接入（main.ts 注册）+ element-theme.css 将 --el-* 映射到 --xl-* 体系 + @element-plus/icons-vue 图标库（两前端同步）；④管理后台全面 EP 化（el-container/el-menu 侧栏、el-form 登录/设置、el-table 模型/审计 + el-dialog 详情 + el-pagination + el-skeleton）；⑤博客前台交互组件 EP 化（顶栏胶囊激活态+图标+Logo、登录 el-tabs/el-form、el-pagination、文章管理 el-select/el-tag/el-skeleton、聊天气泡修复+双头像、详情页正文卡片+TOC 滚动高亮、搜索/首页卡片化+标签胶囊+空态图标）；⑥原生 confirm/alert 全部替换为 ElMessageBox/ElMessage（5 文件 10 处）；⑦全局 :focus-visible 焦点环与 ::selection；验证：双前端 lint/stylelint/typecheck/build 全绿、单测 6 通过、E2E 9 通过（含登录/注册/评论/点赞流程）；修复 CRLF 行尾警告（本次改动文件）；构建产物主 chunk 因 EP 全量引入增至 ~1MB（MVP 可接受）

## 2026/8/13 14:00 · Qoder 代理（MVP 全量交付：M04~M13 + F-1301）

> 影响文档：STATUS/BACKEND · 决策摘要：D8（.env 唯一载体）/D13（发布即索引）/D14（小光命名）/D10（阶段调整）

MVP 全部里程碑交付：M04 内容管理（CRUD+自动保存+乐观锁 version+8 状态机一次定版）；M06+M12 AI 基座（ModelGateway 供应商解析+熔断、AiTask 任务底座幂等+Redis 进度+SSE、场景配置表优先 .env 回退）；M07 AI 创作（写作异步任务+审校异源校验 checkHeterogeneous，默认写作 qwen-plus/审校 qwen-max）；M05 RAG（发布即索引：事件→切片→Embedding 32 片/批→kb_chunk/kb_index_version ACTIVATING→ACTIVE→STALE；NoopVectorStore 降级待 Milvus）；M08 AI 对话（小光 SSE chunk/citation/done+会话/消息落库+文章级问答）；M09 增值（摘要/SEO 结构化落库 ai_enhance_result）；M10 审核发布（双闸门 force_review 开关+驳回三要素+立即/定时发布 PublishJob 每分钟扫描+发布事件/审计/缓存失效）；M11 纠错（匿名提交+追踪号+Redis 限流 429）；F-1301 热点缓存（详情 cache-aside 空值哨兵+分类/标签聚合+evictAll+降级回源）；M13 管理后台（工作区设置/模型配置+连通性测试/审计日志 + admin 前端四页）；修复：Boot 4 .env 大写属性 relaxed binding 失效（AiProperties/KnowledgeAiProperties 改 @Value 显式绑定）、GlobalExceptionHandler 补 404/JSON 解析异常、ai 与 publishing 同名 Bean 显式命名、hutool-json 缺失、审校模型默认 qwen-max；验证：mvn verify BUILD SUCCESS+3 单测、双前端门禁全绿、运行时全链路（真实百炼审校/写作/摘要/SEO/Embedding/连通性测试、发布→索引→公开可见、定时发布到期执行、SSE 流式、匿名纠错+限流、Redis 缓存键落盘）；里程碑顺序变更（M05 后置、M06/M12 提前）已记录

## 2026/8/13 01:20 · Qoder 代理（后端代码风格统一重构：传统 MVC 扁平化）

> 影响文档：STATUS/BACKEND · 决策摘要：D2（传统 MVC）/D15（模块内扁平结构）

后端代码风格统一重构——**去除“业务域”概念，统一传统 MVC 扁平包结构**：publishing 的 engagement 域拆为资源命名（EngagementController → CommentController + LikeController，EngagementService → CommentService + LikeService，接口 URL 不变）；identity 去 iam 域（controller/dto/entity/enums/mapper/service/vo 扁平化、WorkspaceApiImpl 移入 service/impl、TokenVO/UserProfileVO/WorkspaceVO 改 class+Lombok、测试同步 getter）；content 去 editor 域、api/dto 改 class+Lombok、ContentApi.listPublished 参数封装（ArticleQueryDTO 跨模块稳定类型）、ArticleMapper/ContentApiImpl 移位；修复历史遗留：identity 单行乱码损坏文件从 git HEAD 恢复重建（(wenhailong) 垃圾目录清理）、PublicArticleServiceImpl 适配新签名与 getter、CommentQueryDTO/ArticleQueryDTO 补 @Builder.Default；BACKEND §3/§4/§5/§7/§8 同步去域措辞 + §5.1 新增编码风格规范（参数封装 3~4 个上限、DTO/VO 统一 class+Lombok 带字段注释、命名按资源词、@Builder.Default 默认值）；验证：mvn verify BUILD SUCCESS + identity 3 单测通过（环境注意：编译需 JAVA_HOME 指向 JDK 25）

## 2026/8/12 21:40 · Qoder 代理（M03 博客前台公开页）

> 影响文档：STATUS/BACKEND · 决策摘要：D6（Redis 短期状态）/D9（默认空间）

M03 博客前台公开页交付：F-0201 列表/详情（默认空间公开读、Markdown 渲染 markdown-it + DOMPurify XSS 清洗、标题目录导航、卡片阅读时间与互动统计）；F-0202 分类/标签/搜索（LIKE + JSON_CONTAINS/JSON_TABLE 聚合、组合筛选 + 命中高亮 + 服务端分页）；F-0203 评论/点赞/阅读量（eng_comment/eng_like 唯一键幂等切换、Redis setIfAbsent 24h 防刷 + view_count 原子自增）；40_content.sql（cnt_article）/ 60_engagement.sql 入库；B01~B04 四页 + 顶栏导航搜索；**雪花 ID 精度修复**：Long 全局序列化为 String（JacksonConfig，Jackson 3 tools.jackson 包），前端 ID string + 统计数值 API 层 Number()；LikeButton 乐观更新与 props 同步竞态修复；验证：接口全链路（含 404 过滤/防刷/点赞切换/未登录 401）+ E2E 8 个 + 门禁全绿 + 浏览器实测

## 2026/8/12 19:50 · Qoder 代理（M02 身份与多租户）

> 影响文档：STATUS · 决策摘要：D9（注册即建空间）

M02 身份与多租户交付：F-0101 注册/登录/登出/刷新（JWT HS256 15 分钟短时效 + 刷新令牌 SHA-256 哈希存 Redis、GETDEL 轮换防重放、登出撤销；登录失败统一提示 + 300ms 统一延迟防枚举）；F-0102 注册即建空间（决策 D9）与工作空间查询；F-0103 五角色（OWNER/ADMIN/EDITOR/AUTHOR/VISITOR）体系入库 + JWT roles→ROLE 映射；F-0104 双层校验（Security 接口权限 + Service 资源归属，WorkspaceContext 来自 JWT claims）；10_identity.sql 新增 iam_role/iam_user/iam_workspace/iam_workspace_member 并入库；blog 前端 B08 登录注册页（/login）、会话 Store 原子操作、401 单飞刷新（/auth/ 豁免）、路由守卫、顶栏登录态；验证：后端 3 单测 + 接口全链路（含重放/撤销 401）+ E2E 2 个 + 浏览器实测截图全部通过

## 2026/8/12 19:10 · Qoder 代理（M01 代码骨架）

> 影响文档：STATUS/README/GLOBAL · 决策摘要：D1/D7/D8/D12/D15

M01 代码骨架交付：后端 backend/xlumen-server 父 POM + 7 模块（common 基座 ApiResponse/ErrorCode/BizException/RequestId(+Filter)/WorkspaceContext，业务模块按 BACKEND §4 依赖 DAG，boot 装配含全局异常处理/MyBatis-Plus 分页/探活接口）；配置体系 .env.example/.env + application.yml（spring.config.import 加载）+ logback-spring.xml（Appender 显式激活、级别走 XLUMEN_LOG_LEVEL）；SQL 初始化链路 sql/init 00~80（编号契约，90/95 随 V2/V3）+ scripts/init-db.ps1（-EnvFile/-Reset 二次确认）；前端 pnpm Monorepo 双应用（blog :5173 10 模块目录 / admin :5174 5 模块目录，Vue3.5+Vite7+TS5.8 strict+ESLint9 flat+Stylelint+Vitest+Playwright+Design Token）；根 package.json 代理脚本/pnpm-workspace/.editorconfig/.gitignore。验证：JDK 25 下 mvn clean verify 通过；应用启动连接 MySQL（159.75.6.183/xlumen_dev 已初始化）与 Redis 健康 UP、/api/v1/system/ping 返回统一响应；前端 lint/stylelint/typecheck/test/build/test:e2e 双应用全部通过

## 2026/8/12 18:03 · Qoder 代理（Maven 模块压缩 12→7）

> 影响文档：BACKEND/GLOBAL/FRONTEND/README/STATUS · 决策摘要：D15 新增

Maven 模块 12→7：按未来微服务拆分边界合并——identity（含 platform 域）、content（含 analytics 域）、publishing（含 engagement 域）、knowledge、ai（含 chat/enhance 域）+ common/boot；模块内按业务域分包（分包边界即未来拆分边界），表前缀全部保留；BACKEND §3/§4/§5 模块表与依赖 DAG 重写、§7/§8 表归属同步；GLOBAL 架构图与结构树、FRONTEND 前后端模块映射、README 同步

## 2026/8/12 17:58 · Qoder 代理（菜单标签调整：AI 助理）

> 影响文档：PROTOTYPE/FRONTEND · 决策摘要：无

前台导航 F-0701 菜单标签由 [AI 对话] 改为 [AI 助理]，位置调整为首页右侧、分类左侧（导航顺序：首页 / AI 助理 / 分类 / 标签 / 关于）；PROTOTYPE §3/§5.1/B00/B01/B09/D01 线框与描述同步；FRONTEND 应用边界与模块表同步（功能名 F-0701 仍为 AI 对话，仅菜单标签变更）

## 2026/8/12 17:51 · Qoder 代理（AI 命名确定：小光）

> 影响文档：PRODUCT/PROTOTYPE/STATUS · 决策摘要：D14 新增

产品内所有面向用户的 AI 能力（AI 对话、文章级问答、访客助手、AI 写作与审校反馈等）统一称呼为**小光**；PRODUCT §8 新增 AI 命名规则；PROTOTYPE B00 欢迎语、B07 访客助手、D01/D02 弹窗标题文案同步

## 2026/8/12 17:46 · Qoder 代理（前后端职责重组 + 发布即索引 + 目录结构重组）

> 影响文档：PRODUCT/PROTOTYPE/FRONTEND/GLOBAL/README/STATUS · 决策摘要：D11 新增

应用职责重组：blog（:5173）承载文章创建/编辑/发布/阅读/互动与 AI 对话全链路（含创作中心 B08~B13）；web 管理后台改为 admin（:5174）仅管理员配置管理（空间/成员/角色、模型配置、审计日志），不参与内容流转
> 影响文档：PROTOTYPE/GLOBAL/PRODUCT · 决策摘要：F-0701 定位调整

主页调整：B01 博客首页恢复为产品主页（文章展示），B00 降为前台 [AI 对话] 菜单入口页；PROTOTYPE 页面清单重组（B00~B19、A01~A07、D01/D02，验收清单同步）
> 影响文档：PRODUCT/BACKEND/GLOBAL/README/STATUS · 决策摘要：D10 阶段调整、D13 新增

功能总表重构：移除 F-0401 资料导入与 F-0406 URL 安全；F-0402 改为文章自动索引流水线（发布触发）；新增 F-0307 文章可见性（公开/私有，私有仅博主可见但建索引）；F-1201/F-1202 由 V2 提升 MVP；统计 74→73 项（MVP 37 / V2 24 / V3 12）；模块四更名"文章知识索引（RAG）"
> 影响文档：PRODUCT/BACKEND · 决策摘要：D13

RAG 检索规则：检索按身份过滤（访客仅公开已发布、博主全部含私有）；引用溯源改为篇名/段落定位可跳转原文；PRODUCT §3 业务闭环改为三主线（新增主线三反馈与保鲜闭环）
> 影响文档：GLOBAL/BACKEND/FRONTEND/README/STATUS · 决策摘要：D12 新增

仓库目录重组：server/→backend/xlumen-server/，web/→frontend/xlumen-frontend-admin/，blog/→frontend/xlumen-frontend-blog/；scripts、package.json、pnpm-workspace.yaml 等工程文件留仓库根；GLOBAL/README 全部运行与质量门禁命令路径同步（两文档逐字同源）
> 影响文档：BACKEND/STATUS · 决策摘要：D11~D13

BACKEND §13 重写为"文章知识索引与 RAG"（发布触发流水线）；kb_document/kb_snapshot 表移除，保留 kb_chunk/kb_index_version；STATUS 待办新增 M13（管理后台配置管理），决策新增 D11~D13
> 影响文档：FRONTEND/BACKEND/CHANGELOG · 决策摘要：无

技术约定补充：前端界面生成必须使用 frontend-design 类 Skill；前后端代码优先复用成熟工具类与开源框架（后端 Hutool、前端 VueUse/Element Plus）；代码必须有必要的注释（类/公共方法/复杂逻辑）；CHANGELOG 历史记录清空，仅保留本条，时间列改为精确到分钟
