# xLumen AI 变更日志

> 更新日期：2026/8/22
> **本仓库专属**。
> 按时间倒序记录（最新在顶部），每次 AI 会话结束必须追加一条；代码与文档更新同一提交，禁止虚构进度。
> 归档规则：正文只保留最近约 14 天条目；更早条目按原样移入 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md) 顶部，git 历史始终可回溯。

## 条目模板（追加新条目时复制以下骨架，置于本模板之下、所有旧条目之上）

```

## yyyy/M/d HH:mm · 工具名（一句话主题）

> 影响文档：受影响的文档相对路径 · 决策摘要：相关决策编号（D1~D19，见 STATUS.md 第 8 节），无则写"无"

变更内容正文（模块/文件/接口级别的主要变更，自由分点书写，不再放入表格单元格）。时间精确到分钟（yyyy/M/d HH:mm）。
```

## 2026/8/22 · ZCode（docs 文档体系整体瘦身）

> 影响文档：docs/ai/CHANGELOG.md、docs/ai/CHANGELOG-ARCHIVE.md（新增）、docs/ai/STATUS.md、docs/ai/BUGS.md、docs/ai/QA.md、docs/global/GLOBAL.md、README.md、.gitignore · 决策摘要：无

按用户「docs 文档内容越来越多，整体优化」要求瘦身文档体系（纯文档变更，代码零改动）：

- **CHANGELOG 归档机制**：正文只保留最近约 14 天条目（477→约 350 行）；2026-08-16 前 19 条（8-12~8-14 交付细节）全文移入新增 `docs/ai/CHANGELOG-ARCHIVE.md`；归档规则写入 CHANGELOG 头部与 STATUS §1 收尾流程；条目模板从中段移回头部（修复 STATUS「头部模板」指引失实）并精简为纯骨架；补 8/19 QA.md 创建条目丢失的标题
- **STATUS 瘦身**（144→约 100 行）：§2 里程碑压缩为终态陈述；§5 待办删除 28 行「已完成」（与 §3 能力基线重复），只留 OPT-1/V2；§6 过时核验表（W6/W7）替换为归档指引；指向已归档条目的引用改指归档文件
- **BUGS 清理**（130→约 105 行）：两段历史散文合并为「清单历史」简表；按记录约定移除已修复的 BUG-030 小节（细节保留在 CHANGELOG 8-22 BUG-030 条目）；待修复清单重排为 待复核/待用户决策/遗留运维 三块
- **QA 精简**（143→约 95 行）：§7 四个经验案例的教训已存在于 §3.9/§4，整节删除；§7.4 独有的 AI 审核确认弹窗验证结论提炼进 §5 审核与发布行；头部日期修正为 8/22
- **README 去重**（98→约 65 行）：本地快速开始整段替换为指向 GLOBAL §6（顺带消除过时的 spring-boot:run 用法）；技术栈缩短指向 GLOBAL §5；进度段改为指向 STATUS，消除手工刷新漂移；文档清单补归档行、计数 11→12
- **测试资产瘦身**：删除 docs/ai/assets/ 两个 zip（gui-test 与已提交 gui-test-screenshots 截图逐字节重复、browser-test 为 git 历史备份），.gitignore 增加 `docs/ai/assets/*.zip`；docs 体积 1.1M→约 380K
- **GLOBAL 导航同步**：§2 导航表与 §4 结构树补 CHANGELOG-ARCHIVE.md 行、文档计数 11→12

验证：改动文件相对链接 grep 复核无悬空；纯文档变更，不跑代码门禁。

## 2026/8/22 · ZCode（V2/V3 重新划分·决策 D19 + 9 项新 AI 功能立项登记）

> 影响文档：docs/product/PRODUCT.md、docs/ai/STATUS.md、docs/ai/CHANGELOG.md、docs/ai/IDEAS.md、README/GLOBAL/BACKEND/FRONTEND/PROTOTYPE（统计与阶段同步）· 决策摘要：D19（V2 27 / V3 25 / 暂缓 6）

按用户「个人使用为主 + 可能对外展示（简历 URL 浏览，面试官以访客身份打开）」口径，三轮发散形成候选池，经评分（总分 = 自用 P + 访客/面试官可见 I×1.2 + 成本低 +1/中 +0；I 系数经用户两次调参 1.4→1.2、演示冲击维度废除）与用户逐轮确认，定稿 V2/V3 划分并全部落文档：

