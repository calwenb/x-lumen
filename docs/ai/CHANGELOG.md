# xLumen AI 变更日志

> 更新日期：2026/9/6
> **本仓库专属**。
> 按时间倒序记录（最新在顶部），每次 AI 会话结束必须追加一条；代码与文档更新同一提交，禁止虚构进度。
> 归档规则：正文只保留最近约 14 天条目；更早条目按原样移入 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md) 顶部，git 历史始终可回溯。

## 条目模板（追加新条目时复制以下骨架，置于本模板之下、所有旧条目之上）

```

## yyyy/M/d HH:mm · 工具名（一句话主题）

> 影响文档：受影响的文档相对路径 · 决策摘要：相关决策编号（D1~D19，见 STATUS.md 第 8 节），无则写"无"

变更内容正文（模块/文件/接口级别的主要变更，自由分点书写，不再放入表格单元格）。时间精确到分钟（yyyy/M/d HH:mm）。
```

## 2026/9/7 22:06 · ZCode（小光悬浮助理：换行输入/面板拖拽缩放/清空会话）

> 影响文档：docs/ai/CHANGELOG.md（本条）· 决策摘要：无

用户提出「小光 · AI 助理」浮窗三项体验改进，全部落在 blog `modules/chat/components/FloatingAssistant.vue`：

- **Shift+Enter 换行**：输入框 input→textarea（rows=2，样式对齐 ChatPage 口径），Enter 发送沿用 ChatPage 的 `@keydown.enter.exact.prevent` 模式；用户气泡已有 `white-space: pre-wrap`，换行原样展示。
- **窗口移动**：按住标题栏 pointer 事件拖拽（setPointerCapture；清空/关闭按钮排除在拖拽外）。首次拖拽才把 CSS 右下角锚位固化为 left/top 坐标；坐标钳制留 8px 边距保证标题栏可达；监听 window resize 将面板拉回视口。
- **窗口缩放**：面板加原生 `resize: both`（overflow:hidden 已满足条件），min 280×320、max 沿用视口约束；缩放后拖拽钳制按实时 offsetWidth/Height 计算。
- **清空对话**：标题栏新增「清空」胶囊按钮（无消息时禁用），`clearConversation()` 先 abort 进行中的流式请求（`activeController` 记录当前控制器，finally 判等清理）再清 `messages`；会话本就不落盘，仅影响当前面板。
- 验证：eslint --fix 后 build 复跑通过（5.4s）、`vue-tsc` 全绿、stylelint 过；v-html 警告为存量。

## 2026/9/7 21:42 · ZCode（互跳入口兜底端口 5173/5174→6010/6011，修「前往前台跳 5173」）

> 影响文档：docs/frontend/PROTOTYPE.md · 决策摘要：无（9-07 端口方案 D11 修订补漏）

现象：admin 侧栏「前往前台」跳 `http://localhost:5173`。根因链：链接取 `import.meta.env.VITE_BLOG_URL`（**构建期**变量，由 deploy-admin.sh 写 .env.production 打入 bundle），当前在跑的 bundle 构建时该变量缺位（写 .env.production 的双环境脚本 3ac9674 尚未推送部署），落到代码兜底常量；而兜底是端口改版（cfc1910）时漏改的 vite 旧默认口 5173/5174。修复：双端 App.vue 兜底改 6010/6011（blog→admin 同步修 5174→6011）、PROTOTYPE.md §1 表格 :5173/:5174→:6010/:6011；历史归档（CHANGELOG 旧条目、assets 测试记录）不回改。双端 `vue-tsc` 通过。服务器侧根治仍待：推送 3ac9674 后用新脚本重新构建部署，URL 即构建期打入。

## 2026/9/7 21:37 · ZCode（管理后台「索引维护」页，接入全平台补跑接口）

> 影响文档：docs/frontend/FRONTEND.md · docs/backend/BACKEND.md · docs/deploy/DEPLOY.md · 决策摘要：无

admin 前端新增 `knowledge` 运维模块（对应上条 21:24 后端接口）：

- `modules/knowledge/api/indexOps.ts`：`triggerReindexAllPlatform`/`fetchReindexPlatformStatus` 封装，Long 数值字段 API 层统一转 number（沿用 trace.ts 口径）。
- `modules/knowledge/pages/IndexOpsPage.vue`：统计带（可重建/已处理/成功/失败）+ el-progress 进度条（运行中条纹流动）+ 任务状态 tag + 起止时间 + 失败明细表（≤100 条）；触发前 ElMessageBox 确认（提示付费 embedding），运行中每 3s 轮询、结束即停，页面卸载清定时器；顶部 el-alert 提示先确认「Milvus 可达」再补跑。
- 路由 `/index-ops`（authenticated，OWNER/ADMIN 守卫复用）+ App.vue 侧栏菜单「索引维护」（AI 调用追踪之后）。
- 验证：`vue-tsc` 通过、eslint --fix 后 build 通过（4.2s，chunk 警告为既有）。
- 文档：FRONTEND.md admin 模块树/表补 knowledge 行；BACKEND.md 与 DEPLOY.md 注意三改为「UI 入口在索引维护页」。

## 2026/9/7 21:24 · ZCode（全平台索引补跑接口 reindex-all-platform）

> 影响文档：docs/backend/BACKEND.md · docs/deploy/DEPLOY.md · docs/ai/STATUS.md · 决策摘要：无

背景：Milvus 停机降级 Noop 期间发布的知识只有 MySQL 元数据、无向量；既有 `reindex-all` 是博主自助口径（登录用户可见库、同步单页 100 篇），无法承担运维全量补齐。新增全平台补跑：

- content 模块 `ContentApi` 新增运维专用契约 `countPublishedPlatform()` / `listPublishedSnapshotsAfter(cursorId, limit)`（status=PUBLISHED + 非回收站、id 游标分页含正文/workspaceId/kbId/version，不限可见库），`ContentApiImpl` 实现。
- publishing 模块 `IndexBackfillService` 新增 `reindexAllPlatform()`（专用单线程守护执行器异步逐条强制重建，单条失败不中断、重复触发不并跑）与 `reindexAllPlatformStatus()`；进度为内存态（重启即失），新视图 `ReindexPlatformVO`（started/running/total/processed/ok/failedCount/failed≤100/起止时间）。
- 端点：`POST /api/v1/knowledge/reindex-all-platform` + `GET /api/v1/knowledge/reindex-all-platform/status`（均需登录，无角色体系，双端暂无 UI，curl 触发）。
- 文档：BACKEND.md 索引补跑节补两条；DEPLOY.md §4 注意三（Milvus 恢复后补跑 runbook）；STATUS.md 遗留运维改为一次调用口径。`mvn -pl xlumen-content,xlumen-publishing -am compile` 通过。

## 2026/9/7 19:39 · ZCode（前端部署脚本双环境隔离改造）

> 影响文档：docs/deploy/DEPLOY.md §7.3/§11 · 决策摘要：无（补齐环境隔离，用户拍板「直接改造」）

- **动因**：环境隔离核查结论=后端脚本已全维隔离（分支/三目录/端口/进程 pkill 全路径/MySQL 库/Redis index/Milvus database/JWT/邮件），但 `deploy-blog.sh`/`deploy-admin.sh` 为单产物目录+写死 master+互跳 URL 单一正式值，「测试/正式共用」注释自曝无隔离，一次部署即覆盖现网。
- **改造**：两脚本对齐后端脚本结构=配置区双环境（BRANCH/SRC/APP/互跳 URL 各两套，目录 `/wen/{project,app}/frontend/xlumen-frontend-{blog,admin}/{test,prod}`）+ 位置参数 `<test|prod>` + 步骤 0 y/N 确认 + 环境名时间戳日志 + `git clone -b 分支` + `rm -rf` 仅本环境产物；互跳地址 test=6011/6010、prod=5011/80 别名。`.gitignore` 补 `frontend/**/.env.production`（脚本构建期生成物，防未来入库）。`bash -n` 双脚本语法过。
- **DEPLOY.md**：新增 §7.3 测试站点 nginx 块（6010/6011，root 指 test 产物、proxy_pass 6060，SSE 透传同 7.1）+ 安全组提醒；§11 脚本表与示例命令改双参数版。
- **服务器连带**：首次跑 test 部署会自建 clone；nginx 需按 §7.3 加两个测试 server 块后 reload。

## 2026/9/7 19:10 · ZCode（端口方案定版：prod=506x/501x，dev+test=606x/601x）

> 影响文档：AGENTS.md、docs/ai/STATUS.md（D11 行）、docs/ai/QA.md §3、docs/global/GLOBAL.md §2/§6.4/6.5、docs/frontend/FRONTEND.md §2、docs/deploy/DEPLOY.md · 决策摘要：无（端口段约定，用户拍板）

