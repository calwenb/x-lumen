# xLumen 开发状态与交接文档（AI 必读）

> 更新日期：2026/8/25
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
| M01 代码骨架 | 2026-08-12 | 后端 7 个 Maven 模块与 common 基座、配置体系（D8 .env，2026-08-30 升级为 D29 profile）、SQL 初始化链路（00~95 编号契约 + init-db.ps1）、blog/admin 双应用脚手架（pnpm Monorepo） |
| M02 身份与多租户 | 2026-08-12 | JWT 15 分钟短时效 + 刷新令牌 SHA-256 哈希存 Redis、GETDEL 轮换防重放；注册即建空间（D9）；五角色；双层校验（接口权限 + Service 资源归属） |
| M03 博客公开页 | 2026-08-12 | 公开列表/详情（markdown-it + DOMPurify XSS 清洗、标题目录导航）、搜索/标签 JSON_TABLE 聚合、评论/点赞幂等切换、阅读量 Redis 24h 防刷 |
| M04 内容管理 | 2026-08-13 | 知识 CRUD + 自动保存幂等 + version 乐观锁（冲突 409）；8 状态机一次定版（构思->草稿->待审核->已通过->定时发布->已发布->更新中->已下架） |
| M05 RAG 索引 | 2026-08-13 | 发布即索引流水线：事件->清洗->切片->Embedding（百炼 text-embedding-v4，32 片/批）->Milvus/Noop 写向量->kb_chunk 元数据 + kb_index_version 版本管理 |
| M06+M12 AI 基座 | 2026-08-13（Spring AI 2.0.1 全量迁移 2026-08-24） | ChatRuntime（Spring AI 供应商解析 + 熔断 + 连通性测试，双选项装配 ChatModel/ChatClient）、AiTask 任务底座（幂等键 + Redis 进度 + SSE 事件）、场景模型配置（ai_scene_config 优先、profile 配置回退） |
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
| F-0708/F-0608 Agent（IDEA-025，Spring AI 形态） | 2026-08-24（2026-08-25 双轨合单轨） | 工具层知识搬运（knowledge.search/list/getDirectoryTree，只读+可见库强制过滤，注册为 Spring AI ToolCallback）、ChatClient + ToolCallingAdvisor 自动多轮工具循环（对话 SSE tool 事件+chat_message 轨迹三列+历史配对修剪，事件/配对经 ToolEventSink 收集）、写作「大纲→分章→自审→修订」多步工作流、审校事实核对（Schema 不变+库内证据引用）；**2026-08-25 双轨合单轨**：QA/写作/审校一律走 Agent 路径（拆除普通双轨与 `agent_enabled` 开关），写作降级改为「主链路失败任务 FAILED、自审/修订失败跳过修订交付」；ScriptedChatModel 脚本化离线全链路测试 |
| 审核中心恢复+通用消息（IDEA-024） | 2026-08-24 | 新模块 xlumen-notification（noti_notification + /api/v1/notifications + AiTaskCompletedEvent 事件钩子，REVIEWER 完结→通过/未通过/失败站内信）；blog 顶栏铃铛（未读角标+下拉+全部已读）；/studio/review 恢复审核中心路由、工作台加「审核中心」卡片（FLOW-003 关闭）；admin 模型配置页「Agent 模式」开关 |

踩坑备忘（实现时易复犯，背景详见 CHANGELOG 对应条目，8-16 前条目见 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md)）：

- **Spring Boot 4 relaxed binding 失效**：.env 经 spring.config.import 导入的大写属性不做宽松绑定，配置属性类（AiProperties/MilvusProperties 等）必须用 @Value 显式占位符绑定；D29 起改用 profile YAML，小写点号键（xlumen.bailian.api-key）对 @Value 与 @ConfigurationProperties 均按规范名解析生效。
- **雪花 ID 精度**：Long 超出 JS Number 安全整数，后端全局序列化为 String（BACKEND §5.3 已约定）。
- **Milvus 探测**：必须打 REST v2 `collections/has` 接口（/healthz 恒 404 曾导致永远 Noop 降级）；本机 Docker/Milvus 未装，向量以 NoopVectorStore 降级运行（索引元数据正常）。
- **环境**：编译前 JAVA_HOME 必须指向 JDK 25；本地 Redis 需无密码启动（application-dev.yml 密码留空）。
- **遗留运维**：Milvus 就绪后，存量已发布知识需逐篇调用 reindex 补跑端点重建向量（BUG-004 收尾事项，见 BUGS.md 备注）。