- **V2 27 项**（总表 19 + 新登记 8，首批定版 15 项）：总表保留 F-0204/F-0504/F-0505/F-0603/F-0605/F-0606/F-0607/F-0703/F-0704/F-0806/F-1102/F-1103/F-1304/F-1306/F-0206/F-0210/F-0303/F-0304/F-0906；新登记 8 项见下
- **V3 25 项**：原 V3（F-0207/F-1305）+ 新候选 1（F-0220 站点 AI 导游）+ 自 V2 移入 16 项 + **暂缓 6 项**（F-0106/F-0211/F-1002/F-1003/F-1203/F-1204，多用户/治理向，说明列已标「暂缓」）
- **总表新增 9 项**：F-0216 问搜一体 / F-0217 语义搜索 / F-0218 知识术语悬浮解释 / F-0219 知识图文导读 / F-0220 站点 AI 导游 / F-0706 相关追问推荐 / F-0707 问答转知识草稿 / F-0809 图片 AI 讲解 / F-1005 评论 @小光问答（均为对话期新候选，直接立项未入池）
- **IDEAS.md 待评估登记 15 项**：IDEA-009~023（文风学习+术语表、双栏对照改写、AI 修订提案、重复/矛盾检测、翻译阅读模式、对话长期记忆、知识地图页、检索质量评分卡、个人写作周报、互动测验、批量整理助手、月度更新志 + D13 冲突 3 项待用户裁决）
- **统计**：总表 90 → 99 项（MVP 47 / V2 27 / V3 25，V3 含 6 项暂缓）；原「V2（AI 优先）」标注停用（D18 保留历史记录）
- **验证**：grep 复核无残留旧统计；纯文档变更，代码零改动

## 2026/8/22 · ZCode（BUG-030 修复 + BUG-015/026 移除）

> 影响文档：docs/ai/BUGS.md、docs/ai/CHANGELOG.md、docs/ai/STATUS.md · 决策摘要：BUGS.md 记录约定（修复仅在用户明确要求时进行，本次用户逐条指定：移除 015/026、执行修复 030）

接上一修复批次，用户追加三项处理，全部落地并验证：

- **BUG-015 移除（关闭）**：65 次复核均 200 建议关闭，用户明确后从清单移除（编号不回收）——小节+索引+历史说明同步清场
- **BUG-026 移除（清场）**：上一批次已修复的项，用户明确后将其「已修复」小节与索引一并移除（编号不回收）
- **BUG-030 修复**：私有 KB 直链对访客静默回退到「公开知识库」占位——新增公开探测端点 + 前端不可访问态：
  - 后端：`GET /api/v1/public/knowledge-bases/{kbId}`（`PublicKnowledgeService.getKnowledgeBase`，publishing 模块）：公开库返回库信息（name/visibility），私有库/不存在统一 404「知识库不存在或无权访问」；public 白名单 GET 已放行
  - 前端：`KnowledgeBaseDetailPage.vue` 加载链改为 `loadOwnerInfo → probePublicKb`，登录态本人库走库主模式；非本人探测 404 渲染「知识库不可访问（F-0307）+ 返回知识库列表」；公开库探测成功访客头部显示真实库名（替换固定「公开知识库」占位）
  - 验证：GUI 三态 ✅——访客私有库=不可访问页、访客公开库=库名+知识列表、OWNER 库主模式（编辑/新建目录）不受影响；curl 公开 200 / 私有 404 / 不存在 404
  - 门禁：前端 typecheck 通过；后端 JDK25 fat jar 重建重启后 ping 200

**代码变更**：`xlumen-publishing/.../PublicKnowledgeService.java` + `PublicKnowledgeServiceImpl.java` + `PublicKnowledgeController.java`（探测端点）；`frontend/.../knowledgeBase.ts`（fetchPublicKnowledgeBase）+ `KnowledgeBaseDetailPage.vue`（分流+错误态）。

## 2026/8/22 · ZCode（BUGS.md 修复批次：10 条已修复并验证）

> 影响文档：docs/ai/BUGS.md、docs/ai/CHANGELOG.md、docs/ai/STATUS.md · 决策摘要：BUGS.md 记录约定（修复仅在用户明确要求时进行，本次用户「开始修复 bug.md」授权）

按用户「开始修复 bug.md」明确授权，修复 BUGS.md 待修复清单并逐条验证（Java 侧 JDK25 编译 fat jar，重启后 curl/GUI 回归）：