- **定版映射**：prod 后端 **5060**、博客 **5010**（80 保留为别名双监听）、后台 **5011**；dev 与 test **同位复用**（不同机不冲突）：后端均 **6060**、前端均 **6010/6011**（vite dev 与 nginx 同号，URL 跨环境一致）。旧值 8080/8081/5173/5174/8082 全量退役。
- **改动面**：三份 profile `server.port`；`DevPortConflictGuard` 默认端口 8080→6060；双端 `vite.config.ts`（port+proxy target）、`package.json`（dev/preview --port）、`playwright.config.ts`（baseURL/webServer.url）、admin `main.ts` 注释；`deploy-backend.sh`（PORT_TEST=6060/PORT_PROD=5060+注释）、`deploy-blog.sh`（ADMIN_URL→:5011）；DEPLOY.md 28 处（架构图/防火墙/§4 表/§7 验证 curl/§8 Nginx 两块 listen+proxy_pass/§10 验收/§11 表/排障行——排障行顺带修正旧键名 `XLUMEN_DEV_PORT_GUARD`→`xlumen.dev-port-guard`）；GLOBAL/FRONTEND/QA/AGENTS/STATUS(D11) 端口表述。历史测试归档（docs/ai/assets、BUGS.md 复现步骤）按规则不改。
- **服务器连带（用户待办）**：nginx 博客块加 `listen 5010;`（80 保留）、后台块 `listen 8082→5011`、两处 `proxy_pass→127.0.0.1:5060` 后 reload；安全组公网放行 80/5010/5011（测试机 6010/6011），5060/6060 仅本机；`config/application-{test,prod}.yml` 换新版（端口已变）。本机 dev 重启后端（6060）与 vite（6010/6011）生效。

## 2026/9/7 18:56 · ZCode（D31：配置分层重划 + Milvus database 环境隔离）

> 影响文档：docs/ai/STATUS.md（D29 修订+D31）、docs/global/GLOBAL.md §6.2、docs/backend/BACKEND.md §17、docs/deploy/DEPLOY.md §4 · 决策摘要：D31（修订 D29「各环境文件自我完整」条款）

- **配置分层重划（用户两轮拍板）**：判据=键是否环境属性而非值是否相同。`application.yml`（入库）只留环境无关公共项：active 开关、ai 六关、driver+hikari、mail smtp 属性、lettuce 池、编码、circular、management、logging、模型选型（bailian/deepseek base-url+model-*）、agent/writing/trace/tts 参数；三份 profile（不入库）收纳全部环境属性与密钥：`server.port`、datasource url/账密、redis host/port/密码/index、mail 账密、**milvus 三件**、**site-url**、**dev-port-guard**、jwt-secret、api-key、mail-from。五文件总行数 469→252（base 97 + 3×38 + demo 41）；`application-demo.yml` 模板同步新结构并注明划分原则。等效比对（merge(base,profile) vs 旧自含文件）通过，唯一差异=reviewer 修复。
- **P0 修复**：test/prod 的 `model-reviewer` 曾被改为 qwen-plus，与写作同源（同供应商+同模型）会触发 `ReviewServiceImpl.checkHeterogeneous` 抛 CONFLICT、发布自动 AI 审核必挂；统一回 `qwen-max`（base 定义，profile 可同名键覆盖）。
- **Milvus 向量隔离（D31 附带）**：集合名固定 `kb_chunks`，database 是唯一隔离层，原三环境全共用 default 一份向量（测试数据会污染正式检索、reindex/删除互毁）。profile 定版：dev=default（既有向量不动）、test=`xlumen_test`、prod=`xlumen_prod`；代码探测已带 dbName（VectorStoreAutoConfiguration:59）、首写自动建集合，唯 database 须服务端预建（已交付 curl 命令，**Milvus 服务当前未运行，19530 拒连**，待用户启动服务后建库）。同步清空 `xlumen_test`/`xlumen` 两 MySQL 库的 `kb_chunk`(17 行)/`kb_index_version`(13 行) 元数据，防新库无向量而元数据假 ACTIVE（BUG-004 同款症状）；各环境发布知识时自然从零索引。
- **验证**：五份 YAML js-yaml 解析通过；等效比对差异仅 reviewer 一处；元数据清理后 test/prod 两表计数 0/0（dev 未动）。未跑 mvn 冒烟（用户指示快速执行免验证）。

## 2026/9/6 21:45 · ZCode（D30：dev/test/prod profile 移出版本库）

> 影响文档：AGENTS.md、docs/ai/STATUS.md（D29 修订+D30）、docs/global/GLOBAL.md §6.2、docs/backend/BACKEND.md §17、docs/deploy/DEPLOY.md §4/§6/§11 · 决策摘要：D30（修订 D29 的"随仓库提交并打进 fat jar"条款）

- **动因**：`application-{dev,test,prod}.yml` 含真实密钥（MySQL/Redis 密码、百炼 API Key、SMTP 授权码），用户拍板不再入库；仓库仅保留占位符模板 `application-demo.yml`。
- **变更**：`git rm --cached` 三份 profile（本地文件保留）+ `.gitignore` 忽略；配置存放改为——开发机 resources/（本地构建打进包，仅本机）、服务器 jar 同级 `config/application-<env>.yml`（Spring Boot 外部加载、优先级高于 jar 内，改配置重启即生效无需重打包）。`deploy-backend.sh` 新增启动前校验 `$APP/config/application-$ENV.yml` 存在（放在停旧进程之前，避免"旧的停了新的起不来"），`bash -n` 语法过。文档六处"随仓库提交并打进 jar"表述同步改写。
- **安全提示**：git 历史（f5d8796 及其后合并）仍含旧密钥 blob，本次仅从 HEAD 移除；仓库若转公开或需彻底清除，须 filter-repo 重写历史 + 强推，并轮换全部已提交过的密码/Key（推荐尽快轮换）。

## 2026/9/6 18:12 · ZCode（D29 profile 化改造收尾核验与缺陷修复）

> 影响文档：AGENTS.md、docs/ai/QA.md、docs/ai/STATUS.md、docs/deploy/DEPLOY.md、docs/backend/BACKEND.md · 决策摘要：D29（配置唯一载体 spring profile YAML，取代 D8）

- **核验结论**：D29 主体改造已完成。四份 `application-{dev,test,prod,demo}.yml`（115~119 行）落在 `xlumen-boot/src/main/resources/`，`application.yml` 瘦身为 3 行仅留 `spring.profiles.active: dev` 开关；`backend/xlumen-server/config/` 目录与 `.env.example` 已删除，`spring.config.import` 全仓无残留；Java 侧 31 处 `@Value` 与 3 个 `@ConfigurationProperties(prefix = "xlumen")` 全部走小写点号键，无 `XLUMEN_*` 大写占位符残留；`init-db.ps1` 已改 `-Profile` 参数解析 YAML `spring.datasource` 段；`deploy-backend.sh` 启动命令携带 `--spring.profiles.active="$ENV"`。STATUS/GLOBAL/BACKEND/README/DEPLOY 决策与载体描述均已同步（D8 标注历史、D29 生效）。
- **修复缺陷 2 项**：①`application-prod.yml` 中 `xlumen.agent-tool-timeout-millis` 键名被误写为 `agent-tooloklp;-timeout-millis`（键盘误触污染），因 `@Value("${xlumen.agent-tool-timeout-millis}")` 无默认值，prod profile 启动会直接 `Could not resolve placeholder` 失败，已纠正键名；②`deploy-backend.sh` 的 `--spring.profiles.active` 原先放在 `-jar` 之前，会被 JVM 当作无法识别的启动选项导致进程起不来（Spring 应用参数必须在 `-jar <jar>` 之后），已移到 jar 之后并补注释说明。
- **旧 .env 引用清理**：`AGENTS.md`（项目结构与 Security 节的 `config/.env.example` 描述）、`logback-spring.xml` 头注释（级别改述为 profile 的 `logging.level.root`）、`docs/deploy/DEPLOY.md` 第 5 节 `init-db.ps1 -EnvFile` 用法（改 `-Profile` 解析 datasource）、`docs/ai/QA.md` 环境自检的端口守卫与 Redis 密码表述（改指 `application-dev.yml`）、`deploy-backend.sh` 三处 `.env` 注释、根 `.gitignore` 的 D8 注释块。CHANGELOG 历史条目中的 `.env` 记载按归档规则原样保留不改写。
- **验证**：五份 YAML（含 `application.yml`）经 js-yaml 解析通过，四环境端口/键值抽查正常（dev/test/prod/demo 均含全部必填键，prod 键修复后校验通过）；`spring.config.import`、`@Value("${XLUMEN_`、`config/.env`、`-EnvFile` 在 backend/scripts/docs 现行文档中 grep 清零（仅历史 CHANGELOG 条目保留）。未跑 mvn 构建（本次改动仅配置文件与文档，无 Java 源码变更）。
- **部署事故复盘（9/6 晚，服务器 prod 首跑）**：服务器上执行的 `deploy-backend.sh` 是从 Windows 工作区手工复制的副本，本地 `core.autocrlf=true` 使其变为 CRLF：①`set -euo pipefail` 整行因尾部 `\r` 报"无效的选项名"失效，`git pull` 失败后脚本不再熔断，继续用旧代码打包；②CRLF 令 git 视该脚本为"有本地修改"，`git pull --ff-only` 拒绝合并，工作区停在部署前的旧提交（无 D29 profile）。处置：服务器 `sed -i 's/\r$//' scripts/deploy-backend.sh` 转回 LF 后重新 `git pull` + `bash` 执行。**防复发**：新增根 `.gitattributes`（`* text=auto`、`*.sh/*.java/*.xml/*.yml/*.md/*.sql/*.ts/*.vue` 强制 `eol=lf`、`*.ps1 eol=crlf`），任何平台检出 shell 脚本恒为 LF；服务器上取代码只允许 `git pull`，禁止从 Windows 工作区手动 scp `.sh`。

## 2026/8/29 07:30 · ZCode（全功能黑盒巡检 1080p + 修复知识级问答缺上下文）

> 影响文档：docs/ai/QA.md（无·测试记录）、docs/ai/STATUS.md（§7 最近变更随本条目更新）、docs/frontend/FRONTEND.md（前端两处） · 决策摘要：无

