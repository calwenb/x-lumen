# xLumen 开发状态与交接文档（AI 必读）

> 更新日期：2026/8/12 18:03
> **本仓库专属**。
> 本仓库由多个 AI 工具协作开发，**本文件是唯一的上下文交接中心**：开始工作前通读，结束时更新。变更历史另见 [CHANGELOG.md](./CHANGELOG.md)。

## 1. 工作流（强制规则）

1. **阅读**：开工前先确认仓库路径为项目根目录；通读本文件 → 任务相关规范文档：产品 [PRODUCT](../product/PRODUCT.md)（唯一功能事实源，第 5 节总表）、后端 [BACKEND](../backend/BACKEND.md)、前端 [FRONTEND](../frontend/FRONTEND.md)、页面 [PROTOTYPE](../frontend/PROTOTYPE.md)、运行命令 [GLOBAL](../global/GLOBAL.md)。
2. **认领**：在第 5 节待办中把任务标为 `已认领（AI 名）`，一次一项；未认领任务可能被其他 AI 同时开始，冲突以先提交者为准。
3. **实现与验证**：只做任务范围内变更；目录结构与功能范围不得偏离 docs（决策 D7）；跑通相关质量门禁（命令见 GLOBAL.md，后端编译需 JDK 25）。
4. **收尾**（不得省略）：更新本文件状态与待办；按 [CHANGELOG.md](./CHANGELOG.md) 头部模板追加一条；代码与文档同一提交。
5. **最高规则**：文档必须与代码实际状态一致，禁止虚构进度；禁止顺手重构、记录密钥。

## 2. 当前里程碑

**文档体系：已完成（本次交付，含前后端职责重组与目录结构变更）。** 下一步：**代码骨架** → **MVP 模块**。MVP 范围以 PRODUCT.md 第 5 节功能总表（37 项 MVP 功能）为准，落地顺序见第 5 节待办 M01~M13。核心变更：前后端职责重组（blog 承载创作/阅读/互动/AI 对话全链路，admin 仅配置管理）、主页恢复为文章展示页（AI 对话降为菜单入口）、发布即索引（取消外部资料导入）、新增文章可见性（公开/私有，F-0307）、仓库目录重组（backend/xlumen-server + frontend/xlumen-frontend-blog/admin）。

> 里程碑完成标准：代码骨架以 M01 定义为准（目录结构与 docs 一致，决策 D7）；MVP 模块以功能总表对应功能验收（完成定义见 PRODUCT.md 第 12 节）。

## 3. 已完成（文档体系）

- `docs/ai/STATUS.md`（本文件）、`docs/ai/CHANGELOG.md`、`docs/product/PRODUCT.md`：本任务交付，已完成。
- `docs/global/GLOBAL.md`、`docs/backend/BACKEND.md`、`docs/frontend/FRONTEND.md`、`docs/frontend/PROTOTYPE.md`、根 `README.md`：已完成（2026-08-12 交付）。
- 交付规范：新文档以实际链路为准；功能清单唯一来源为 PRODUCT.md 第 5 节总表，其他文档引用不复制。

## 4. 进行中

无（当前没有已认领任务）。

## 5. 待办

按 MVP 模块拆分；每项依赖的文档章节以 PRODUCT.md 第 5 节功能总表为准，实现时同步完成对应后端模块与初始化 SQL（模块职责见 BACKEND.md，页面见 PROTOTYPE.md）。