- **BUG-018** `POST /tasks/{id}/retry` 任务不存在返 404：TaskController.retry 补 `get()` 判空抛 NOT_FOUND「任务不存在」✅ 实测 404
- **BUG-019** `POST /ai/enhance` scene OpenAPI 契约暴露枚举：EnhanceRequestDTO.scene 加 `@Schema(allowableValues={"SUMMARY","SEO"})`（ai 模块补 swagger-annotations-jakarta 编译依赖）✅ v3/api-docs 显示 `enum: [SUMMARY, SEO]`
- **BUG-020** `PUT /knowledge/{id}` 同版本同内容重发 409：KnowledgeServiceImpl.update 补幂等短路（版本+标题+正文+目录+标签全比对一致即成功返回，乐观锁 409 仅对真实并发冲突保留）✅ 同 version 同 body 重发 200
- **BUG-021** `DELETE /recycle-bin/{type}/{id}` 二次确认契约：契约确认=query `confirm=CONFIRM`（OpenAPI 自动暴露）；purge 补「条目不在回收站 → 404」与 restore 一致性 ✅ 无 confirm 409 / confirm 后不存在条目 404
- **BUG-022** `POST /public/knowledge/{id}/view` 对草稿 200：recordView 补存在性/可见性判定，未公开 404 ✅ 公开已发布 200、不存在/私有 404
- **BUG-023** `POST /auth/logout` 无 body 精确消息：AuthController.logout 改 `@RequestBody(required=false)` + 显式校验「refreshToken 刷新令牌不能为空」✅ 无 body/空 body 均精确
- **BUG-024** `POST /knowledge/retrieval-test` 缺参 Jackson 错误：RetrievalTestRequestDTO.topK 改 Integer + `resolvedTopK()` 兜底（null→10），IndexController 改用 ✅ `{}` 走 INVALID_PARAM「query 查询文本不能为空」
- **BUG-026** ReviewController 缺角色注解：类级补 `@PreAuthorize("hasRole('OWNER')")` 与 ReleaseController 对齐 ✅ OWNER submit 可达（业务 404 优先，未误伤）
- **BUG-029** `/register` 路由空白（前端 blog）：router 注册 `/register`（复用 LoginPage）+ LoginPage `mode` 按 `route.name==='register'` 初始化 ✅ GUI 实测 `/register` 渲染注册表单（用户名/邮箱/密码/注册按钮，注册 tab 默认选中）；`/login` 回归默认登录 tab 不受影响
- **BUG-028** 全局 UTF-8 P0：**结论修正**——浏览器 SPA 与 UTF-8 文件方式的 POST 中文均正常（Jackson 默认 UTF-8 解码，8-21 GUI 发布链路已验证）；8-22 见「14/14 400」为 Windows console curl 参数编码（GBK）假象。仍补防御配置 `spring.servlet.encoding.charset=UTF-8 + force=true`（Spring Boot 4 前缀从 `server.servlet.encoding` 迁移为 `spring.servlet.encoding`，由 ServletEncodingProperties 绑定；旧键已失效——反编译 Boot 4.1 确认）✅ UTF-8 文件 POST 中文 200（评论/建库落库）

**回滚与排除**：BUG-025 ping 修复批次重启后 5/5 200 未复现（保持待复核）；BUG-027 代码复查 `unsubscribe` 已清理 heartbeat/Set/Map 无残留路径（保持待复核无需改）；BUG-015 建议关闭待用户明确；BUG-030/FLOW-001~005 属契约缺口本次不动。

**验证方式**：后端 mvn package（JDK25）+ fat jar 重启 + curl 各端点断言 + GUI 浏览器 /register 渲染验证；前端 typecheck 通过、lint 0 errors（3587 warning 全为仓库既有 CRLF，非本次改动）。测试数据已清理（幂等草稿、中文验证库已删）。

**代码变更清单**（7 个 Java 文件 + 1 个 xml + 1 个 yml + 2 个前端文件）：
- `xlumen-ai/.../TaskController.java`（BUG-018）
- `xlumen-ai/.../dto/EnhanceRequestDTO.java` + `xlumen-ai/pom.xml`（BUG-019）
- `xlumen-ai/.../dto/EnhanceRequestDTO.java`（BUG-019）
- `xlumen-content/.../KnowledgeServiceImpl.java`（BUG-020）
- `xlumen-publishing/.../RecycleBinFacadeService.java`（BUG-021）
- `xlumen-publishing/.../PublicKnowledgeServiceImpl.java`（BUG-022）
- `xlumen-identity/.../AuthController.java`（BUG-023）
- `xlumen-knowledge/.../RetrievalTestRequestDTO.java` + `IndexController.java`（BUG-024）
- `xlumen-publishing/.../ReviewController.java`（BUG-026）
- `xlumen-boot/.../application.yml`（BUG-028）
- `frontend/.../router/index.ts` + `LoginPage.vue`（BUG-029）