- **测试范围**：按 QA.md §5 全模块巡检（1920×1080，qa_ft_0829 / qa_ft_0829_b 测试账号）：身份多租户 / 博客公开阅读 / 互动反馈 / 内容管理 / 知识库体系 / 审核发布 / AI 对话 / AI 写作 / AI 内容增值 / RAG 索引 / 管理后台 / 多用户可见性，另覆盖 V2 页（知识地图、站点更新日志、搜索关键词+语义双线、AI 调用追踪、站点动态管理）。
- **全部通过项**：注册（即建空间）/登录/登出/再登录；详情页（TOC/右轨赞踩收藏/相关推荐/AI 摘要块「AI 摘要」）；赞踩互斥+toggle、收藏、评论发表、读者纠错（追踪号 + 同 IP 1/分钟 429「提交过于频繁」）；建库（公开/私有）、目录树新建/改名/删除、访客私有库直链「知识库不可访问」fallback（BUG-030 复验通过）、回收站；编辑器选库/目录、保存草稿、发布→自动 AI 审核（0 条建议）→自动发布→消息中心通知→审核中心；AI 对话（Agent 工具循环 knowledge.search + 引用溯源展开）、知识级问答、访客助手（访客模式）；AI 写作（topic→四步生成→保存为新知识）；RAG 发布即索引（ACTIVE/3 切片）；后台（空间设置/模型配置空态/审计日志 KNOWLEDGE_PUBLISH/站点更新日志/新增动态表单）；多用户可见性（B 见 A 公开知识、B 直链 A 私有库被拦）。
- **发现并修复缺陷**：**知识级问答（详情页「问小光」）不注入当前知识上下文**——用户问「这篇文章主要讲什么」时，模型答「无法判断所指是哪一篇」。根因：`ChatController` 路径 `knowledgeId` 传入 `ChatServiceImpl.runAgent` 后未被使用（未进 `toolContext.knowledgeIds`，系统提示也无知识标题），Agent 不知锚定哪篇。修复：①`runAgent` 在 `knowledgeId != null` 时并入 `toolContext.knowledgeIds`（knowledge.search 精确检索单篇）；②新增 `ChatRequestDTO.knowledgeTitle`，`runAgent` 将其注入系统提示（「本次问答锚定知识《标题》」）；③前端 `streamKnowledgeAsk`/`KnowledgeQaDialog` 透传 `knowledgeTitle`。实测模型已能识别「这篇文章」=《QA 巡检：测试知识》并据此作答。
- **顺带修正文案**：`KnowledgeBasesPage.vue`「全平台公开知识库聚合将在 V2 提供」已与 V2 交付不符，改为「全平台公开知识库聚合见「发现」页」。
- **排除/遗留（非本次缺陷，记录备查）**：①存量旧发布知识（calwen 的「虚拟线程实战」「Spring Boot 4 自动配置」等）无 ACTIVE RAG 索引——知识级问答/对话检索不到其内容（BUG-004 补跑缺口），仅最近发布知识可检索；②审计日志 KNOWLEDGE_PUBLISH 的「操作人」为空——AI 自动发布由系统上下文触发，`WorkspaceContext.userId()` 为空（未把发起人穿过审核事件），低优先级；③AI 写作生成标题为通用「AI 生成文章」（识别主题能力偏弱，非代码缺陷）；④模型配置页空态为默认无场景配置，属预期。
- **验证**：后端 ai 模块 10 个测试类全绿（`mvn -pl xlumen-ai -am test`，0 失败）；blog `typecheck`/`build`/改动文件 `eslint`（0 error，1 处既有 v-html 警告）通过。测试数据（qa_ft_0829 / qa_ft_0829_b 及公开/私有库、已发布知识、目录）保留在 xlumen_dev。



> 影响文档：docs/ai/QA.md（无·测试记录）、docs/ai/STATUS.md（§7 最近变更随本条目更新） · 决策摘要：无

- **测试范围**：按 QA.md §5 全模块巡检（1080p，qa_ft_0828 / qa_ft2_0828 测试账号）：身份多租户 / 博客公开阅读 / 互动反馈 / 内容管理 / 知识库体系 / 审核发布 / AI 对话 / AI 写作 / AI 内容增值 / RAG 索引 / 管理后台 / 多用户可见性，另覆盖 V2 页（知识地图、站点更新日志、搜索双线）。
- **全部通过项**：注册/登录/登出/再登录；详情页渲染（TOC/AI 摘要块）；赞踩互斥、收藏 toggle、评论发表、读者纠错（追踪号）；建库（公开/私有）、目录树、编辑器保存草稿、发布→自动 AI 审核→自动发布→消息中心通知→审核中心；AI 对话流式+工具调用（knowledge.list/getDirectoryTree/search）+ 引用计数；AI 写作「大纲→分章→自审→修订」四步生成完成（含"保存为新知识"）；管理后台（空间设置、模型配置空态、审计日志 KNOWLEDGE_PUBLISH、AI 调用追踪 13 次 0 失败）；多用户可见性（公开知识跨空间可见、私有库 404「知识库不存在或无权访问」+ 前端"知识库不可访问"fallback）；回收站（删库→恢复）；站点更新日志页。
- **发现并修复缺陷**：**blog 与 admin 前端均无 404 兜底路由**——访问不存在的路径（如 `/map` 误为 `/knowledge-map`）时 Vue Router 无匹配路由、无 fallback，渲染空白页（Vue Router warn `No match found`），与 BUG-029 同类路由盲区。修复：新增 `NotFoundPage.vue`（blog `/modules/blog/pages`、admin `/modules/workspace/pages`，匹配 V2 设计系统）+ 两端 router 追加 `path: '/:pathMatch(.*)*'` catch-all。
- **排除项（非缺陷）**：①AI 对话「切片为空跳过索引」— 因测试用 fill 填充正文时换行丢失为字面 `\n`（整篇变单行，chunking 按标题切分产出空），非产品问题；存量 9 篇已发布知识均有 ACTIVE 索引（8-21 补跑），RAG 链路正常。②登录态在整页刷新后丢失（M02 约束，刷新令牌不持久化，预期）。③向量语义/问小光搜索未登录禁用（D25 预期）。
- **验证**：blog 与 admin 均 `typecheck`/`lint` 通过（0 errors）；浏览器复测 `/knowledge-map` 渲染 404 页、admin `/foo` 渲染 404 页。测试数据（qa_ft_0828 / qa_ft2_0828 及知识/库）保留在 xlumen_dev。



> 影响文档：docs/frontend/FRONTEND.md（无·纯后端与配置）、docs/ai/STATUS.md（§6 遗留说明随本条目闭合） · 决策摘要：无

- **背景**：8-26 V2 交付遗留三项（CHANGELOG 2026/8/26 遗留说明）：F-0806 TTS 旧模型 qwen-tts 网关已下线（/audio/speech 404）；F-0809 图片讲解默认走文本模型 qwen-plus 不可见图片；F-0105 SMTP 账号未填走开发模式。用户 8-27 指示：TTS 与图片讲解用较便宜的模型；SMTP 已在 .env 配好。
- **F-0806 语音合成（便宜档 qwen3-tts-flash）**：TtsController 硬编码 qwen-tts 改为 `@Value("${XLUMEN_BAILIAN_MODEL_TTS:qwen3-tts-flash}")` + `XLUMEN_TTS_VOICE`（默认 Cherry），.env 可换模型/发音人；端点或网关不可用时仍 501/503 优雅降级（实测网关 404 走降级，前端友好提示）。
- **F-0809 图片讲解（便宜档视觉模型 qwen3-vl-flash）**：ChatRuntime 新增 `chatWithModel(ws, scene, modelName, messages, temp, maxTokens)`——凭证固定走百炼、模型名不经场景表解析，配额（WRITING 场景）与 ai_call_log 追踪照常埋点；AssistServiceImpl 对 `image_explain` 分支走该路径，模型取新增 `XLUMEN_BAILIAN_MODEL_VISION`（默认 qwen3-vl-flash）。实测真实图片 URL 讲解成功（ai_call_log 落 model=qwen3-vl-flash success=1）。
- **F-0105 忘记密码（真实 SMTP 发信）**：application.yml 补 `spring.mail.*`（host/port/username/password 绑定 XLUMEN_MAIL_*，465 SSL），Boot 自动装配 JavaMailSender 生效；MailService 发送成功补 INFO 日志（`邮件已发送 to=...`）。实测 QQ SMTP 465 真实发信成功。
- **配置**：config/.env.example 新增 `XLUMEN_BAILIAN_MODEL_VISION` / `XLUMEN_BAILIAN_MODEL_TTS` 两键；本地 .env 同步填入便宜档默认值。
- **验证**：后端 `mvn -T 1C clean verify` 126 测试全绿（+1：chatWithModel 密钥缺失回退脚本模型）；fat jar 重启冒烟——SMTP 真实发信 ✓、图片讲解 qwen3-vl-flash 真实调用 ✓、TTS 网关不可用 503 优雅降级 ✓；冒烟临时账号已清理。

## 2026/8/27 10:07 · ZCode（前端字阶整体提一档）

> 影响文档：docs/frontend/FRONTEND.md（§10.1 Design Token 字阶）、frontend/xlumen-frontend-blog/src/styles/tokens.css、frontend/xlumen-frontend-admin/src/styles/tokens.css · 决策摘要：无