| V2 全量交付 | 2026-08-26 | 28 项功能 + 工程项 IDEA-027（批次 0 注释清理 / 1 AI 基建：配额+追踪+Prompt 后台+事件解耦 / 2 检索双线：语义向量+问搜一体+全量补跑 / 3 写作：RAG 增强+辅助编辑+代码解读 / 4 对话组：访客助手+多文档+追问+草稿+记忆+缺口+库洞察+评论@小光 / 5 前台增值：推荐+SEO+术语+导读+导游+地图+日志+TTS+图片讲解+主题+忘记密码）；Milvus 检索线落地（快速建集/大整数字符串化/扁平响应适配）；ai_call_log 访客可空 |

## 4. 进行中

IDEA-006~008 已落地为 F-0215/F-0907/F-1307，浏览器回归与文档收尾已完成；**IDEA-024/025 已于 2026-08-24 立项实施完成**（F-0708/F-0608 登记总表，见 §3 能力基线与本日 CHANGELOG）；**OPT-2 AI 实现方式全量迁移 Spring AI 2.0.1 已于 2026-08-24 交付**（决策 D20，见本日 CHANGELOG）；V2/V3 范围经决策 D19（2026-08-22）按「个人使用 × 访客/面试官浏览可见」评分重划，决策 D21（2026-08-26）将 I 系数调至 2 重评换档，决策 D22（2026-08-26，用户指定）再微调，决策 D23（2026-08-26，用户拍板）F-0906 随保鲜组移 V3，决策 D24（2026-08-26）工程项 IDEA-027 采纳入 V2 批次，决策 D25（2026-08-26，用户拍板）检索定案 MySQL 关键词 + 语义向量双线，决策 D26（2026-08-26，用户拍板）搭车 5 项登记并入 V2（F-0221 知识地图/F-0222 站点更新日志/F-0506 Prompt 后台/F-0609 双栏改写/F-0709 对话记忆），决策 D27（2026-08-26，用户拍板）F-0609/F-0605 移 V3：现 V2 28 项功能（首批 15 项定版优先实施）+ 工程项 IDEA-027、V3 29 项（含 6 项多用户/治理向「暂缓」）；9 项对话期新候选已登记总表（F-0216~F-0220/F-0706/F-0707/F-0809/F-1005），其余候选在 IDEAS.md 待评估（决策 D18 保留历史记录）。待办为 OPT-1（AI 线程模型虚拟线程评估，待认领）与 V2 批次（见 §5 待办）；用户新发现缺陷记 [BUGS.md](./BUGS.md)（仅按明确要求修复，不自动认领）。
；**2026-08-26 晚：V2 全量实施（批次 0~5）已交付**（28 项功能 + 工程项 IDEA-027，后端 125 测试全绿 + 前端双应用 typecheck/lint/tests + 真实模型 API 冒烟，详情见本日 CHANGELOG），后续进入 V3 规划。

## 5. 待办

按 MVP 模块拆分；每项依赖的文档章节以 PRODUCT.md 第 5 节功能总表为准，实现时同步完成对应后端模块与初始化 SQL（模块职责见 BACKEND.md，页面见 PROTOTYPE.md）。M01~M13、KB-1~KB-6 与已交付功能（F-0212~F-0215/F-0312/F-0808/F-0907/F-1307 等）的完成记录见第 3 节能力基线，不再重复列出。

