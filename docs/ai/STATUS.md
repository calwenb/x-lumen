# xLumen 开发状态与交接文档（AI 必读）

> 更新日期：2026/8/22
> **本仓库专属**。
> 本仓库由多个 AI 工具协作开发，**本文件是唯一的上下文交接中心**：开始工作前通读，结束时更新。变更历史另见 [CHANGELOG.md](./CHANGELOG.md)。

## 1. 工作流（强制规则）

1. **阅读**：开工前先确认仓库路径为项目根目录；通读本文件 → 任务相关规范文档：产品 [PRODUCT](../product/PRODUCT.md)（唯一功能事实源，第 5 节总表）、后端 [BACKEND](../backend/BACKEND.md)、前端 [FRONTEND](../frontend/FRONTEND.md)、页面 [PROTOTYPE](../frontend/PROTOTYPE.md)、运行命令 [GLOBAL](../global/GLOBAL.md)、待修问题 [BUGS](./BUGS.md)（**仅按用户明确要求修复，不自动认领**）、浏览器测试 [QA](./QA.md)（AI 浏览器测试发起前必读）。
2. **认领**：在第 5 节待办中把任务标为 `已认领（AI 名）`，一次一项；未认领任务可能被其他 AI 同时开始，冲突以先提交者为准。
3. **实现与验证**：只做任务范围内变更；目录结构与功能范围不得偏离 docs（决策 D7）；跑通相关质量门禁（命令见 GLOBAL.md，后端编译需 JDK 25）。
4. **收尾**（不得省略）：更新本文件状态与待办；按 [CHANGELOG.md](./CHANGELOG.md) 头部模板追加一条（正文中超出归档期限的旧条目顺带移入 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md)）；代码与文档同一提交。
5. **最高规则**：文档必须与代码实际状态一致，禁止虚构进度；禁止顺手重构、记录密钥。

## 2. 当前里程碑

**MVP 全部 13 个里程碑（M01~M13）与知识平台化重构 KB-1~KB-6 均已交付并通过运行时验证**（交付摘要见第 3 节，明细见 CHANGELOG 对应日期条目，2026-08-16 前条目见 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md)）。**待环境**：本机 Docker/Milvus 未安装，向量检索以 NoopVectorStore 降级运行（索引元数据正常，向量写入待 Milvus 就绪后自动生效）。**V2 批次未启动**（范围见第 5 节待办与决策 D19）。

> 里程碑完成标准：代码骨架以 M01 定义为准（目录结构与 docs 一致，决策 D7）；MVP 模块以功能总表对应功能验收（完成定义见 PRODUCT.md 第 12 节）。

## 3. 已完成（能力基线摘要）

> 本节只保留能力基线与踩坑备忘，供快速了解当前系统形态；各次交付的完整改动与验证记录以 [CHANGELOG.md](./CHANGELOG.md) 对应日期条目为准（2026-08-16 前条目见 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md)），不再在此重复展开。