- **背景**：用户反馈 1920×1080 下字体偏小。核实前端对分辨率无任何约束/缩放（viewport 标准写法，媒体查询仅按宽度做布局堆叠，字号全固定 px），属 V2 设计基线紧凑 → 决定整体提一档。
- **字阶 token 更新（双端同步）**：H1 38→40 / H2 24→26 / Title 18→20 / 正文 14→15 / Caption 12→13；`element-theme.css` 补 EP 字体变量（--el-font-size-base 挂 --xl-fs-body，medium 16 / large 18 / small 13 / extra-small 12），Element Plus 组件与页面正文同步放大。
- **全站硬编码字号收敛**：脚本按一档阶梯批量替换双端 src 全部 `font-size`（11→12、12→var(--xl-fs-caption)、13→14、14→var(--xl-fs-body)、15→16、16→18、17→18、18→20、20→22、22→24、24→26、26→28、28→30、30→32、38→40、40→42），共 40 文件；12/14px 收敛为 token 引用。层阶不变，仅整体放大；文章正文 .markdown-body 15→16px。
- **验证**：双端 typecheck / lint / build / vitest 全绿。

## 2026/8/27 · ZCode（全功能黑盒测试 + 修复 3 项：AI 写作流断裂 / 详情页右轨交互不同步）

> 影响文档：docs/frontend/FRONTEND.md（无·仅前端模块内修复）、docs/ai/QA.md（工具选择沉淀） · 决策摘要：无

- **测试范围**：按 docs/ai/QA.md 第 5 节模块顺序以内置浏览器（agent-browser，1080p 视口）全功能巡检——身份/多租户、博客公开阅读、互动反馈、内容管理、知识库体系（建库/目录）、审核与发布（AI 审核弹窗→发布成功）、AI 对话（流式/Agent 工具循环/追问）、AI 写作、AI 摘要、管理后台（空间/模型/AI 追踪/动态/审计）、多用户可见性、回收站/发布管理页。全程使用 qa_ft_20260827 / qa_ft2_20260827 新建测试账号。
- **BUG-031 · AI 写作结果流断裂（404/400，已修复）**：`writing.ts` 任务 URL 用 `/ai/tasks/{id}`，而 TaskController 挂 `/api/v1/tasks`（无 `/api/v1/ai/tasks`），导致 SSE `/ai/tasks/{id}/events`、`GET /ai/tasks/{id}`、`/ai/tasks/{id}/retry` 全部 404。又因 `WritingController` 返回 `ApiResponse<Long>`（原始 taskId 字符串），`submitWriting` 取 `result.taskId` 得 `undefined`，SSE 再报 400。修复：3 处 URL 改 `/tasks/{taskId}`，`submitWriting` 改 `String(result)`。
- **BUG-031 附带 · AI 写作展示原始 JSON（已修复）**：SSE 链路曾因 URL 错误未连通，展示解析缺陷被掩盖。修复 `AiWritePage.handleEvent` chunk 分支从 `streamText += event.data`（原始 JSON）改为 `JSON.parse(event.data).content`。修复后 AI 写作流式输出清晰 Markdown（多章长文）。
- **BUG-032 · 详情页右轨赞/踩/收藏不同步（已修复）**：同篇知识存在正文操作带 + 右侧操作轨两处 ReactionBar/FavoriteButton，组件仅 watch `knowledgeId`，一处状态变化不回传 props，右轨计数/高亮滞后（如正文「已赞 1」、右轨仍「赞 0」）。修复：ReactionBar/FavoriteButton 增加 props（initial/initialReaction/count）watch 在非 pending 时回同步；ReactionBar 的 `update:counts` 载荷扩展携带 `reaction`，父组件 `onCountsChange` 同步 `knowledge.liked`。验证：点赞/收藏正文后右轨即时一致。
- **遗留事项（非缺陷）**：① 存量已发布知识（如 qodet_test 的「JDK 21 + Spring Boot 3」）RAG 索引为 null，AI 对话检索未命中——属 BUG-004 已知「Milvus 就绪后存量 reindex 补跑」缺口，非新缺陷；新发布知识已正常建索引（chunkCount=1/ACTIVE）。② `/knowledge-bases` 页「全平台公开知识库聚合将在 V2 提供」文案与 V2 已交付状态不符，属契约文案，留待用户决策是否更新。③ 测试数据（qa_ft/q a_ft2 账号、qa测试公开库/qa私有库、API创建测试知识/qa私有库测试知识、若干评论）保留在 xlumen_dev，命名带 qa_ 前缀，清理需用户明确要求。
- **验证**：博客 `vue-tsc --noEmit`、`eslint`（0 errors）、`vite build`、`vitest run`（7 文件 19 用例）全绿；浏览器复测 AI 写作流式 Markdown、右轨交互同步均通过。



> 影响文档：docs/frontend/FRONTEND.md（§10.1 Design Token 值） · 决策摘要：D19（UI 统一入口/品牌色突出）

- **设计系统**：新增品牌组件 `XlLogo`（SVG 双光柱+四角星+隐藏X+字标）、`InitialAvatar`（首字圆头像）、`SegmentedControl`（关键词/向量语义/问小光三态）；`tokens.css` 对齐 V2 调色板（Ink #162033、Success #2E8B68、Warning #B7791F、Danger #C94B50，并把 Success 与 AI Teal 拆开）并新增字体层级/容器/头部高度 token；`index.css` 全局标题层级与 `.xl-container` 版心；`element-theme.css` 映射新语义色。
- **全局壳**：博客 64px 吸顶头部（Logo / 发现·知识库·动态·创作中心·AI小光+Teal星 / 搜索胶囊 / 主题图标仅视觉·固定浅色 / 通知铃 / 写知识 CTA / 头像）；后台左固定侧栏品牌化（XlLogo + 用户名/登出）；修复 `/studio/knowledge` 被 knowledge-list 与 index-status 重复注册，RAG 索引页拆到 `/studio/index-status`。
- **博客页面**（按 V2 规范重排版式，功能/数据/交互真值不变）：B01 首页（AI 光带入口 + 探索轨 + 细分隔线知识流）、B02 知识详情（阅读中轴：目录/正文/右操作轨 + 面包屑 + AI 摘要/导读）、B00 AI 小光（会话/对话/本轮过程三栏）、B09 工作台、B08 编辑器、B11 AI 写作、B12 审核中心、B13 发布管理、B03 搜索三态、B16 知识库发现、B20 知识库详情、B24 知识地图、B25 动态、B26 关于、B23 收藏、B06 登录、B10 知识管理、B22 我的知识库、B21 回收站、B14 RAG 索引状态。
- **后台页面**：A00 登录（44/56 秩序化分栏）、A01 空间设置、A02 模型与 Prompt 配置（配置表 + Prompt 侧栏检查器）、A03 AI 调用追踪（统计带 + 吸顶表头）、A04 站点更新日志、A05 审计日志（日志表 + 详情检查器）。
- **验证**：双端 `pnpm typecheck`、`pnpm lint`（0 errors）、`pnpm build` 全绿；用内置浏览器 1080p 抽查首页/AI 对话/关于/搜索/知识库，版式符合 V2 规范。后端未启动，数据填充态未逐一截图（以 UI 规范 + 图为准）。遗留：G01-G05 全局浮层（悬浮小光/通知/导览/纠错/空态）样式待统一；暗色未启用（固定浅色，主题图标仅视觉）。

## 2026/8/26 · ZCode（V2 全量实施交付：批次 0~5，28 项功能 + 工程项 IDEA-027）

> 影响文档：docs/product/PRODUCT.md（V2 全部交付状态）、docs/ai/STATUS.md §3/§4/§5/§7 · 决策摘要：D19~D27 全部落地（D20 Spring AI 迁移为旧）