---

## 2026/8/22 · ZCode（2026-08-22 单浏览器回归 + BUG-028 路径细分 + BUG-030 私有 KB 静默回退）

> 影响文档：docs/ai/BUGS.md、docs/ai/CHANGELOG.md、docs/ai/STATUS.md · 决策摘要：QA §1 三铁律、STATUS §1 不自动认领

按用户「再做一轮更详细的测试」要求第三轮 GUI 测试，**主代理 browser-use web-gui-tester 单跑**（子代理无法用 browser-use，已向用户确认取消 API smoke + Playwright E2E + docs 审计子代理；纯黑盒、1080p、纯测试不修）。

**测试账号**：`qa_gui_20260821 / Test123456`（OWNER，userId `2090619871503880192` workspace 起）。本轮成果：
- 新建私有 KB `QA-GUI-TEST-KB`（id 2090619871503880192）
- 创作并发布知识 `中文测试标题 BUG-028`（id 2090620721416671233）：**全程 GUI 路径成功保存并发布**（POST /knowledge → POST /review → POST /publishing/release 全部 200）

**关键 BUG-028 路径细分（推翻 8-22 「14/14 全失败」结论的适用范围）**：
- **浏览器 SPA JSON POST 路径（`Content-Type: application/json; charset=UTF-8`）**：全部成功——Jackson 默认按 UTF-8 解码 InputStream，绕开 servlet 默认字符编码
  - `POST /api/v1/knowledge` 200（保存草稿，跳转编辑页，状态「已保存」）
  - `POST /api/v1/knowledge/{id}/review` 200（AI 审核返回 2 条非阻断建议：移除"BUG-028"技术编号等）
  - `POST /api/v1/publishing/release` 200（状态「已发布」）
  - `POST /api/v1/admin/audit-logs` 同步抓到 `KNOWLEDGE_PUBLISH` 记录（10:15 qa_gui_20260821 / KNOWLEDGE 2090620721416671233）
- **未在 GUI 路径复现** BUG-028——8-22 用 curl 默认无 `charset=UTF-8` 头部触发 servlet Latin-1 解码，故 14/14 全 400
- **建议修复后复测范围收敛**到 `application/x-www-form-urlencoded` / `multipart/form-data` / curl 无 charset 头部三路径，才是 BUG-028 真实影响域
- 已更新 BUGS.md BUG-028 卡片附 8-21 GUI 复检结论

**新增 BUG-030**（私有 KB 直链对访客静默回退）：
- 复现：登出态访问 `http://localhost:5173/kb/2090619871503880192`（私有 KB）
- 现象：渲染"公开知识库 / 公开 / 这个视图下还没有知识。"——无 404 / 无权限提示，URL 与页面内容不一致
- 期望：返回 404「知识库不存在或无权访问」与 `/knowledge/{id}`「知识不可访问」UI 一致
- 推测根因：`/kb/[id].vue` 对 401/403/404 静默回退到全局"公开知识库"占位组件
- 影响：用户分享私有 KB URL，对方打开见空白公开页易误判故障
- 优先级：中

**其他验证项**（主代理 GUI 路径）：
- /chat AI 小光对话：发问「请用一句话介绍你自己」→ 流式响应正常，含可溯源引用契约文案
- /studio/releases：空状态渲染「暂无待发布知识 / 暂无发布记录」（已发布知识自动跳过）
- /studio/knowledge 草稿列表：「中文测试标题 BUG-028 / 草稿 / 2026-08-21 10:03」+ 编辑 + 删除（发布后删除按钮 disabled）
- /studio dashboard 三卡：知识管理 / AI 写作 / 发布管理入口齐全
- /knowledge-bases 我的 KB 列表：「QA-GUI-TEST-KB / 私有 / 1 篇」+ 编辑 + 删除
- /kb/{id} KB 详情：返回 / 编辑库资料 / 新建目录三按钮 + 「全部知识」sidebar tree + 知识卡片 + 标签云
- 管理后台 /settings /models /audit-logs 三页全部可达；audit-logs 抓拍本次 `KNOWLEDGE_PUBLISH`
- **BUG-029 复测**：`/register` 路由仍渲染空白（仅 banner），8-22 报告结论成立

**纯文档变更，代码零改动**。

**遗留未决**：
- 8-19 / 8-21 / 本轮 qa_ 账号数据均未自动清理（QA §3.8 22:00 调度未落实），历史公开列表/评论仍可见——治理问题待用户决策
- 本轮未清理 qa_gui_20260821 + 已发布的 `中文测试标题 BUG-028`（私有 KB 下，外部不可见），如需清理走管理后台或直接 SQL