| 交付 | 日期 | 摘要 |
| --- | --- | --- |
| M01 代码骨架 | 2026-08-12 | 后端 7 个 Maven 模块与 common 基座、.env 配置体系（D8）、SQL 初始化链路（00~95 编号契约 + init-db.ps1）、blog/admin 双应用脚手架（pnpm Monorepo） |
| M02 身份与多租户 | 2026-08-12 | JWT 15 分钟短时效 + 刷新令牌 SHA-256 哈希存 Redis、GETDEL 轮换防重放；注册即建空间（D9）；五角色；双层校验（接口权限 + Service 资源归属） |
| M03 博客公开页 | 2026-08-12 | 公开列表/详情（markdown-it + DOMPurify XSS 清洗、标题目录导航）、搜索/标签 JSON_TABLE 聚合、评论/点赞幂等切换、阅读量 Redis 24h 防刷 |
| M04 内容管理 | 2026-08-13 | 知识 CRUD + 自动保存幂等 + version 乐观锁（冲突 409）；8 状态机一次定版（构思->草稿->待审核->已通过->定时发布->已发布->更新中->已下架） |
| M05 RAG 索引 | 2026-08-13 | 发布即索引流水线：事件->清洗->切片->Embedding（百炼 text-embedding-v4，32 片/批）->Milvus/Noop 写向量->kb_chunk 元数据 + kb_index_version 版本管理 |
| M06+M12 AI 基座 | 2026-08-13 | ModelGateway（供应商解析 + 熔断 + 连通性测试）、AiTask 任务底座（幂等键 + Redis 进度 + SSE 事件）、场景模型配置（ai_scene_config 优先、.env 回退） |
| M07 AI 创作 | 2026-08-13 | AI 写作（topic/draft/content 至少一项 -> 结构化 title+content）、审校异源校验（写作/审校模型不同源，结构化 severity/position/evidence/suggestion） |
| M08 AI 对话 | 2026-08-13 | 小光（D14）SSE 流式问答（chunk/citation/done 协议）、会话/消息落库、知识级问答、引用溯源 |
| M09 AI 增值 | 2026-08-13 | 摘要/SEO 结构化输出（Schema 校验）落库 ai_enhance_result |
| M10 审核发布 | 2026-08-13 | 发布前自动 AI 审核（error/失败阻断，warning/info 作者确认），旧审核接口保留回退；立即/定时发布（pub_release 幂等 + PublishJob 每分钟扫描） |
| M11 读者纠错 | 2026-08-13 | 匿名提交（permitAll）+ 追踪号 + Redis 限流（同 IP 每分钟 1 条，超限 429） |
| M13+F-1301 后台与缓存 | 2026-08-13 | admin 四页（登录/空间设置/模型配置/审计日志）、热点缓存 cache-aside（空值哨兵 + 降级回源） |
| KB-1~KB-6 知识平台化重构 | 2026-08-14 | 文章->知识全仓改名（D17，不保留兼容期）、kb_knowledge_base/kb_directory 三层体系（单库单目录）、库级可见性 + 可见库集合单一推导（VisibilityService）、回收站（publishing 聚合，30 天，recycle_status 独立软删列）、跨空间公开读、前端 8 屏（B01/B16/B20~B22 等）、存量迁移与全量验收 |
| 后端风格优化 | 2026-08-14 | PageQueryDTO 分页基类、@PathVariable/@RequestParam 隐式命名（根 POM -parameters）、业务内部类清零、Controller 参数全 DTO 封装 |
| 全功能测试缺陷修复 | 2026-08-16 | BUG-3~11：content->knowledge 依赖 DAG 补齐、知识归属/孤儿防线（checkOwnership + 86_orphan_cleanup.sql 清理 4 条 kb_id=0 孤儿）、库/目录计数闭环（KnowledgeCountApi 反向 SPI）、编辑器重做（选库/目录/提交审核入口）、session 持久化（refreshToken 不落盘）、点赞状态以服务端为准、http 错误消息透出 |
| BUG-002~005 修复 | 2026-08-17 | chat 流式整段渲染（占位消息改 reactive 代理）、审核 AI 结果懒回填（backfillAiResult）、RAG 检索恒空（Milvus 探测改 REST v2 collections/has + reindex 强制重建 + 补跑端点）、提交审核后跳转 |
| 小光 Markdown 渲染 | 2026-08-17 | ChatPage/KnowledgeQaDialog 助手消息改 v-html 渲染 renderMarkdown()（复用 markdown-it + DOMPurify 通道），用户消息保持纯文本插值防 XSS |
| IDEAS 批次 + BUG-006 | 2026-08-18 | F-0212 知识赞/踩互斥+收藏+B23 收藏页、F-0213 评论赞踩（eng_like 三态化 + eng_favorite/eng_comment_reaction 新表）、F-0214 创作中心主导航、F-0312 目录树右键菜单（B01/B20 共用组件）、F-0808 详情 AI 摘要（发布事件异步生成+aiSummary 透出）；BUG-006 详情页 TOC 空时 grid 单栏回退修复；顺带修复目录 PUT 重命名返回空值契约缺陷 |

踩坑备忘（实现时易复犯，背景详见 CHANGELOG 对应条目，8-16 前条目见 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md)）：