- **批次 0（chore，f21daf5）**：IDEA-027 注释编号清理——Python 脚本清除 Java/TS/Vue/CSS/XML/yml/SQL(--) 注释中的 F-/IDEA-/BUG-/OPT- 编号（保留 Dxx 决策号；SQL DDL COMMENT 字符串不动），375 文件 787 行 + 8 处用户可见文案 + BUG-4/8 短编号兜底；验证：后端 8 模块 SUCCESS、双前端 typecheck。
- **批次 1（feat，7a07256）**：AI 基建四件套——F-0506 Prompt 后台动态管理（ai_scene_config.prompt 列，WRITING 四槽位 JSON，PromptResolver 表优先回退常量，admin 编辑）；F-0505 AI 调用追踪（ai_call_log 表 + ChatRuntimeImpl 四入口统一埋点：模型/Prompt 哈希/Token/费用/耗时/成败/降级，admin AI Trace 页）；F-0504 配额管理（daily_quota + Redis 预占/释放，超限 429，fail-open）；F-1304 审核事件解耦（pub_review ai_status/ai_error 镜像 + publishing 监听 AiTaskCompletedEvent 写回，读取镜像优先实时兜底，状态机闸门原样）。迁移 90/91 幂等已应用；93 测试全绿 + 真实模型冒烟（tokens/费用落库）。
- **批次 2（feat，1eb200e）**：检索双线——F-0217 语义向量（public 搜索 mode=semantic，Embedding→Milvus 可见库过滤→段落聚合卡片（相关度/命中段数/首锚点），不可用自动回退 LIKE）；F-0216 问搜一体（SearchPage 三态：关键词/向量语义/问小光，问小光复用 chat SSE + 引用溯源）；reindex-all 全量补跑端点（存量 14 篇已补）；**Milvus 本环境三连坑根治**（REST 仅快速建集→建集最小化；大雪花 ID 超 float64 精度失真→动态字段字符串化 + 过滤器字符串字面量；search 响应扁平 vs 嵌套→兼容解析 + postJson 校验响应体 code 消除静默降级）；98 测试全绿。
- **批次 3（feat，2d4534c）**：写作三件套——F-0603 RAG 增强写作（写作前检索注入参考资料 [n] 标注 + results.references 证据，XLUMEN_WRITING_RAG_ENABLED 默认开，检索失败降级）；F-0606 辅助编辑（POST /ai/assist：continue/polish/titles/spellfix + MarkdownEditor AI 工具条）；F-0607 代码解读（assist code_explain/bug/test + highlight.js + 详情页代码块按钮弹窗）；100 测试全绿，前端新增依赖 highlight.js。
- **批次 4（feat，d03bd21）**：对话组八项——F-0703 访客问答（/public/chat 匿名 SSE + IP 小时限流 60，前端全站悬浮小光）；F-0709 对话长期记忆（chat_memory 主题记忆注入 QA 提示词，上限 20 条）；F-0706 相关追问（SSE followups 事件 + chips 点击即问 + 落知识缺口池）；F-1103 知识缺口（零命中自动记录 ai_question_gap + 清单分页/置已处理）；F-0704 多文档对比（knowledgeIds 透传，单篇精确限定）；F-0707 问答转草稿（回答卡存草稿：正文+参考来源）；F-0705 库洞察能力（assist kb_insight/kb_cluster）+ KB 详情页「AI 库洞察」卡；F-1005 评论 @小光（检索摘要回复 is_ai+citations、5 分钟限流、跨空间可见性修复、状态机零影响）；迁移 92/93 已应用；125 测试全绿；冒烟：访客匿名问答/追问事件/记忆落库/缺口清单/@小光 回复带真实引用（0.67 分）。
- **批次 5（feat，44a0594 + d7caa85）**：前台增值十二项——F-0105 忘记密码（starter-mail + forgot/reset 验证码 Redis 10 分钟/错 5 次作废，SMTP 未配置开发模式日志输出验证码，上线仅需 .env 补 MAIL_*）；F-0218 术语解释（/ai/term-explain 访客公开 + 24h 缓存 + 详情页选词气泡）；F-0204 相关推荐（同库标签交集优先 + 语义兜底）；F-0806 听知识（/public/knowledge/{id}/speech 百炼 qwen-tts 兼容端点，环境不可用 501 降级 + 前端音频条）；F-0809 图片 AI 讲解（assist image_explain，Spring AI Media 多模态，模型需支持视觉，默认模型不可见图时提示）；F-0219 图文导读（SUMMARY 提示词扩展 guide 要点 + 详情页 AI 导读折叠卡）；F-0206 SEO（sitemap.xml/robots.txt 公开端点，15 URL 冒烟）；F-0222 站点更新日志（plt_changelog 表 + admin CRUD + 前台时间线页，发布/草稿联动）；F-0221 知识地图页（assist kb_cluster 聚类，访客按库静态分组回退）；F-0220 站点导游（首次访问 4 步静态导览）；F-1306 深浅色主题（双端 tokens/element-theme dark 块 + 切换按钮 + localStorage/系统偏好）；修复：ai_call_log.workspace_id 可空（访客追踪）、MailService ObjectProvider 无 SMTP 启动容错。
- **总结**：V2 28 项功能 + 工程项 IDEA-027 全部交付；后端 125 测试全绿（JUnit5），博客 19 + 后台 2 前端测试全部通过，双端 typecheck/lint 0 errors；真实模型 API 冒烟覆盖全部交互链路（注册/登录/对话/检索/追踪/配额/评论/assist/忘记密码/更新日志）；Milvus 检索线落地为真实向量检索；冒烟账号/临时凭据（admin/qoder_test 密码交换）已全部恢复原状并清理。遗留说明：F-0806 TTS 需环境支持 qwen-tts 或换供应商；F-0809 需在模型配置改用视觉模型；SMTP 账号待用户提供后填 .env 启用真实发件。


## 2026/8/26 11:01 · ZCode（启动 V2 实施：D27 落档 + 开工约束确认，V2 冻结 28 项功能）

> 影响文档：docs/product/PRODUCT.md（F-0605/F-0609 两行+统计行+阶段标记说明 D27）、docs/ai/STATUS.md（§4/§5/§7/§8 D27）、docs/ai/IDEAS.md（IDEA-010 阶段注记）· 决策摘要：D27

用户拍板开工约束（全对话任务，V2 28 项功能 + 工程项全部实施，执行顺序由 AI 合理安排）：

- **实施**：一个对话任务全部实施；每批完成后自测（后端 mvn test / 前端 typecheck+lint / GUI 截图）；按批次 conventional commit
- **检索**：F-0217 语义向量 Embedding 先走云端百炼
- **UI**：不做整体重构，但 AI 能力统一入口/标识（AI 小光悬浮按钮、AI 能力区域品牌色高亮），界面突出 AI
- **依赖**：允许按需引入新开源库（引入时说明理由），优先复用现有技术栈
- **模型**：.env key 已配置，V2 新功能开发态默认走真实模型（ScriptedChatModel 仍作兜底）

**D27 本体**：F-0609 双栏对照改写、F-0605 写作任务可恢复移 V3，V2 30→28 项功能、V3 27→29 项。本批为 V2 定版收尾（自 D21 起全链 D21~D27），随后按批次开始实现。

## 2026/8/26 10:36 · ZCode（D26 搭车 5 项并入 V2：V2 定版 30 项功能 + 工程项，全部实施）

> 影响文档：docs/product/PRODUCT.md（新增 F-0221/F-0222/F-0506/F-0609/F-0709 五行 + 统计行 106 项 + 阶段标记说明 D26）、docs/ai/IDEAS.md（5 项状态→已采纳）、docs/ai/STATUS.md（§4/§5/§7/§8 D26）· 决策摘要：D26

用户拍板将 5 项搭车/改版想法并入 V2 并登记总表（均属产品功能，占 F 编号）：

- **F-0221 知识地图页**（IDEA-015，博客前台）：AI 主题聚类生成可交互知识地图，与 F-0705 库洞察共用聚类能力，访客可见
- **F-0222 站点更新日志**（IDEA-020 改版，博客前台）：原「站点月度更新志」按用户要求改为**不固定月度**的更新日志——前台展示页 + admin 后台动态编辑发布
- **F-0506 Prompt 后台动态管理**（IDEA-026，AI 核心引擎）：prompt 从代码常量改为 ai_scene_config 场景化存储，admin 按场景编辑，缺省回退 PromptTemplates 常量（8-25 就绪的默认值落点）
- **F-0609 双栏对照改写**（IDEA-010，AI 内容创作）：编辑器选中段落→右侧 AI 改写建议→接受才替换，与 F-0606 辅助编辑同一入口
- **F-0709 对话长期记忆**（IDEA-014，AI 对话）：按用户持久化对话主题记忆，与对话组 F-0703/F-0706/F-0707 同一改造窗口

**V2 定版最终形态**：V2 30 项功能 + 工程项 IDEA-027（D24），全部实施；V3 27 项（含暂缓 6）；总表 101→106 项（MVP 49 / V2 30 / V3 27）。其余确认项：F-0105 SMTP 先写代码、账号后续提供；门面内容（公开知识/架构篇/演示账号）先不生成，后续另行安排；本批文档提交后执行 V2。纯文档变更，未动代码。

## 2026/8/26 10:23 · ZCode（D25 检索定案：MySQL 关键词 + 语义向量双线，V2 冻结 25 项功能）

> 影响文档：docs/product/PRODUCT.md（F-0217/F-1305 两行+统计行+阶段标记说明）、docs/ai/STATUS.md（§4/§5/§7/§8 D25）· 决策摘要：D25

用户拍板检索线最终方案（结束 F-0217 vs F-1305 的取舍待裁决）：**MySQL 关键词 + 语义向量双线并存**。

- **页面搜索默认 MySQL 关键词**（现成 LIKE 能力，全站零费），**登录用户可切换向量语义检索**（F-0217），访客/未登录显示提示引导登录
- **F-1305 ES 全文搜索移 V3**（不接入 ES，作关键词升级线留待后续）
- **对话 RAG 检索（knowledge.search）统一走 MySQL 关键词线**（对话高频零 Embedding 调用；现状对话工具走向量线，实施时改为关键词并回归 F-0708 工具链路）
- 基建前置更新：Milvus 已装 → 换真检索（`MilvusVectorStore` 代码已存在，配置对通即可）+ 存量 reindex 补跑（BUG-004 收尾）；F-0105 需 SMTP 邮件服务

**V2 最终定版：25 项功能 + 工程项 IDEA-027（D24），首批 15 项不变**；V3 27 项（含暂缓 6）；检索线「待裁决」标注已全部清除。纯文档变更，未动代码。

## 2026/8/26 09:58 · ZCode（D24：IDEA-027 代码注释清理纳入 V2 批次）

> 影响文档：docs/ai/IDEAS.md（IDEA-027 状态→已采纳）、docs/ai/STATUS.md（§5 V2 行 + §8 D24）· 决策摘要：D24

用户拍板将 IDEA-027「代码注释去除功能编号」作为工程项加入 V2 批次：属非产品功能，不占 PRODUCT 功能总表编号（仿 OPT 处理，转 STATUS §5 V2 行）；存量范围 Java 注释约 600 行（F-xxxx 513 / IDEA-xxx 57 / BUG-xxx 30，跨 294 文件）+ 前端 TS/Vue 66 文件；执行方式（一次性脚本批量 + git diff 复核，或仅新代码起执行）待批次开工确定。V2 功能 26 项 + 工程项 1 项。纯文档变更，未动代码。