| 编号 | 阶段/任务 | 依赖文档 | 状态 | 认领人 |
| --- | --- | --- | --- | --- |
| M01 | 代码骨架（backend/xlumen-server 模块划分、frontend 双应用脚手架、SQL 初始化链路） | PRODUCT §5、BACKEND、FRONTEND、GLOBAL §4 | 待办 | — |
| M02 | 身份与多租户（F-0101~F-0104） | PRODUCT §5 模块一 | 待办 | — |
| M03 | 博客前台公开页（F-0201~F-0203） | PRODUCT §5 模块二、PROTOTYPE B01~B04 | 待办 | — |
| M04 | 内容管理与可见性（F-0301~F-0302、F-0307） | PRODUCT §5 模块三、PROTOTYPE B10 | 待办 | — |
| M05 | 文章知识索引 RAG：发布即索引（F-0402~F-0405、F-0407） | PRODUCT §5 模块四、BACKEND §13 | 待办 | — |
| M06 | AI 核心引擎（F-0501~F-0503） | PRODUCT §5 模块五 | 待办 | — |
| M07 | AI 内容创作（F-0601、F-0604） | PRODUCT §5 模块六、PROTOTYPE B11 | 待办 | — |
| M08 | AI 对话：菜单页与文章级问答（F-0701~F-0702） | PRODUCT §5 模块七、PROTOTYPE B00/D01/D02 | 待办 | — |
| M09 | AI 内容增值（F-0801~F-0802） | PRODUCT §5 模块八 | 待办 | — |
| M10 | 审核与发布（F-0901~F-0905） | PRODUCT §5 模块九、PROTOTYPE B12/B13 | 待办 | — |
| M11 | 互动与反馈闭环（F-1001） | PRODUCT §5 模块十 | 待办 | — |
| M12 | 技术基础设施（F-1301~F-1303） | PRODUCT §5 模块十三 | 待办 | — |
| M13 | 管理后台配置管理（空间/成员/角色 F-1201、审计 F-1202、模型配置 F-0501/F-0502 管理面） | PROTOTYPE A01~A04 | 待办 | — |

> 说明：数据分析与知识保鲜（模块十一）为 V2/V3 功能，平台治理（模块十二）MVP 部分（空间设置/审计）随 M13 落地、其余 V2/V3 随依赖模块迭代实现；阶段调整须经 CHANGELOG 记录（决策 D10）。

## 6. 文档一致性核验

| 编号 | 核验项 | 状态 |
| --- | --- | --- |
| W6 | 文档体系一致性核验 | 2026-08-12 通过（二次核验），会话 #6 前后端职责重组与目录变更后已同步 8 份文档，待代码骨架阶段复验 |
| W7 | 会话 #6 变更同步核验 | 2026-08-12 完成：功能总表 73 项（MVP 37/V2 24/V3 12）与 PROTOTYPE 页面清单（B00~B19、A01~A07、D01/D02）、GLOBAL/README 命令路径、BACKEND 模块表交叉一致 |

## 7. 最近变更

> 历史记录已按用户要求清空，CHANGELOG 仅保留最新一条；完整变更以 [CHANGELOG.md](./CHANGELOG.md) 为准。

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
| D9 | **个人博客保留多租户架构**：默认单空间使用，团队模式 V2 可选启用（PRODUCT §2） |
| D10 | **阶段标注（MVP/V2/V3）为规划非承诺**：调整须经 CHANGELOG 记录（PRODUCT §5） |
| D11 | **应用职责划分**：blog（:5173）承载文章创建/编辑/发布/阅读/互动与 AI 对话全链路；admin（:5174）仅管理员配置管理（空间/成员/角色、模型、审计），不参与内容流转（PROTOTYPE §2） |
| D12 | **仓库目录**：后端 backend/xlumen-server，前端 frontend/xlumen-frontend-blog 与 frontend/xlumen-frontend-admin，scripts 与根工程配置留仓库根（GLOBAL §4） |
| D13 | **发布即索引**：文章发布自动建 RAG 索引，取消外部资料导入与 URL 抓取；私有文章亦建索引，检索按身份过滤（访客仅公开已发布、博主全部含私有）（PRODUCT §6、BACKEND §13） |
| D14 | **AI 命名**：产品内所有面向用户的 AI 能力（对话/问答/访客助手/写作与审校反馈等）统一称呼为**小光**，界面文案不得使用其他 AI 名称（PRODUCT §8） |
| D15 | **Maven 模块压缩 12→7**：按未来微服务拆分边界合并——identity（+platform）、content（+analytics）、publishing（+engagement）、knowledge、ai（+chat+ai-enhance）+ common/boot；模块内按业务域分包，表前缀保持独立，分包边界即未来拆分边界（BACKEND §4/§5） |

## 9. 环境速查

- 后端：`JAVA_HOME` 必须指向 JDK 25；Maven 3.9 构建（命令见 GLOBAL.md）。
- 前端：Node 20+ 与 pnpm 9+（命令见 GLOBAL.md）。
- 中间件：MySQL 8.4 / Redis 为远程实例，配置唯一载体为 `backend/xlumen-server/config/.env`（决策 D8，参数与模板见 GLOBAL.md）。
- SQL 初始化：链路由 M01 代码骨架阶段按 BACKEND.md 建立（编号以实际为准），脚本与代码同一提交。