- **Spring Boot 4 relaxed binding 失效**：.env 经 spring.config.import 导入的大写属性不做宽松绑定，配置属性类（AiProperties/MilvusProperties 等）必须用 @Value 显式占位符绑定。
- **雪花 ID 精度**：Long 超出 JS Number 安全整数，后端全局序列化为 String（BACKEND §5.3 已约定）。
- **Milvus 探测**：必须打 REST v2 `collections/has` 接口（/healthz 恒 404 曾导致永远 Noop 降级）；本机 Docker/Milvus 未装，向量以 NoopVectorStore 降级运行（索引元数据正常）。
- **环境**：编译前 JAVA_HOME 必须指向 JDK 25；本地 Redis 需无密码启动（.env 密码为空）。
- **遗留运维**：Milvus 就绪后，存量已发布知识需逐篇调用 reindex 补跑端点重建向量（BUG-004 收尾事项，见 BUGS.md 备注）。

## 4. 进行中

IDEA-006~008 已落地为 F-0215/F-0907/F-1307，浏览器回归与文档收尾已完成；V2/V3 范围经决策 D19（2026-08-22）按「个人使用 × 访客/面试官浏览可见」评分重划：V2 27 项（首批 15 项定版优先实施）、V3 25 项（含 6 项多用户/治理向「暂缓」）；9 项对话期新候选已登记总表（F-0216~F-0220/F-0706/F-0707/F-0809/F-1005），其余候选在 IDEAS.md 待评估（决策 D18 保留历史记录）。待办为 OPT-1（AI 线程模型虚拟线程评估，待认领）与 V2 批次（见 §5 待办）；用户新发现缺陷记 [BUGS.md](./BUGS.md)（仅按明确要求修复，不自动认领）。

## 5. 待办

按 MVP 模块拆分；每项依赖的文档章节以 PRODUCT.md 第 5 节功能总表为准，实现时同步完成对应后端模块与初始化 SQL（模块职责见 BACKEND.md，页面见 PROTOTYPE.md）。M01~M13、KB-1~KB-6 与已交付功能（F-0212~F-0215/F-0312/F-0808/F-0907/F-1307 等）的完成记录见第 3 节能力基线，不再重复列出。

| 编号 | 阶段/任务 | 依赖文档 | 状态 | 认领人 |
| --- | --- | --- | --- | --- |
| OPT-1 | 技术优化：AI 线程模型评估虚拟线程。主项：chatStreamExecutor（SSE 长连接占平台线程、池满 CallerRuns 堵容器线程）改 `Executors.newVirtualThreadPerTaskExecutor()` + Semaphore 并发上限（限流与线程模型解耦）。候选点：aiTaskExecutor（AI 任务，需保留并发上限）、indexExecutor（发布即索引 embedding/Milvus 阻塞 I/O）、OpenAICompatibleProvider/EmbeddingServiceImpl/MilvusVectorStore 的 JDK HttpClient 阻塞调用；SseService 心跳与 PublishJob 为固定间隔单线程调度，不适用 | 2026-08-17 线程模型评估（chatStreamExecutor 结构性短板，详见会话记录） | 待认领 | |
| V2 | V2 批次（决策 D19，27 项）：首批定版 15 项——总表 F-0204/F-0603/F-0606/F-0607/F-0703/F-0806/F-1103 + 新登记 F-0216~F-0219/F-0706/F-0707/F-0809/F-1005；基建前置：Milvus 安装 + 存量 reindex 补跑（BUG-004 收尾）、F-0504/F-0505/F-1304 | PRODUCT §5（V2 27 项）、PROTOTYPE §7/§8 | 待认领 | |

> 说明：V2/V3 范围经决策 D19（2026-08-22）按「个人使用效率 × 访客/面试官浏览可见」评分重划：V2 27 项 / V3 25 项（其中 F-0106/F-0211/F-1002/F-1003/F-1203/F-1204 共 6 项多用户/治理向标「暂缓」）；原 D18「AI 优先」标注停用（历史决策保留于 §8 与 CHANGELOG）；V3 其余 19 项保持规划不排期；阶段调整须经 CHANGELOG 记录（决策 D10）。
> KB-1~KB-6 已全部交付验收；实施细则方案（`knowledge-redesign-proposal.md` / `code-implementation-plan.md`）已随实施完成删除，需要时经 git 历史回溯。

## 6. 文档一致性核验

历史核验记录（W6/W7 等）已随归档移入 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md)；后续一致性核验随每次交付在 CHANGELOG 对应条目记录，不再单独维护本节。

## 7. 最近变更

> 仅保留最近 3 条摘要；完整变更以 [CHANGELOG.md](./CHANGELOG.md) 为准。