## 2026/8/26 09:26 · ZCode（D23 冻结：F-0906 随保鲜组移 V3，V2 名单定版 26 项）

> 影响文档：docs/product/PRODUCT.md（§5 F-0906 行+统计行+阶段标记说明）、docs/ai/STATUS.md（§4/§5/§7/§8 D23）· 决策摘要：D23

用户拍板 D22 遗留的连带项：F-0906 下架/回滚采纳推荐方案随「知识保鲜生命周期组」移 V3（回滚能力依赖 F-0303 版本管理，与版本管理同批做）。V2 27→26 项、V3 25→26 项；「发布无下架」流程缺口（FLOW 清单）对应修复随后延。

**V2 名单至此冻结（26 项）**，剩余唯一待裁决：F-0217 语义搜索（向量/Milvus）与 F-1305 全文搜索（ES）两条检索升级线是否同期做（STATUS §5 已标注）。纯文档变更，未动代码。

## 2026/8/26 08:46 · ZCode（D22 用户微调：保鲜组移 V3 + F-0105 忘记密码换入 V2）

> 影响文档：docs/product/PRODUCT.md（§5 四行阶段调整+统计行+阶段标记说明）、docs/ai/STATUS.md（§4/§5/§7/§8 D22）· 决策摘要：D22

用户在 V2 开发定版后再一次人工调整（覆盖 D21 评分结论的一部分）：

**移回 V3（3 项）**：F-1102 时效检测、F-0303 版本管理、F-1105 旧知识更新闭环——三项构成「知识保鲜生命周期组」，整体后移，V2 聚焦访客演示与自用高频。

**换入 V2（1 项）**：F-0105 忘记密码（邮件发送验证码形式）——需新增 SMTP 邮件服务依赖（.env 配 `XLUMEN_` 前缀邮件项），记入 STATUS §5 V2 基建前置。

**范围结果**：V2 29→27 项、V3 23→25 项；首批 15 项与 6 项「暂缓」不变；D21 其余换档（F-0220/F-0705/F-1305 换入，F-0304/F-0210 换出）保持。

**连带待裁决（记入 STATUS §5/§8 D22）**：F-0906 下架/回滚依赖 F-0303 版本管理，F-0303 移 V3 后 F-0906 的「回滚」基础缺失——二选一：F-0906 随保鲜组移 V3（回滚与版本管理同批做），或 F-0906 留 V2 但范围收缩为「下架 + 审计」、回滚依赖 V3 版本管理。纯文档变更，未动代码。

## 2026/8/26 01:44 · ZCode（V2 开发前评审：D21 换档重评，V2 27→29 项）

> 影响文档：docs/product/PRODUCT.md（§5 总表 6 行阶段调整+统计行+阶段标记说明）、docs/ai/STATUS.md（§4/§5/§7/§8 D21）· 决策摘要：D21（V2/V3 换档重评）

V2 开发启动前，用户将 D19 评分公式的 I 系数由 1.2 调至 2 并要求按高分重排（本次为新评——D19 只归档了公式与结论，未存逐项分值）。

**公式与口径**：总分 = P（个人实用 1~5）+ I×2（吸引面试官 1~5，含「面试官以访客身份直接开 URL 能否看见」）+ 成本（低 +1 / 中、高 +0）；换档规则：换入线 ≥10.5 且成本低/中、换出线 <7.0，中间区间维持 D19 原档（避免全量洗牌）。交叉验证：独立重评高分区与 D19「首批 15 项」完全重合。

**换档结果（6 项）**：F-0220 站点 AI 导游、F-1305 全文搜索（ES）、F-0705 知识库洞察、F-1105 旧知识更新闭环移入 V2；F-0304 定时发布、F-0210 URL slug 移出至 V3。V2 27→29 项、V3 25→23 项；首批 15 项与 6 项「暂缓」均不变。

**52 项评分明细归档**（下次重排直接复用；P=个人实用 / I=面试官可见 / C=成本加成，总分=P+I×2+C）：

V2（29 项，按总分降序）：F-0703 访客 AI 助手 4.5/4/1=13.5 · F-0219 图文导读 4.5/4/1=13.5 · F-0216 问搜一体 4/4/1=13 · F-0706 相关追问 3.5/4/1=12.5 · F-0220 AI 导游⬆ 2.5/4.5/1=12.5 · F-0217 语义搜索 3/4.5/0=12 · F-0809 图片讲解 4/4/0=12 · F-1305 ES 全文搜索⬆ 3/4.5/0=12 · F-0603 增强写作 3.5/4/0=11.5 · F-0606 辅助编辑 4.5/3.5/0=11.5 · F-0218 术语悬浮 3.5/4/0=11.5 · F-0705 库洞察⬆ 3.5/4/0=11.5 · F-0607 代码解读 4/3.5/0=11 · F-0707 问答转草稿 4/3.5/0=11 · F-1005 评论 @小光 3/4/0=11 · F-1103 缺口分析 4/3.5/0=11 · F-1306 主题切换 3/4/0=11 · F-1105 更新闭环⬆ 3.5/3.5/0=10.5 · F-0505 AI Trace 2.5/3.5/0=9.5 · F-1102 时效检测 3.5/3/0=9.5 · F-0204 相关推荐 3/3/0=9 · F-0704 多文档对比 3/3/0=9 · F-0806 TTS 3/3/0=9 · F-0206 SEO 1.5/3.5/0=8.5 · F-0504 配额管理 1.5/3.5/0=8.5 · F-1304 事件解耦 1.5/3.5/0=8.5 · F-0303 版本管理 3/1.5/1=7 · F-0605 任务可恢复 3/1.5/1=7 · F-0906 下架/回滚 3/1.5/1=7。

V3（23 项）：F-0804 知识配图 2/4/0=10（临界，成本高不换入，多模态故事位已由 F-0809 占住） · F-0807 分享卡片 2/3.5/1=10（临界不换入） · F-0805 自动标签 3/3/0=9 · F-1104 更新建议 2.5/3/0=8.5 · F-0209 作者主页 1.5/2.5/1=7.5 · F-1101 访问统计 3/2.5/0=7.5 · F-0602 大纲生成 2/2.5/1=8（与已交付 F-0608 多步工作流重叠） · F-0803 翻译 2.5/3/0=8 · F-0205 RSS 2/2/1=7 · F-0207 归档 2/2/1=7 · F-0310 置顶 3/1/1=7 · F-0311 回收站批量 3/1/1=7 · F-0105 忘记密码 2/1.5/1=6.5 · F-0306 图片附件 2.5/2/0=6.5（中成本） · F-1004 通知系统 2.5/2/0=6.5 · F-0210 URL slug⬇ 2/1.5/1=6 · F-0304 定时发布⬇ 2/1.5/0=5 · 暂缓 6 项：F-1002 纠错归类 1.5/2.5/0=6.5、F-1203 运行监控 1.5/2.5/0=6.5（与 F-0505 重叠）、F-0106 私有库授权 1.5/2/0=5.5、F-0211 库关注 1.5/2/0=5.5、F-1003 评论助手 1/2/0=5、F-1204 敏感检测 1/1.5/0=4。

**待裁决（记入 STATUS §5 V2 行）**：F-0217（向量/Milvus）与 F-1305（ES）两条检索升级线是否同期做——绑定 Milvus 去留决策；F-0705 依赖 embedding 基建同受牵连。纯文档变更，未动代码。

## 2026/8/25 17:30 · ZCode（AI 代码级去重 A-F 批：Prompt/JSON/任务VO/SSE 事件/前端工具轨迹）

> 影响文档：docs/ai/STATUS.md（§7 最近变更）、docs/ai/CHANGELOG.md · 决策摘要：无（承接上一批「AI 架构收敛」后的机械去重，REST/SSE 对外协议形状不变）

按用户「统一处理上面的改动」对扫描出的 A~F 六项落地的代码级去重（G 统一 SSE 底座属结构性改造，另行评审）。

**F. Prompt 集中成常量类**：新增 `xlumen-ai .../prompt/PromptTemplates`，9 条 System 提示词从 4 文件收口（WritingExecutor 4 / ReviewExecutor 2 / EnhanceServiceImpl 2 / ChatServiceImpl 1），按 AiScene 场景命名，含 `{{MAX}}`/`{{TITLE}}` 占位符模板保留由使用方替换；IDEA-026（Prompt 后台动态管理）后续落点为场景配置化时默认值来源。

**A. JSON 提取统一**：新增 `.../util/AiJson`（extractObject/extractArrayText/extractArray：围栏剥离 + 花/方括号截取），WritingExecutor（extractJsonObject/extractJsonArray）/ReviewExecutor（extractJsonArray）/EnhanceServiceImpl（parseJson）三份近似实现收敛；Enhance「非法 JSON 抛 SERVICE_UNAVAILABLE」语义保留（其余按容错返回 null）。

**B. 任务 VO 去重**：删除 `TaskVO`，`GET /api/v1/tasks/{taskId}` 改返跨模块稳定类型 `TaskResultVO`（REST 字段 `id`→`taskId`）；blog `writing.ts` RawTask/fetchWritingTask 同步读取 taskId（对外归一化接口 `AiWritingTask` 不变）。

**C. SSE 事件名单一**：后端新增 `.../util/SseEventName`（chunk/progress/done/error/citation/tool），SseService/ChatServiceImpl/TaskController 字面量全替换；前端新增 `blog/.../ai/utils/sseEvent.ts` 同名常量，chat.ts switch 与 AiWritePage.handleEvent 改用。