| 编号 | 阶段/任务 | 依赖文档 | 状态 | 认领人 |
| --- | --- | --- | --- | --- |
| OPT-1 | 技术优化：AI 线程模型评估虚拟线程。主项：chatStreamExecutor（SSE 长连接占平台线程、池满 CallerRuns 堵容器线程）改 `Executors.newVirtualThreadPerTaskExecutor()` + Semaphore 并发上限（限流与线程模型解耦）。候选点：aiTaskExecutor（AI 任务，需保留并发上限）、indexExecutor（发布即索引 embedding/Milvus 阻塞 I/O）、OpenAiChatModel/EmbeddingServiceImpl/MilvusVectorStore 的同步阻塞调用；SseService 心跳与 PublishJob 为固定间隔单线程调度，不适用 | 2026-08-17 线程模型评估（chatStreamExecutor 结构性短板，详见会话记录） | 待认领 | |
| V2（已完成） | V2 批次（D19 定 27 项，D21 换档、D22/D23/D25 微调、D26 加 5 项、D27 移 2 项后 28 项功能 + 工程项 IDEA-027，全部实施）：首批定版 15 项——总表 F-0204/F-0603/F-0606/F-0607/F-0703/F-0806/F-1103 + 新登记 F-0216~F-0219/F-0706/F-0707/F-0809/F-1005；D21 换入 F-0220/F-0705/F-1305（F-1305 已于 D25 移回 V3）、换出 F-0304/F-0210（至 V3）；D22 用户微调：F-0105 忘记密码（邮件验证码）换入，F-1102/F-0303/F-1105 保鲜组移回 V3；D23 用户拍板：F-0906 随保鲜组移 V3；D24 用户拍板：+工程项 IDEA-027 代码注释去除功能编号（存量 Java 约 600 行/294 文件 + 前端 66 文件，脚本批量 + git diff 复核，执行方式待定）；D25 用户拍板：检索引擎定案 MySQL 关键词 + 语义向量双线——页面搜索默认 MySQL 关键词（全站零费），登录用户可切换向量语义（未登录提示），F-1305 ES 移 V3，对话 RAG 统一 MySQL 关键词；D26 用户拍板：+5 项登记 F-0221 知识地图页（IDEA-015）/F-0222 站点更新日志（IDEA-020 改版，admin 动态编辑发布）/F-0506 Prompt 后台动态管理（IDEA-026）/F-0609 双栏对照改写（IDEA-010）/F-0709 对话长期记忆（IDEA-014）；D27 用户拍板：F-0609/F-0605 移 V3；基建前置：Milvus 已装（D25 起换真检索）+ 存量 reindex 补跑（BUG-004 收尾）、F-0105 需 SMTP 邮件服务（账号后续提供）、F-0504/F-0505/F-1304 | PRODUCT §5（V2 28 项）、PROTOTYPE §7/§8 | 已完成（2026-08-26 批次 0~5 全部交付） | |

> 说明：V2/V3 范围经决策 D19（2026-08-22）按「个人使用效率 × 访客/面试官浏览可见」评分重划，决策 D21（2026-08-26）将 I 系数调至 2 重评换档，决策 D22/D23（2026-08-26，用户指定/拍板）与 D25（检索定案）、D26（搭车并入）、D27（移出 F-0609/F-0605）微调：现 V2 28 项功能 / V3 29 项（其中 F-0106/F-0211/F-1002/F-1003/F-1203/F-1204 共 6 项多用户/治理向标「暂缓」），另加工程项 IDEA-027；原 D18「AI 优先」标注停用（历史决策保留于 §8 与 CHANGELOG）；V3 其余 23 项保持规划不排期；阶段调整须经 CHANGELOG 记录（决策 D10）。
> KB-1~KB-6 已全部交付验收；实施细则方案（`knowledge-redesign-proposal.md` / `code-implementation-plan.md`）已随实施完成删除，需要时经 git 历史回溯。

## 6. 文档一致性核验

历史核验记录（W6/W7 等）已随归档移入 [CHANGELOG-ARCHIVE.md](./CHANGELOG-ARCHIVE.md)；后续一致性核验随每次交付在 CHANGELOG 对应条目记录，不再单独维护本节。

## 7. 最近变更

> 仅保留最近 3 条摘要；完整变更以 [CHANGELOG.md](./CHANGELOG.md) 为准。

- 2026/8/29 · ZCode：**全功能黑盒巡检（1080p）+ 修复知识级问答缺上下文**——按 QA.md 全模块巡检（身份/阅读/互动/内容/知识库/审核发布/AI 对话/AI 写作/AI 增值/RAG/管理后台/多用户，12 模块全覆盖，qa_ft_0829/qa_ft_0829_b）；全链路通过（注册登录、赞踩互斥、评论、纠错+速率、建库/目录树、发布→AI 审核→自动发布→通知→审核中心、AI 对话工具循环+引用溯源、访客助手、AI 写作四步、AI 摘要块、后台、多用户可见性+私有库拦截）；**修复：详情页「问小光」知识级问答不注入当前知识上下文**——用户问「这篇文章主要讲什么」模型答「无法判断所指是哪一篇」，根因为路径 `knowledgeId` 在 `runAgent` 未用（未进 toolContext.knowledgeIds、系统提示无标题）；改法=knowledgeIds 并单篇 + 新增 `ChatRequestDTO.knowledgeTitle` 注入系统提示 + 前端透传；顺带把 "知识库管理页「全平台公开知识库聚合在 V2 提供」" 过期文案改掉；验证：ai 10 测试类全绿、blog typecheck/build/lint 通过；遗留：存量旧发布知识无 RAG 索引（BUG-004 补跑缺口，知识级问答/对话查不到其内容）、审计日志 KNOWLEDGE_PUBLISH 操作人为空（AI 自动发布系统上下文）。