**报告归档**：`docs/ai/assets/gui-test-2026-08-21/` + `gui-test-screenshots/2026-08-21/`（6 张截图）

---

## 2026/8/22 · ZCode（2026-08-22 全功能测试：浏览器渲染 + 复测 + 发现 P0 全局 UTF-8 缺陷）

> 影响文档：docs/ai/BUGS.md、docs/ai/CHANGELOG.md、docs/ai/STATUS.md · 决策摘要：QA §1 三铁律、STATUS §1 不自动认领

按用户「用 browser-use 模拟客户进行全功能测试」要求再跑一轮（距上次测试不足 24 小时、代码无新提交）。**主代理 browser-use 单跑**（不再启子代理，避免重复 8-21 已完成的 API smoke + Playwright E2E + 文档审计）：

- **浏览器渲染验证（11 个入口全部通过）**：博客 `/`、`/knowledge/{id}`（详情/评论/点赞/收藏/小光）、`/search`、`/knowledge-bases`、`/chat`、`/login`、`/register`；管理后台 `/`。SPA 路由、表单元素、列表卡片、目录/筛选器均正常渲染。**IAB 真实点击仍超时**（与 8-21 一致），按 QA §3.4 走 DOM 代替。
- **API 客户旅程**：ASCII 数据下登录→建库→建目录→建草稿→提交审核→列表分页→评论全通；进入中文 content 即触发 **P0 全局 UTF-8 解码失败**。
- **复测昨天 BUG（代码无变更）**：BUG-018/H1（tasks retry fake id 误返 200）、BUG-019/H2（ai/enhance scene=clarity 拒）、BUG-022/M4（public view 对草稿 200）**全部仍存在**；BUG-025/L5（system/ping 首测 401 冷启动）**今天未复现**（冷启动已过）。
- **GET 读路径不受 UTF-8 影响**：数据库已有中文知识详情/列表/评论可正常 GET 读取。

**新增 P0 缺陷 BUG-028（14/14 写接口含中文全失败）**：
- 现象：任意 POST/PUT body 字段含中文 → `{"code":"INVALID_PARAM","message":"请求参数格式错误：Invalid UTF-8 middle byte 0xd0"}` HTTP 400
- 影响：/auth/register（displayName）、/knowledge-bases POST/PUT（name）、/knowledge-bases/{id}/directories POST（name）、/knowledge POST/PUT/autosave（title/content）、/public/knowledge/{id}/comments POST（content）、/ai/writing|review|enhance（content）、/chat/conversations POST（title）共 14 端点全部阻塞中文用户
- 根因推测：Spring Boot 4 / Tomcat Servlet 容器字符编码未设为 UTF-8（应在 `application.yml` 加 `server.servlet.encoding.charset=UTF-8` + `force=true`），请求 body 被 servlet 用 Latin-1 解码再交给 Jackson 导致 UTF-8 多字节序列错位
- 历史背景：8-19 / 8-21 批次均未测中文 content（测试数据 ASCII），本 BUG 长期存在但未暴露；GET/boolean payload 不受影响
- 优先级：**P0**——核心功能完全阻塞中文用户，须最优先修复

**其他发现**：
- `qa_alpha_20260819` / `qa_fulltest_20260821` 等历史 qa_ 账号数据仍出现在公开列表与评论中，QA §3.8 22:00 自动清理机制实际未实现（数据库未发现清理调度）——治理问题待用户决策

**纯文档变更，代码零改动**。

## 2026/8/21 · ZCode（2026-08-21 全功能测试批次：4 路并行 + 11 模块覆盖 + 10 新 BUG 候选）