**D. 前端工具轨迹去重**：ChatPage 与 KnowledgeQaDialog 逐字重复的 `activeTools`/`doneTools` 收口到 `blog/.../chat/utils/toolPanel.ts`（新增 2 条单测锁定）。

**E. parse* 源核结论**：chat.parseCitations/parseToolCalls/parseToolEvent 与 review.parseReviewIssues 各自解析不同数据形态、本已单源，无跨文件复制，无可独立去重点；并入 C/D 落地。

验证：`mvn -pl xlumen-boot -am test` BUILD SUCCESS（8 模块，ai 24 / publishing 44 等全绿）；blog typecheck 过、vitest 3 文件 6 测试过（含新增 toolPanel 2 条）、eslint 0 errors（存量 CRLF 警告不计）。

## 2026/8/25 16:51 · ZCode（AI 架构收敛：双轨合单轨 + EMBEDDING 删虚 + 删 EnhancePanel）

> 影响文档：docs/ai/STATUS.md（§3 能力基线/§7 最近变更）、docs/ai/CHANGELOG.md、docs/backend/BACKEND.md（§14 Agent 段落）、docs/product/PRODUCT.md（F-0502/F-0604/F-0608/F-0708）、backend/xlumen-server/sql/init/30_ai.sql、sql/migration/89_ai_scene_single_track.sql · 决策摘要：无（承接 IDEA-025/D20 落地形态收敛）

按用户拍板的三项执行（无需迁移成本，主代理亲改核心 + 子代理并行删前端孤儿模块）。

**① 双轨合单轨（QA/写作/审校一律走 Agent 路径）**：

- QA（ChatServiceImpl）：删除固定 RAG 路径 `runFixedRag`/`retrieve`/`buildSystemPrompt`/`SYSTEM_PROMPT` 与 `RETRIEVAL_TOP_K`、未用的 `KnowledgeApi` 注入；RAG 检索收敛到 `knowledge.search` 工具（引用只聚合模型实际命中，`citationCollector`）；`runStream` 去掉 agent 分支直接 `runAgent`。
- 写作（WritingExecutor）：删除 `singlePass` 单次生成；主链路失败（大纲解析失败/章节超限/单章生成失败）→ `ctx.fail` 任务 FAILED；增强失败（自审失败/修订失败）→ 跳过修订交付初稿。
- 审校（ReviewExecutor）：删除普通 `chat()` 与旧 `SYSTEM_PROMPT`（并入工具核对版提示词）；统一走 `chatWithTools` 事实核对（知识库证据引用，Schema 不变）。
- 移除场景级开关 `ai_scene_config.agent_enabled`：`SceneModel`/`SceneConfigService`/`SceneConfigServiceImpl`/`AiSceneConfigEntity`/`ModelConfigVO`/`ModelConfigUpdateDTO`/`ModelConfigController` 全删 agentEnabled；admin 模型配置页删除「Agent 模式」列与 `model.ts` agentEnabled 字段；存量库列清理见新 migration `89_ai_scene_single_track.sql`（删列 + 清 EMBEDDING 行，幂等），30_ai.sql 同步删列。

**② EMBEDDING 删虚**：移除 `AiScene.EMBEDDING` 枚举、`AiProperties.bailianModelEmbedding`、`SceneConfigServiceImpl` 的 EMBEDDING case；向量化统一由 knowledge 模块 `KnowledgeAiProperties` 读 `.env` 的 `XLUMEN_BAILIAN_MODEL_EMBEDDING`（本就生效，未动）；admin 模型配置页移除 Embedding 行。

**③ 删 EnhancePanel 孤儿模块**（子代理并行，blog `src/modules/ai-enhance/` 261 行）：`EnhancePanel.vue`/`enhance.ts`/`index.ts` + 空 `__tests__`；确认无任何页面引用，blog typecheck 通过；后端 `/ai/enhance` 端点与 `EnhanceServiceImpl.generateAndStoreSummary`（F-0808 自动摘要复用）保留。

验证：后端 `xlumen-ai` 24 测试全绿（WritingExecutorTest 5 / ReviewExecutorTest 4 / ChatRuntimeImplTest 3 等）、`mvn -pl xlumen-ai -am test` BUILD SUCCESS、admin typecheck 通过、代码零 EMBEDDING·agentEnabled·singlePass 残留（grep 核验）。

## 2026/8/24 · ZCode（审核中心「发布」409 修复：发布门禁人工/AI 双轨 + 幂等前置）

> 影响文档：docs/ai/CHANGELOG.md、docs/ai/BUGS.md（清单历史） · 决策摘要：无

用户审核中心点「发布」报 409（POST /api/v1/releases）。根因两条：

- **人工审核路径被 AI 门禁误杀**：ReleaseServiceImpl.hasPassedAutoReview 强制 `pub_review.ai_task_id` 非空且 `ai_result_json` 非空——审核中心人工提交（auto_mode=0，无 AI 任务）通过后点「发布」必 409「发布前必须完成自动 AI 审核」；人工通过（approve）本就是有效门禁。
- **自动路径重复点击**：auto 审核发布链路已建发布记录后，再点「发布」在幂等返回前先被「仅审核通过的知识可发布」拦截（状态 PUBLISHED），同样 409。

修复（ReleaseServiceImpl）：

- 发布门禁放宽为「存在该版本 APPROVED 审核记录」：人工通过（无 AI 结果）即放行；AI 结果非空仍校验无 error 级问题（防直调绕过兜底）。
- 幂等返回（同 ws/knowledgeId/version 已有发布记录）提前到状态检查之前：已发布重复点「发布」直接返回既有记录，不再 409。
- 新增 ReleaseServiceImplTest 4 条（人工审核可发布 / 已发布幂等返回 / 无审核拒绝 / AI error 拦截）。

验证：全仓 `mvn -T 1C clean verify` 全绿（79 测试，publishing 模块 44 含新增 4 条）。


> 影响文档：docs/design/spring-ai-migration.md（已随实施删除）、docs/ai/STATUS.md（OPT-2 完成，D20 入 §8） · 决策摘要：D20

用户拍板「不分批、直接全量替换」，AI 实现方式整体迁移 Spring AI 2.0.1（不引 Alibaba、不降 Boot/JDK）；api-docs/聊天/健康冒烟全通，后端 75 测试全绿。

- **依赖与版本**：父 POM import `spring-ai-bom:2.0.1`（与 Boot 4.1.0 同版）；xlumen-ai/xlumen-knowledge 引 `spring-ai-starter-model-openai` 并排除 openai-java-core 传递的非 jakarta `swagger-annotations`；父 BOM 钉 `swagger-annotations-jakarta:2.2.52`（修复 /v3/api-docs NoSuchMethodError：openai sdk 传入 2.2.31 与 springdoc swagger-core 2.2.52 同包冲突）；application.yml 六个 `spring.ai.model.*: none` 全关自动配置（全部 @ConditionalOnProperty matchIfMissing）。
- **新架构（Spring AI 形态）**：`ChatRuntime`（替代 ModelGateway/AgentRunner：场景解析/熔断/缺密钥回退 ScriptedChatModel；ChatClient + ToolCallingAdvisor 自动多轮工具循环；逐请求 options 设 model/temperature/maxTokens/toolCallbacks/toolContext）；`ScriptedChatModel`（替代 MockProvider，脚本队列 ChatResponse）；`ToolCallbackAdapter`（AgentTool→ToolCallback，预算上限/超时/截断/信封 + 向 ToolEventSink 实时推 SSE 事件与落库配对）；`ToolEventSink/ToolRun/ToolPair/ToolEventPayload`（轨迹收集）；knowledge `EmbeddingServiceImpl` 换 `OpenAiEmbeddingModel`（builder 依 options 自建 SDK 客户端，32 片/批语义不变）。
- **删除手写实现**：provider 包（OpenAICompatibleProvider 等 11 类）、agent 包（AgentRunnerImpl 等 6 类）、ToolRegistry、ModelGateway/Impl、`ModelGateway.embed` 死代码——合计约 1600 行；同步删 3 个旧协议/循环/工具测试（636 行）。
- **业务语义等价**：SSE 事件序列实测 chunk→citation→done 与迁移前一致；chat_message 逐调用配对（合成 tc-序号 tool_call_id，前端按 id 归并工具面板，历史回放配对修剪不变）；事件格式/表结构/.env/前端/SQL 零变化。
- **完整性复核（收尾）**：源码 grep 零旧类型残留；BACKEND.md §14 与 STATUS §3 能力基线同步为 Spring AI 形态描述（原 ModelGateway/MockProvider/AgentRunner 描述已替换）；业务 `ToolContext` 更名 `AgentToolContext` 消除与 Spring AI 同名遮蔽（Lombok 访问器链 11 文件机械替换，24 测试复绿）。
- **验证**：`mvn -T 1C clean verify` 全绿（75 测试，xlumen-ai 24 项含 ChatRuntime 工具循环集成测试与 ToolCallbackAdapter 单测）；fat jar（JDK25）启动 health UP / ping 200 / api-docs 200；Mock 模式注册→登录→`/chat/stream` 实测 SSE chunk×21→citation→done 全链路；真实供应商冒烟（chat/chatStream/Agent 循环/审校核对/连通性）待有密钥后执行。

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

## 2026/8/24 晚 · ZCode（发布链路异步化：发布后异步 AI 审核 + 站内通知，去掉轮询与阻塞弹窗）