- 2026/8/28 · ZCode：**全功能黑盒巡检（1080p）+ 修复 404 路由盲区**——按 QA.md 全模块巡检（身份/阅读/互动/内容/知识库/审核发布/AI 对话/AI 写作/AI 增值/RAG/管理后台/多用户，12 模块全覆盖）；修复 blog 与 admin 均缺 404 兜底——访问不存在路由渲染空白页（BUG-029 同类路由盲区），补 `NotFoundPage.vue` + 两端 router catch-all；验收：发布→AI 审核→自动发布→通知→审核中心全链路、AI 写作四步、AI 调用追踪 13 次 0 失败、多用户可见性（私有库 404+前端 fallback）、回收站等全通过；排除项：AI 对话"切片为空"为测试 fill 换行丢失假象（存量 9 篇已发布知识均已建 ACTIVE 索引）、整页刷新登录态丢失（M02 预期）；双端 typecheck/lint 绿。

- 2026/8/27 · ZCode：**全功能黑盒测试 + 修复 3 项**——按 QA.md 全模块巡检（身份/阅读/互动/内容/知识库/审核发布/AI 对话/AI 写作/AI 摘要/后台/多用户可见性，1080p，qa_ft + qa_ft2 测试账号）；修复：①AI 写作任务 URL `/ai/tasks`→`/tasks`（SSE/GET/retry 404）+ `submitWriting` 取 `result.taskId`→`String(result)` + chunk 展示原始 JSON→解析 `content`（F-0603 流断裂）；②详情页右轨赞/踩/收藏不同步（ReactionBar/FavoriteButton 加 props watch 回同步，`update:counts` 载荷带 reaction，父组件同步 `knowledge.liked`）。验证：博客 typecheck/lint/build/vitest（19 用例）全绿，浏览器复测 AI 写作流式 Markdown 与右轨同步通过。遗留：存量知识 RAG 索引为空（BUG-004 补跑缺口）、`/knowledge-bases`「将在 V2 提供」文案与 V2 已交付不符。

- 2026/8/26 · ZCode：**V2 全量实施交付（批次 0~5，28 项功能 + 工程项 IDEA-027）**——0：注释编号清理（375 文件 787 行）；1：AI 基建（F-0504 配额 / F-0505 调用追踪 / F-0506 Prompt 后台 / F-1304 审核事件解耦）；2：检索双线（F-0217 语义向量+Milvus 落地+全量补跑、F-0216 问搜一体）；3：写作（F-0603 RAG 增强 / F-0606 辅助编辑 / F-0607 代码解读）；4：对话组八项（F-0703 访客助手 / 0704 / 0705 / 0706 / 0707 / 0709 / 1103 / 1005 评论@小光）；5：前台增值十二项（F-0105 忘记密码 / 0204 / 0206 / 0218 / 0219 / 0220 / 0221 / 0222 / 0806 / 0809 / 1306）。验证：后端 125 测试全绿、blog 19 + admin 2 前端测试、双 typecheck/lint 0 errors、真实模型 API 冒烟全链路；Milvus 检索线三连坑根治；冒烟数据与临时凭据全部清理还原。
- 2026/8/26 · ZCode：**V2 名单定版 D21~D27（V2 冻结 28 项功能 + 工程项 IDEA-027，全部实施，未开工）**——D21：I 系数 1.2→2（总分 = P + I×2 + 成本），52 项全量重评：F-0220/F-1305/F-0705/F-1105 移入 V2、F-0304/F-0210 移出至 V3，评分明细归档 CHANGELOG 2026/8/26；D22（用户指定）：保鲜组 F-1102/F-0303/F-1105 移回 V3、F-0105 忘记密码（邮件验证码，SMTP 依赖）换入 V2；D23（用户拍板）：F-0906 随保鲜组移 V3；D24：IDEA-027 代码注释清理采纳入 V2 批次（工程项）；D25（用户拍板）：**检索引擎定案 MySQL 关键词 + 语义向量双线**——页面搜索默认 MySQL 关键词、登录可切换语义（未登录提示），F-1305 ES 移 V3，对话 RAG 统一 MySQL 关键词；D26（用户拍板）：**搭车 5 项并入**——F-0221 知识地图页/F-0222 站点更新日志（admin 动态编辑发布）/F-0506 Prompt 后台/F-0609 双栏改写/F-0709 对话记忆。最终：V2 30 项功能 / V3 27 项（首批 15 项与 6 项暂缓不变），全部实施、未开工。纯文档变更。
- 2026/8/25 · ZCode：**AI 代码级去重 A-F 批（承接架构收敛）**——①Prompt 9 条集中为 `PromptTemplates` 常量类（WritingExecutor 4/ReviewExecutor 2/EnhanceServiceImpl 2/ChatServiceImpl 1，IDEA-026 落点预留为默认值来源）；②JSON 提取统一 `AiJson`（三执行器近似实现收敛，Enhance 抛异常语义保留）；③删 `TaskVO`，`GET /tasks/{taskId}` 改返跨模块稳定类型 `TaskResultVO`（REST `id`→`taskId`，blog writing.ts 同步）；④SSE 事件名后端 `SseEventName`/前端 `sseEvent.ts` 常量化（chunk/progress/done/error/citation/tool）；⑤前端 activeTools/doneTools 收口 `chat/utils/toolPanel.ts`；⑥parse* 函数源核后无跨文件重复，并入 ④⑤。验证：后端 8 模块 BUILD SUCCESS（ai 24 测试全绿）、blog typecheck/vitest 6 条（含新增 toolPanel）/eslint 0 errors。
- 2026/8/25 · ZCode：**AI ## 8. 关键决策摘要（详见规范文档，勿推翻）