- 2026/8/22 · ZCode：**docs 文档体系整体瘦身**——CHANGELOG 归档机制（8-16 前 19 条移入新增 CHANGELOG-ARCHIVE.md，模板移回头部）、STATUS §2/§5/§6 精简、BUGS 历史简表化、QA §7 并入 §3/§4、README 快速开始/技术栈改链接 GLOBAL、删除重复测试 zip（docs 体积 1.1M→约 380K）。纯文档变更。
- 2026/8/22 · ZCode：**V2/V3 重新划分（决策 D19）+ 9 项新 AI 功能立项登记**——按用户「个人使用为主 + 简历展示（面试官可能以访客身份浏览 URL）」口径，三轮发散候选经评分（总分 = 自用 P + 访客可见 I×1.2 + 成本低 +1/中 +0，I 系数经用户调参 1.4→1.2、演示冲击维度废除）定稿：V2 27 项（总表 19 + 新登记 8，首批定版 15 项）、V3 25 项（原 V3 2 + 新候选 1 + 自 V2 移入 16 + 暂缓 6）。总表新增 F-0216 问搜一体 / F-0217 语义搜索 / F-0218 术语悬浮解释 / F-0219 图文导读 / F-0220 站点 AI 导游 / F-0706 相关追问推荐 / F-0707 问答转知识草稿 / F-0809 图片 AI 讲解 / F-1005 评论 @小光问答；统计 90→99 项（MVP 47 / V2 27 / V3 25）。其余候选登记 IDEAS.md 待评估（IDEA-009~023）；D13 冲突 3 项待用户裁决。纯文档变更，代码零改动。
- 2026/8/22（修复批次续） · ZCode：**BUG-015/026 移除 + BUG-030 修复**——按用户逐条指定：「BUG-015、BUG-026 移除」清场（015 建议关闭 65 次复核均 200、026 上批已修复）；「BUG-030 执行修复」：新增公开探测端点 `GET /api/v1/public/knowledge-bases/{kbId}`（公开库 200 返回 name/visibility，私有库/不存在 404「知识库不存在或无权访问」）+ 前端 `/kb/[id]` 加载链改 loadOwnerInfo→probePublicKb 分流（404 渲染「知识库不可访问（F-0307）」+ 返回列表链接，访客公开库头部显示真实库名）。GUI 三态验证通过（访客私有库 404 页 / 访客公开库正常 / OWNER 库主模式不受影响）；curl 公开 200 / 私有 404 / 不存在 404。前端 typecheck 过；后端 JDK25 fat jar 重启。

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
| D18 | **AI 相关功能优先级提升（2026/8/20）**：V3 的 AI 功能并入 V2（F-0505、F-0607、F-0704/F-0705、F-0806/F-0807、F-1003、F-1103、F-1203/F-1204），V2 内 24 项 AI 相关功能标「AI 优先」优先实施（AI 范围定义见 PRODUCT §5 阶段标记说明）；V3 仅剩非 AI 的 F-0207/F-1305 |
| D19 | **V2/V3 重新划分（2026/8/22）**：按「个人使用效率 × 访客/面试官浏览可见」评分（总分 = 自用 P + 可见 I×1.2 + 成本低 +1/中 +0）重划——V2 27 项（首批定版 15 项）、V3 25 项（含 6 项多用户/治理向「暂缓」：F-0106/F-0211/F-1002/F-1003/F-1203/F-1204）；新增总表登记 F-0216~F-0220/F-0706/F-0707/F-0809/F-1005 共 9 项；D18 的「AI 优先」标注停用（PRODUCT §5 阶段标记说明）；未采纳候选入 IDEAS.md 待评估 |

## 9. 环境速查

- 后端：`JAVA_HOME` 必须指向 JDK 25；Maven 3.9 构建（命令见 GLOBAL.md）。
- 前端：Node 20+ 与 pnpm 9+（命令见 GLOBAL.md）。
- 中间件：MySQL 8.4 / Redis 为远程实例，配置唯一载体为 `backend/xlumen-server/config/.env`（决策 D8，参数与模板见 GLOBAL.md）。
- SQL 初始化：链路由 M01 代码骨架阶段按 BACKEND.md 建立（编号以实际为准），脚本与代码同一提交。