> 影响文档：docs/product/PRODUCT.md、docs/backend/BACKEND.md、docs/ai/CHANGELOG.md、docs/ai/STATUS.md、sql/init/50_publishing.sql · 决策摘要：无（用户反馈发布确认弹窗样式差、期望「发布后异步审核+审核完通知」，据此改 F-0907 异步语义）

按用户反馈「点击发布应进入异步 AI 审核、审核完站内通知，而非阻塞等待弹窗」改造发布链路（IDEA-024 落地续）：

- **后端**：`pub_review` 加两列——`auto_mode TINYINT NOT NULL DEFAULT 0`（1=发布按钮提交，审核通过自动发布；0=审核中心人工）与 `auto_publish_at DATETIME NULL`（定时发布时间），`ReviewEntity`/`CreateReviewDTO`（+publishAt）/`ReviewController /auto` 同步；新增 `ReviewServiceImpl.finalizeAutoReview`（事件驱动最终化：COMPLETED 无 error→通过+自动发布（携带 auto_publish_at）；含 error/FAILED→驳回回草稿）与 `job/ReviewAutoPublishListener`（监听 `AiTaskCompletedEvent`，仅 auto_mode=1 且 PENDING，显式建立 WorkspaceContext、异常只记日志不抛出——不中断站内信监听）；`publishAfterAutoReview` 保留供审核中心人工路径。
- **前端**：编辑器与发布管理两处发布入口去掉轮询（fetchReview 循环）、高危 alert、软问题确认弹窗——提交审核即返回，提示「已提交 AI 审核，审核完成后将通过消息中心通知你」（强制审核关闭时提交即通过直接提示已发布）；`createAutoReview(knowledgeId, publishAt?)` 携带定时时间。
- **通知文案**：站内信「通过/未通过」文案中性化（自动模式已自动发布，不再提示「可前往发布」；未通过明确「已回退为草稿」）。
- **测试**：新增 `ReviewAutoPublishListenerTest`（5 条：筛选/忽略/异常不抛）与 `ReviewServiceImplAutoFinalizeTest`（4 条：通过自动发布（带 scheduledAt）/error 驳回回草稿/FAILED 驳回/非自动模式不动）；后端全量 89 测试全绿；blog typecheck/lint 0 errors。
- 存量库 DDL（`auto_mode`/`auto_publish_at` 两列）已在 xlumen_dev 本地执行（skill 确认流程）。
- **实时推送（续）**：按用户要求「后端主动推送 + 右上角弹窗提醒」（el-notification），新增用户级 SSE 端点 `GET /api/v1/notifications/stream`（`UserSseRegistry`：按 userId 注册、30s 心跳、30min 超时），通知创建后即时推送 `notification` 事件；前端 NotificationBell 建立 SSE 长连接（断线 5s 自动重连）、收到事件即 `ElNotification` 右上角弹窗（未通过/失败=error、通过=success、其余 info，点击跳转链接），未读数同步 +1，30s 轮询保留为兜底。**单向服务端推送用 SSE 即可，不引入 WebSocket**（项目无 WS 依赖，SSE 基建现成）。

## 2026/8/24 · ZCode（IDEA-025 AI Agent 工具调用 + IDEA-024 审核中心恢复/通用消息）

> 影响文档：docs/product/PRODUCT.md、docs/backend/BACKEND.md、docs/ai/STATUS.md、docs/ai/CHANGELOG.md、docs/ai/IDEAS.md、docs/ai/BUGS.md、sql/init/30_ai.sql、sql/init/70_chat.sql、sql/init/90_notification.sql（新增） · 决策摘要：无（IDEA-024/025 立项实施，SEO 批次按方案红线裁决砍掉）

### IDEA-025 · F-0708/F-0608 Agent 全链路改造（按方案实施完成，方案文档已删）

**协议层（xlumen-ai/service/provider）**：新增 `ToolSpec`/`ToolCall`/`ProviderChatResult`/`StreamCallback`；`ChatMessage` 加 toolCalls/toolCallId/name（role 扩 tool）；`ProviderChatRequest` 加 tools（空则行为与旧版一致）；`ModelProvider.chat()` 返回类型 String→ProviderChatResult、`chatStream()` 改 StreamCallback，`ModelGateway(+Impl)` 签名跟随；`OpenAICompatibleProvider` 四处改动（buildChatBody 序列化 tools/assistant.tool_calls/tool 消息；chat 解析 message.tool_calls+finish_reason；流式 delta.tool_calls 按 index 归并、finish_reason 暂存、流尾回调 onResult；parseDelta/mergeToolCallDelta 提为包级方法）；`MockProvider` 脚本化（Deque 出队，无脚本保持现状）；4 调用方适配（ReviewExecutor/EnhanceServiceImpl/WritingExecutor/ChatServiceImpl）。

**场景开关**：`ai_scene_config` 加 `agent_enabled TINYINT NOT NULL DEFAULT 0`（附存量 ALTER 注释）；`SceneModel`/`AiSceneConfigEntity`/`ModelConfigVO`/`ModelConfigUpdateDTO`/`SceneConfigService.update` 全链路加字段；admin 模型配置页「Agent 模式」开关（ModelConfigPage.vue + model.ts）。

**工具层（service/tool）**：`AgentTool`/`ToolContext`（workspaceId/userId/conversationId/kbId/citationCollector）/`ToolRegistry`（Bean 自动收集、specsFor(scene)、白名单/超时/截断/异常兜底全部返回错误信封）；3 个只读工具 knowledge.search（kbId 越权拦截、缺省按可见库全集、命中上报 citationCollector、ctx.kbId 锁定语义）/ knowledge.list / knowledge.getDirectoryTree；参数上限走 `XLUMEN_AGENT_*` 六项 .env 配置（AiProperties + .env.example）。

**编排与对话**：`AgentRunnerImpl` 多轮工具循环（流式/非流式双模式；每轮独立模型调用，符合「流式开始后不切换模型」约束；轮数用尽追加强制收尾轮去掉 tools；工具总次数截断给错误信封；citation 去重聚合）；`ChatServiceImpl` 双路径（agent_enabled=true 去预检索、Agent 版 system prompt、SSE 新增 `tool` 事件、中间轮 assistant/tool 行落库；false 原样保留）；`chat_message` 加 `tool_calls_json`/`tool_call_id`/`tool_name` 三列、`ChatMessageEntity/VO` 同步、HISTORY_LIMIT 10→30、历史重放配对修剪（孤儿 tool 剔除、toolCalls 无响应降级纯文本）。

**写作多步（F-0608）**：WritingExecutor agent 模式「大纲→分章流式（chunk 打章标）→REVIEWER 异源自审→修订」，中间产物（大纲/意见快照）随 resultJson 落库；大纲解析失败/章节超限（默认 8）/单章失败重试后仍失败/自审失败共四条降级路径全部回退单次生成；前端写作任务页加进度条 + 阶段文案（SSE progress 事件）。

**审校事实核对（F-0604 增强）**：REVIEWER 开关开时挂 knowledge.search 走 AgentRunner 非流式，独立轮数上限 `XLUMEN_REVIEWER_AGENT_MAX_ROUNDS=2`；`ReviewIssueVO` 加可选 evidenceKnowledgeId/evidenceQuote（Schema 兼容，旧结果合法）；工具失败 ≠ 任务失败（闸门语义回归）；前端发布确认弹窗/审核中心展示库内证据引用（有字段才显示）。

**测试**：新增 38 条单测（协议快照断言/流式分片归并/MockProvider 脚本、工具层越权与截断、AgentRunner 全循环/轮数上限/次数截断/工具失败不阻断、写作四条降级路径、审校 Schema 兼容与重试）；后端全量 `clean verify` 79 测试全绿；前端 typecheck 通过、lint 0 errors（CRLF 存量警告不计）。

### IDEA-024 · 审核中心恢复 + 通用消息

**新模块 xlumen-notification**（父 POM modules、dependencyManagement、boot POM 三处登记）：`noti_notification` 表（90_notification.sql，id/workspace_id/user_id/type/title/content/link/read_flag/created_at）；`NotificationEntity/Mapper/Service/Controller`（`GET /api/v1/notifications` 分页含未读数、`GET /unread-count`、`POST /{id}/read`、`POST /read-all`；归属校验 404、分页上限 100）；`AiTaskCompletedEvent`（xlumen-common/event）由 `AiTaskServiceImpl.complete/fail` 发布，`AiTaskCompletedListener` 监听 REVIEWER 场景生成站内信（COMPLETED 按 severity 汇总：存在 error→「AI 审核未通过」+高危条数，否则「AI 审核通过」+建议条数；FAILED→「AI 审核失败」+重试提示），链接指向审核中心。

**前端 blog**：新模块 `modules/notification`（api + NotificationBell：顶栏铃铛、未读角标 30s 轮询、下拉消息列表、单条/全部已读、点消息跳转 link）；App.vue 登录态挂铃铛；`/studio/review` redirect 改为 ReviewCenterPage 路由（审核中心 B12 恢复，FLOW-003 关闭）；工作台加「审核中心」卡片（第 4 入口）。

### 文档

PRODUCT 总表登记 F-0708/F-0608（统计 99→101，MVP 47→49）、F-0502 补 Agent 开关、F-0604 补事实核对说明；BACKEND §14 增「Agent 模式」与「通用站内消息」小节；STATUS §3/§4/§7 同步；IDEAS.md IDEA-024/025 标已实施；BUGS.md FLOW-003 标已解决；方案文档 `docs/design/agent-function-call.md` 随实施完成删除（git 历史可回溯）。

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