| 编号 | 决策 |
| --- | --- |
| D1 | 模块化单体，非微服务；`xlumen-boot` 是唯一装配入口（GLOBAL/BACKEND） |
| D2 | 模块内部传统 MVC，不引入六边形/DDD 等复杂分层（GLOBAL/BACKEND） |
| D3 | 跨模块反向流程用 RocketMQ + Outbox 事件解耦，不新增反向 Maven 依赖（BACKEND） |
| D4 | OpenAPI 是前后端接口契约唯一来源，前端据此生成类型（PRODUCT §7） |
| D5 | 当前直接维护初始化 SQL，不建升级机制（BACKEND） |
| D6 | Redis 只存短期状态，业务事实以 MySQL 为准（PRODUCT §9） |
| D7 | **文档先行**：目录结构以 docs 为唯一事实源，代码骨架不得偏离（PRODUCT §5） |
| D8 | ~~配置唯一载体 .env~~（2026-08-30 被 D29 取代，保留历史） |
| D29 | **配置唯一载体 spring profile YAML（2026/8/30）**：application-{dev,test,prod,demo}.yml 位于 xlumen-boot/src/main/resources；启动 --spring.profiles.active=<dev|test|prod>；废除 .env 与 spring.config.import（GLOBAL）。「随仓库提交并打进 fat jar」条款被 D30 修订；「各环境文件自我完整」条款被 D31 修订 |
| D30 | **环境 profile 不入库（2026/9/6）**：application-{dev,test,prod}.yml 含真实密钥，从 git 移除并 .gitignore 忽略，仓库仅保留 application-demo.yml 模板；开发机放 resources（本地构建打进包），服务器放 jar 同级 config/ 外部加载（改配置重启即生效）；git 历史 9-06 前提交仍含旧密钥，仓库转公开前须轮换并清历史（BACKEND §17 / DEPLOY §4） |
| D31 | **配置分层·环境属性归 profile（2026/9/7）**：application.yml 只放环境无关公共项（active 开关、连接池策略、健康检查、日志、模型选型、Agent 参数）；中间件地址/端口/库名、server.port、site-url、端口守卫、全部密钥一律写对应 profile——即使各环境当前值相同（判据=键是否环境属性，而非值是否相同）。附带向量隔离修正：Milvus 集合名固定 kb_chunks，database 是唯一隔离层，test/prod 分别用 xlumen_test/xlumen_prod（需服务端预建 database），dev 保留 default（既有向量不动）（BACKEND §17 / DEPLOY §4） |
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
| D20 | **AI 实现方式全量迁移 Spring AI 2.0.1（2026/8/24，OPT-2）**：不再手写 OpenAI 兼容协议/工具循环，统一为 Spring AI 形态——ChatRuntime + ChatClient（ToolCallingAdvisor 自动多轮工具循环）+ ToolCallbackAdapter + ScriptedChatModel（Mock 兜底）+ OpenAiEmbeddingModel（向量化）；不引 Spring AI Alibaba、不降 Boot（4.1.0）/JDK（25）；模型名仍按 ai_scene_config 逐请求解析；对外契约（REST/SSE/chat_message/.env/前端）零变化 |
| D21 | **V2/V3 换档重评（2026/8/26，V2 开发前评审）**：I 系数由 1.2 调至 2（新总分 = 自用 P + I×2 + 成本低 +1/中 +0），对 D19 划分全 52 项重评——F-0220（12.5）/F-1305（12.0）/F-0705（11.5）/F-1105（10.5）移入 V2，F-0304（5.0）/F-0210（6.0）移出至 V3；V2 27→29 项、V3 25→23 项，首批 15 项与 6 项「暂缓」不变；52 项 P/I/总分明细归档于 2026/8/26 CHANGELOG 条目（下次重排直接复用，不再重评）；待裁决：F-0217（向量/Milvus）与 F-1305（ES）两条检索线是否同期做 |
| D22 | **V2 名单人工微调（2026/8/26，用户指定）**：F-1102 时效检测/F-0303 版本管理/F-1105 旧知识更新闭环三项「知识保鲜生命周期组」整体移回 V3（V2 聚焦访客演示与自用高频）；F-0105 忘记密码（邮件发送验证码形式）换入 V2，需新增 SMTP 邮件依赖；V2 29→27 项、V3 23→25 项；D21 其余换档不变；⚠️ F-0906 下架/回滚依赖 F-0303，F-0303 移 V3 后回滚基础缺失，待裁决 |
| D23 | **F-0906 随保鲜组移 V3（2026/8/26，用户拍板）**：F-0906 下架/回滚的回滚能力依赖 F-0303 版本管理，采纳推荐方案随保鲜组移 V3（与版本管理同批做），V2 27→26 项、V3 25→26 项；「发布无下架」流程缺口（FLOW 清单）对应修复随后延。V2 名单至此冻结为 26 项，剩余待裁决：F-0217（向量/Milvus）与 F-1305（ES）两条检索线是否同期做 |
| D24 | **IDEA-027 代码注释清理采纳入 V2 批次（2026/8/26）**：工程项非产品功能，不入 PRODUCT 功能总表；存量 Java 约 600 行命中（F-xxxx 513/IDEA-xxx 57/BUG-xxx 30，294 文件）+ 前端 66 文件，执行方式（一次性脚本批量 or 仅新代码执行）待批次开工确定 |
| D25 | **检索引擎定案：MySQL 关键词 + 语义向量双线（2026/8/26，用户拍板）**：页面搜索默认 MySQL 关键词（全站现成 LIKE、零费），登录用户可切换向量语义（F-0217，未登录显示提示）；F-1305 ES 全文搜索移 V3（不接入 ES）；对话 RAG 检索（knowledge.search）统一走 MySQL 关键词线（高频零费）；基建前置：Milvus 已装→换真检索 + 存量 reindex 补跑 |
| D26 | **搭车 5 项并入 V2（2026/8/26，用户拍板）**：登记 F-0221 知识地图页（IDEA-015，与 F-0705 共用聚类，访客可见）/F-0222 站点更新日志（IDEA-020 改版：不固定月度，前台展示页 + admin 后台动态编辑发布）/F-0506 Prompt 后台动态管理（IDEA-026，默认值落点 PromptTemplates 常量已就绪）/F-0609 双栏对照改写（IDEA-010，与 F-0606 同编辑器入口）/F-0709 对话长期记忆（IDEA-014，与对话组同改造窗口）；V2 25→30 项功能；工程项 IDEA-027（D24）不变 |
| D27 | **F-0609/F-0605 移 V3（2026/8/26，用户拍板）**：F-0609 双栏对照改写、F-0605 写作任务可恢复移 V3（编辑器/任务编排类按实现价值后置）；V2 30→28 项功能、V3 27→29 项 |

## 9. 环境速查

- 后端：`JAVA_HOME` 必须指向 JDK 25；Maven 3.9 构建（命令见 GLOBAL.md）。
- 前端：Node 20+ 与 pnpm 9+（命令见 GLOBAL.md）。
- 中间件：MySQL 8.4 / Redis 为远程实例，配置唯一载体为 `xlumen-boot/src/main/resources/application-<env>.yml`（决策 D29，模板见同目录 application-demo.yml）。
- SQL 初始化：链路由 M01 代码骨架阶段按 BACKEND.md 建立（编号以实际为准），脚本与代码同一提交。