> 影响文档：docs/ai/BUGS.md、docs/ai/CHANGELOG.md、docs/ai/STATUS.md、docs/ai/assets/browser-test-2026-08-21/* · 决策摘要：QA §1 三铁律、STATUS §1 不自动认领

按用户「做一轮全功能测试」按 QA.md 规范发起，4 路并行：主代理 browser-use 访客视角 + 3 子代理（API 冒烟 / Playwright E2E / BUG 根因 + docs 审计），耗时 ~32 分钟。

**测试账号**：`qa_fulltest_20260821 / Test123456`（OWNER，userId `2090488188213489664`，workspaceId `2090488188544839680`）；跨用户账号 `qa_smoke_b_20260821 / Test123456`。已建公开库 `QA-public-test` (2090488328710090752)、私有库 `QA-private-test` (2090488329049829376)、目录 + 草稿。两账号按 QA §3.8 于 8-21 22:00 自动清理。

**通过项（11 模块覆盖）**：
- 身份与多租户：注册即建空间绑 OWNER（决策 D9）、JWT 鉴权、refresh 轮换、跨用户 404 隔离生效
- 博客公开阅读：首页 11 篇瀑布流、知识库发现页去登录引导、库页列表、详情页目录+Markdown+评论+互动按钮（访客提示）
- 搜索：RAG 关键词命中 11 篇 + 三筛选器（KB/目录/标签）
- 互动与反馈：详情页赞/踩/收藏/问小光/纠错按钮齐全（访客受限），评论列表时间显示「N 天前」（BUG-010 已修复）
- 内容管理：知识 CRUD（草稿/已发布状态机）、版本乐观锁、自动保存幂等
- 知识库体系：公开/私有库 CRUD、目录树 CRUD、子目录挂载
- 审核与发布：8 状态机、Reviewer AI 阻断与确认（IDEA-007 8-20 已落地）、release/unpublish/version 校验
- AI 对话：小光访客受限预览（输入框可用、发送 disabled）、SSE 流式 `chunk/citation/done` 全链路
- AI 写作/审校/增值：真实模型调用 200（SUMMARY/SEO 两 scene），结构化输出
- RAG 索引：发布即索引、Noop 降级（Milvus 未装）
- 管理后台：admin 四页登录入口、模型配置必填校验精确
- 多用户可见性（D9）：A 的私有库对 B 404（设计取舍，非 403）

**新发现 BUG 候选（10 条，登记 BUGS.md BUG-018~027，全部仅记录未修）**：
- BUG-018（H）`POST /tasks/{fake}/retry` 对不存在 task 误返 200
- BUG-019（H）`POST /ai/enhance` scene 枚举与 OpenAPI DTO 不一致
- BUG-020（中）`PUT /knowledge/{id}` 同 version 重发仍 409（非幂等，待草稿数据复测）
- BUG-021（中）`DELETE /recycle-bin/{type}/{id}` 二次确认契约不明
- BUG-022（中）`POST /public/knowledge/{id}/view` 对草稿也 200（虚增浏览量）
- BUG-023（低）`POST /auth/logout` 无 body 错误消息不指字段
- BUG-024（低）`POST /knowledge/retrieval-test` 缺参 Jackson 反序列化错
- BUG-025（低）`GET /system/ping` 首测偶发 401 冷启动
- BUG-026（中）`ReviewController` 缺类级 `@PreAuthorize` 与 F-0903 职责分离契约冲突
- BUG-027（低）`SseService.publish` 单 emitter 失败内存泄漏可能

**BUG-015 第三次复核**：65 次 create→submit→get（5 完整循环 + 30 并发 + 20 顺序）全部 200，**建议关闭**（按 STATUS §1 不自动移除，待用户明确要求时操作）。

**Playwright E2E**：6 套件 11 用例 100% 通过，无失败截图。
**API 冒烟**：71 端点全覆盖，0 个 5xx；必填校验精确；跨空间隔离生效；AI 真实模型调用成功。

**降级与排除**：
- Milvus 未装，RAG 检索走 NoopVectorStore（索引元数据正常，引用溯源能力降级）—— 预期
- AI 真实调用走百炼 API（qwen-plus / qwen-max / text-embedding-v4），有真实耗时与费用 —— 测试账号限定
- `/public/*` 写操作（like/dislike/favorite/comment）需 token——设计意图，非 BUG
- 不存在路由 `/xyz-does-not-exist` 主区域为空（路由兜底缺失）—— 体验小瑕疵，未登记 BUGS

**报告路径**：
- API 冒烟：`docs/ai/assets/browser-test-2026-08-21/api-smoke.md`（124 次请求落盘 `C:/temp/results.json`）
- E2E 重放：`docs/ai/assets/browser-test-2026-08-21/e2e-baseline.md`
- BUG 根因 + docs 审计：`docs/ai/assets/browser-test-2026-08-21/bug-and-docs-audit.md`

**docs 一致性审计结论**：
- PRODUCT §5 总表 90 项（MVP 47 / V2 41 / V3 2）在 README/GLOBAL/BACKEND/PRODUCT/STATUS §7/CHANGELOG 六处**完全一致**（D18 调整已同步）
- PROTOTYPE §7 MVP 页面范围 B00~B04/B08~B13/B16/B20~B23/A01~A04/D01/D02 与 STATUS 待办交付项**对齐**（B12 审核中心代码保留·导航隐藏）
- STATUS §3 能力基线摘要覆盖 8-12~8-20 所有交付**一致**
- STATUS §5 待办 OPT-1 / V2-AI 仍标「待认领」，其余已完成项标注日期与 AI 名**一致**
- **STATUS §6 W6/W7 行自身过期**（仍记 73 项 / MVP 37 / V2 24 / V3 12 与 8 份文档，与现行 90 / MVP 47、11 份文档不一致）—— 已记入 STATUS §7 待 STATUS 维护批次刷新，不在本次自动范围内
- **CHANGELOG 8-19 11:50 段落 D1 仍记旧值 82 项 / MVP 39**——同上待 STATUS 维护刷新

**遵守 QA §1 三铁律**（不替代质量门禁 / 缺陷不自动修 / 环境假缺陷先排除），**遵守 STATUS §1**（代码零改动，仅文档更新；BUGS 登记 + 报告落盘）。

## 2026/8/20 · ZCode（AI 相关功能优先级提升：V3 的 AI 并入 V2，V2 内 AI 优先）

> 影响文档：docs/product/PRODUCT.md、docs/frontend/PROTOTYPE.md、docs/ai/STATUS.md、docs/ai/CHANGELOG.md、docs/frontend/FRONTEND.md、docs/global/GLOBAL.md、docs/backend/BACKEND.md、README.md · 决策摘要：D10、D18

按用户「提升 AI 相关功能优先级」调整版本规划（决策 D10 记录，新增决策 D18）：
- V3 的 10 项 AI 功能并入 V2：F-0505 AI Trace、F-0607 代码解读、F-0704 多文档对比、F-0705 知识库洞察、F-0806 语音化、F-0807 分享摘要卡片、F-1003 AI 评论助手、F-1103 知识缺口分析、F-1203 AI 运行监控、F-1204 敏感内容检测；V3 仅剩非 AI 的 F-0207 知识归档与 F-1305 全文搜索。
- V2 内 24 项 AI 相关功能标「V2（AI 优先）」优先实施（AI 范围定义见 PRODUCT §5 阶段标记说明），STATUS 待办新增 V2-AI 优先批次。
- 总表统计更新为 90 项（MVP 47 / V2 41 / V3 2，原 90 项 MVP 47 / V2 31 / V3 12）。

**同步**：PRODUCT 总表与阶段标记说明、STATUS（进行中/待办/决策表/最近变更）、CHANGELOG、README/GLOBAL/BACKEND 统计数、FRONTEND 模块映射、PROTOTYPE §7.6 注记。**纯文档变更，代码零改动**。

## 2026/8/20 · IDEA-006~008 落地

- 知识列表前端改为 `IntersectionObserver` 滚动触底累计加载，保留页面原有单列/卡片墙结构、后端分页契约和错误/重试/已到底状态；B01 首页按原型保持右栏单列知识列表。
- 发布入口按钮统一为「发布」，点击先二次确认并提示将执行 AI 审核；error、失败或非法结果阻断，并展示高风险位置/依据/建议；warning/info 展示建议并由作者确认后继续；审核中心导航暂时隐藏，旧接口保留回退。
- 无限列表遇到重复页或最后一页短页立即停止请求，修复“加载更多…”常驻问题。
- 开发后端新增可选 `XLUMEN_DEV_PORT_GUARD`，检测端口占用后展示进程并交互确认结束；`XLUMEN_SERVER_PORT` 支持覆盖端口。

**验证**：双前端 lint/stylelint/typecheck/test/build 通过（仅既有 CRLF/大 chunk 警告）；后端 `mvn -pl xlumen-boot -am package -DskipTests`（JDK 25）通过；内置浏览器已验证首页/搜索自动加载状态、`/studio/review` 重定向到发布管理、admin 空间设置不再显示审核开关。

## 2026/8/20 17:12 · Codex（新增知识发布链路修复与交互优化）

> 影响文档：docs/frontend/PROTOTYPE.md、docs/ai/CHANGELOG.md · 决策摘要：D9、D16

修复新增知识必须先单独保存才能发布、自动保存遗漏知识库/目录/标签、首次自动保存后仍停留在新增路由、保存冲突仍可能继续审核、定时发布本地状态错误等问题；同时修复自动保存请求期间继续编辑时，旧请求返回会把新内容误标为已保存的竞态。发布入口现在会自动保存最新完整草稿，按立即/定时计划二次确认，确认后立即锁定编辑；AI 审核期间显示明确状态，error 逐条展示位置/依据/建议并阻断，warning/info 也展示详情后再由作者确认。后端自动保存的幂等判断同步纳入目录和标签，避免前端已提交但发布读取旧元数据。新增隔离浏览器回归用例覆盖新增知识、完整保存、AI 建议展示和确认发布，不触发真实 AI 或真实业务写入。

## 2026/8/19 12:05 · ZCode（BUGS.md 修复批次：9 条修复 + 1 条复核关闭）

> 影响文档：docs/ai/BUGS.md、backend/xlumen-server/sql/migration/88_knowledge_version.sql · 决策摘要：D9、D13、D16、D17

按用户「执行修复 BUGS.md」要求修复 2026-08-19 全功能测试批次缺陷。**修复 9 条（BUG-007/008/009/010/012/013/014/016/017）+ 复核 1 条（BUG-015 未复现保留 SUSPECT）**，全部从 BUGS.md 清单移除、编号不回收。

**后端 content**：
- BUG-014 知识版本历史（F-0303 补全）——`cnt_knowledge_version` 表此前在 BUGS.md 被误记「8-12 M04 已建表」，实际全仓无 DDL；本次补建表（init/40_content.sql + migration 88）+ `KnowledgeVersionEntity/Mapper` + 创建/更新/自动保存落库后写标题/正文快照（`saveVersionSnapshot`，MyBatis-Plus @Version 回写版本号）+ `GET /api/v1/knowledge/{id}/versions` 分页端点（版本降序，越权 404）。
- BUG-013 知识 update/autosave 接受越界 directoryId 静默写入——同库内换目录前经 `KnowledgeApi.checkOwnership` 校验，越界返回 400「目录不属于当前知识库」（create 路径已有，update/autosave 补全）。
- BUG-016 下架端点缺失 + 删除闭环——新增 `POST /api/v1/releases/{knowledgeId}/unpublish`（仅已发布可下架 → UNPUBLISHED(8) + 出索引 + 失效热点缓存 + 审计 KNOWLEDGE_UNPUBLISH，乐观锁冲突 409）；`KnowledgeServiceImpl.delete` 允许已下架删除（「删除已发布需先下架」闭环）。

**后端 publishing**：
- BUG-010 评论/AI 增值 createdAt 为 null——`CommentServiceImpl.createComment` 与 `EnhanceServiceImpl.store` insert 前手动 `setCreatedAt(now)`（DB 有 DEFAULT 但 MyBatis-Plus 不回填内存实体；前端「20684 天前」的根因）。
- BUG-012 读者纠错限流失效——`FeedbackServiceImpl` RATE_LIMIT 2→1（M11 契约同 IP 每分钟 1 条，第二次即 429）。
- BUG-007 审核通过后无发布入口——`ReleaseController` 补 `@PreAuthorize("hasRole('OWNER')")`（F-0903 职责分离落地）；`ReleaseServiceImpl.release` 移除「发布入参版本 == 知识当前版本」强校验（approve 状态迁移经 @Version 会把知识版本 +1，审核快照版本必然落后；幂等改由 release 表 knowledgeId+version 记录保证，迁移仍用知识当前版本防覆盖并发）。

**前端 blog**：
- BUG-007 配套：审核中心 `ReviewCenterPage` 「已通过」详情新增「发布」按钮（调 `createRelease` 立即发布，409 冲突恢复提示）。
- BUG-008 HomePage 左栏 H2「公开知识库」→「我的公开库」（数据源是鉴权接口仅返回自己的库，标题对齐语义；空态同步）。
- BUG-009 详情页「登录后可点赞、收藏与评论」加 `v-if="!session.loggedIn"`（登录态隐藏）。
- BUG-017 编辑器提示文案「归属库与目录不可修改」→「归属库不可修改，目录可调整」（与目录 select 实际行为对齐）。
- BUG-010 前端防御：`CommentList.formatTime` 空时戳返回空串（避免 null 当 1970）。

**验证**：后端 `mvn -pl xlumen-boot -am package` 通过 + 受影响模块单测全绿；前端 typecheck/lint 干净（仅既有 CRLF 警告）；E2E 9/9 通过；curl 实测——版本快照 create v0 + update v1、越界目录 400、评论 createdAt 非 null、feedback 第二次 429、approve→release→公开可见（status 4→6）、unpublish→公开隐藏（status 8）→已下架可删除。**环境注意**：修复验证期间发现 8080 端口存在守护进程自动拉起 `java -jar` 后端（PID 1400 等），排查时曾误判旧代码存活；spring-boot:run 不带 `-am` 时依赖模块从 .m2 取旧 jar，须 `mvn -pl xlumen-boot -am package` 后 `java -jar` 运行新代码。

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

## 2026/8/19 · ZCode（新增 AI 浏览器测试指南 QA.md）

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

