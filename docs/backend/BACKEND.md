# xLumen 后端开发文档

> 更新日期：2026/8/20
> **本仓库专属**。
> 架构形态：多 Maven 模块的 Spring Boot 模块化单体；包结构：传统 MVC。
> 适用范围：后端技术基线、模块划分、编码规范、数据规则、配置与性能约束。
> 引用原则：**引用不复制**——功能清单唯一来源为 PRODUCT.md 第 5 节总表（引用用 F-xxx 编号）；内容状态机文字版见 PRODUCT.md 第 4 节；启动命令与质量门禁命令见 GLOBAL.md；本文档不重复上述内容。

## 1. 文档目标与阅读顺序

本文用于指导开发者和 AI 生成一致、简洁、可维护的后端代码，定义 Maven 模块、MVC 包结构、对象边界、数据规则、接口、中间件、配置、性能与测试约束。

开始实现前依次阅读：

1. [产品设计文档](../product/PRODUCT.md)——产品行为、验收要求与 13 模块 90 项功能总表（唯一功能事实源，MVP 47 / V2 41 / V3 2）。
2. 本文档——后端实现方式。
3. [全局文档](../global/GLOBAL.md)——本地初始化、启动命令与质量门禁命令。

## 2. 技术基线

> 版本基线全仓唯一维护在 GLOBAL.md，本文档只写依赖清单与关键配置要求，不重复版本号管理规则。

- JDK 25、Maven 3.9、Spring Boot 4.1.0（模块化单体，决策 D1）。
- Spring MVC、Spring Validation、Spring Security（OAuth2 resource-server + jose）。
- MyBatis-Plus 3.5.16：`mybatis-plus-spring-boot4-starter`（Boot 4 专用 starter）+ `mybatis-plus-jsqlparser`（分页与优化器支持）。
- MySQL 8.4 LTS、Redis、RocketMQ、Milvus、MinIO。
- Hutool 5.8.38、Lombok。
- springdoc-openapi（OpenAPI 契约唯一来源，决策 D4）、Actuator、Micrometer。
- JUnit 5、AssertJ、Mockito、Spring Boot Test、WireMock、ArchUnit。

依赖由父 `pom.xml` 统一管理版本。框架或核心依赖升级必须单独验证，不能与业务功能混在一次修改中。

**Lombok 强制配置（JDK 23+ 必须）**：JDK 23 起 classpath 上的注解处理器不再被隐式加载，必须在 `maven-compiler-plugin` 的 `annotationProcessorPaths` 中显式声明 Lombok，否则 `@Slf4j`、`@Data` 等注解不生效，编译产物缺少 getter/setter 导致失败。

## 3. 总体结构

> 总体架构图见 [GLOBAL.md 第 3 节](../global/GLOBAL.md#3-总体架构)（全仓唯一权威版本），本节不再重复画图。本模块的装配边界：xlumen-boot 为唯一启动装配入口，聚合 7 个模块（common 基座 + 5 个业务模块：identity/content/publishing/knowledge/ai + boot 装配）；模块间通过 XxxApi/XxxApiImpl 跨模块边界调用，不跨模块访问 Mapper/Entity（详见第 5 节包结构）。模块按未来微服务边界合并（决策 D15），模块内部统一传统 MVC 扁平结构（不引入业务域包，决策 D2），表前缀保持独立，未来拆分时按模块/表前缀边界整包迁出。

系统以一个 Spring Boot 进程运行（决策 D1）。耗时任务由 RocketMQ 消费者和定时任务异步执行，但最终仍通过所属模块的 Service 完成业务处理。

## 4. Maven 模块

共 7 个 Maven 模块（父 POM `xlumen` 负责聚合与依赖管理，决策 D15），对应 PRODUCT 功能总表 13 个产品模块；模块按未来微服务拆分边界合并，模块内统一传统 MVC 扁平结构，表前缀保持独立（模块十三"技术基础设施"为横切工程能力，由 common/boot 与本文工程规范承载）。

```text
backend/xlumen-server/
├─ pom.xml                  # 父 POM：统一依赖与版本管理
├─ sql/init/                # 初始化 SQL（编号见第 7 节）
├─ config/.env.example      # 配置模板（决策 D8；.env 不入库）
├─ xlumen-common/           # 基座：ApiResponse/BizException/WorkspaceContext/RequestId
├─ xlumen-identity/         # 身份与多租户 + 平台治理（iam_ + plt_）
├─ xlumen-content/          # 内容管理 + 数据分析与知识保鲜（cnt_ + analytics_）
├─ xlumen-publishing/       # 审核发布与公开读 + 互动反馈（pub_ + eng_）
├─ xlumen-knowledge/        # 知识库与目录管理 + 知识索引 RAG：发布即索引（kb_）
├─ xlumen-ai/               # AI 引擎 + 对话 + 增值（ai_ + chat_ + ai_enhance_）
└─ xlumen-boot/             # 装配层：唯一启动入口
```

| 模块 | 主要职责 | 表前缀 | 阶段 |
| --- | --- | --- | --- |
| `xlumen-common` | 统一响应 `ApiResponse`、`BizException`、`WorkspaceContext`、`RequestId`、事件信封等真正通用的基础类型 | — | MVP 基座 |
| `xlumen-identity` | 用户、登录、会话、工作空间、成员、角色与权限（F-0101~F-0106）；空间设置、审计（MVP），配额、通知（V2）（F-1201~F-1204） | `iam_`、`plt_` | MVP |
| `xlumen-content` | 知识 CRUD、草稿自动保存、版本、AI 写作结果落库（F-0301~F-0307）；访问统计、时效检测、缺口分析、更新建议、旧知识更新闭环（F-1101~F-1105，V2/V3） | `cnt_`、`analytics_` | MVP（analytics V2/V3） |
| `xlumen-publishing` | 审核状态机、AI 自动审核发布、发布幂等、回滚下架、博客前台公开读与知识库浏览（F-0901~F-0907、F-0201~F-0208）；评论、点赞、读者纠错（F-1001~F-1004） | `pub_`、`eng_` | MVP（通知 V2） |
| `xlumen-knowledge` | 知识库与目录管理（F-0308/F-0309，库 CRUD/回收站/目录树）、知识自动索引流水线（发布触发）、索引管理与检索、检索权限过滤、引用溯源（F-0402~F-0405、F-0407） | `kb_` | MVP |
| `xlumen-ai` | 模型网关、场景模型配置、流式输出、AI 写作任务、审校（F-0501~F-0505、F-0601~F-0607）；AI 对话、知识级问答、访客助手（F-0701~F-0705）；摘要、SEO、翻译、配图等增值（F-0801~F-0807） | `ai_`、`chat_`、`ai_enhance_` | MVP |
| `xlumen-boot` | 应用启动、Security、中间件和配置装配 | — | 装配层 |

依赖 DAG（同步 Maven 依赖，方向即"被依赖"）：

- `common` 为基座：所有模块依赖 common；不放业务 Entity、业务 DTO、Mapper 或万能工具类。
- `identity` 被所有业务模块依赖：工作空间与权限是全局横切能力。
- `knowledge` 被 `ai` 依赖（检索与引用溯源），并被 `content` 依赖（知识库/目录归属校验，内容侧通过 `KnowledgeApi` 校验 `kbId`/`directoryId` 归属与可见性）。
- `ai` 被 content/publishing 依赖（写作/审校/增值能力）；`content` 被 publishing/ai 依赖（知识与版本是内容侧事实源）。
- `boot` 依赖全部模块；业务模块不得依赖 boot；Maven 依赖不能形成循环，双向流程优先用业务事件解耦（决策 D3）。
- 业务模块不能直接操作其他模块的 Mapper、Entity 和数据表；每张业务表只有一个所属模块（表前缀归属）。
- 未来拆分（GLOBAL §8 路线图）：AI 长任务消费拆出时迁 ai 模块，公开读拆出时迁 publishing 模块公开读，RAG 独立时迁 knowledge；未达触发条件不得拆分。

## 5. 模块包结构与 MVC 调用规则

每个业务模块统一采用以下结构，只创建实际需要的包：`api/`（对外接口）、`controller/`（REST/SSE 入口）、`service/` + `service/impl/`（业务接口与实现）、`mapper/`、`entity/`、`dto/`、`vo/`、`job/`（定时任务与消息消费入口）、`config/`、`enums/`、`constants/`（确有必要的常量）。

**模块内统一传统 MVC 扁平结构（决策 D2/D15）**：不引入“业务域”概念（如 engagement/editor 等包名），所有类直接按分层放模块根包下：

```text
xlumen-publishing/src/main/java/.../publishing/
├─ controller/   # CommentController / LikeController / PublicKnowledgeController
├─ service/      # CommentService / LikeService / PublicKnowledgeService
├─ service/impl/ # CommentServiceImpl / LikeServiceImpl / PublicKnowledgeServiceImpl
├─ mapper/       # CommentMapper / LikeMapper
├─ entity/       # CommentEntity / LikeEntity
├─ dto/          # 入参/查询参数/跨模块稳定类型
└─ vo/           # 出参视图
```

- 命名按资源/领域词（如 Comment/Like/Knowledge），禁止使用域后缀（如 EngagementService）或域包名（如 engagement/）。
- 未来拆分边界以**模块 + 表前缀**为准（如 publishing 的 eng_ 表随互动能力迁出），不依赖包内域边界。

### 5.1 MVC 调用规则

```text
Controller → Service → Mapper
Job / MQ Consumer → Service → Mapper
Other Module → Api → Service → Mapper
```

- Controller 只负责参数校验、权限入口、调用 Service 和返回结果，不编写核心业务逻辑。
- Service 使用接口加 `impl` 实现类；事务放在实现类的公共方法上。
- Mapper 只负责当前模块的数据访问，不被 Controller、Job 或其他模块直接调用。
- Entity 只对应数据库结构，不能作为接口入参或响应直接返回。
- Job 包同时容纳定时任务和消息消费者，入口代码只做消息解析、幂等检查和 Service 调用。
- 不为简单 CRUD 增加额外抽象层。

**编码风格规范（强制）**：

- **参数封装**：方法参数不超过 3~4 个；字段较多的入参/查询条件必须封装为 DTO/BO（如 `KnowledgeQueryDTO` 承载关键词/目录/标签/分页），Controller 方法同样遵守；Service 方法内只读需要的字段，避免“全量对象传参”。
- **DTO/VO 用普通 class + Lombok**：统一 `@Data @Builder @NoArgsConstructor @AllArgsConstructor`，字段必须带简短注释；DTO/VO 不使用 record（跨模块稳定类型同样遵守，历史 record 已统一替换为 class）。
- **分页入参继承基类**：列表分页请求 DTO 统一继承 `common.dto.PageQueryDTO`（`pageNo`/`pageSize`，默认 1/20，`pageSize` 上限 100 由服务层截断）；继承链上的类统一用 `@SuperBuilder`（父类字段默认值加 `@Builder.Default`，子类无字段时省略 `@AllArgsConstructor` 避免构造器冲突）。
- **默认值语义**：字段初始化表达式（如 `pageNo = 1`）同时使用 `@Builder.Default`，避免 @Builder 忽略默认值。
- **路径/查询参数隐式命名**：`@PathVariable`/`@RequestParam` 不写与参数名相同的显式名称（依赖根 POM `maven-compiler-plugin` 的 `-parameters` 保留参数名元数据）；参数名与路径模板/客户端参数不一致时，优先改模板变量名保持一致，仍不一致才显式声明名称。
- **业务类不写内部类**：Controller/Service 及业务模型中禁止嵌套类型，值对象/状态载体一律提取为顶层类（如 `WorkspaceScope`、`RefreshSession`）。
- **命名按资源/领域词**：Service/Controller/Mapper/Entity 使用同一资源词前缀（如 Comment、Like、Knowledge），不引入域后缀。

### 5.2 对外 Api 目录

`api/` 是其他 Maven 模块同步调用本模块的唯一入口。

- 接口使用 `XxxApi` 命名（如 `ContentApi`、`QuotaApi`）；实现放 `service/impl/`，命名 `XxxApiImpl`，通过 `@Service` 注册并注入模块内 Service 编排。
- Api 方法只接收和返回所属模块 `dto/` 中的稳定类型或基础类型；不能返回 Entity、Mapper 对象、分页插件内部对象或第三方 SDK 类型。
- Api 只暴露真实存在的跨模块能力，模块内部调用继续使用 Service；其他模块只能依赖目标模块 `api/`、`dto/`、`enums/` 中的公开类型。
- 新增或修改 Api 时必须补充调用方测试，并检查 Maven 依赖方向。

### 5.3 JSON 序列化约定（雪花 ID 精度）

雪花 ID（1.9e18）超出 JS Number 安全整数（2^53），数字直传前端会丢精度（M03 详情路由踩坑）。约定：

- **后端 Long 统一序列化为 String**：xlumen-boot 的 `JacksonConfig` 已全局配置（Spring Boot 4 = Jackson 3，`tools.jackson` 包，`JsonMapperBuilderCustomizer` + `JacksonModule` 注册 `ToStringSerializer`），全模块生效，业务代码无需逐字段标注。
- **前端 ID 类字段一律 string 类型**（请求参数/路由参数/响应类型）；**统计与分页数值**（viewCount/commentCount/total 等）在**前端 API 层用 Number() 还原**，页面代码不感知。
- 例外：非 ID 且前端需要精确数值的场景需显式评估；后续里程碑默认遵守本约定。

示例（接口与实现成对出现，均带类注释）：

```java
/**
 * 内容模块对外接口。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface ContentApi {

    KnowledgeVersionDTO saveAiResult(SaveAiResultDTO dto);
}
```

## 6. 命名与对象边界

| 类型 | 示例 | 使用范围 |
| --- | --- | --- |
| Controller | `KnowledgeController` | HTTP 或流式入口 |
| Service | `KnowledgeService` | 模块内部业务接口 |
| Service 实现 | `KnowledgeServiceImpl` | 业务逻辑和事务 |
| 对外接口 | `ContentApi` | 其他 Maven 模块调用 |
| 对外接口实现 | `ContentApiImpl` | 对外调用适配与 Service 编排 |
| Mapper | `KnowledgeMapper` | 当前模块数据访问 |
| Entity | `KnowledgeEntity` | 数据库表映射 |
| DTO | `CreateKnowledgeDTO` | Service 或 Api 参数和结果 |
| VO | `KnowledgeDetailVO` | Controller 响应 |

- Controller 入参使用 DTO，并通过 Bean Validation 校验；返回统一响应包装下的 VO。
- Service 和 Api 不接收 `Map<String, Object>` 表达固定业务结构（禁止 Map 作请求参数）。
- 不使用含义不清的 `Data`、`Info`、`Object` 作为业务类型名。
- 同名字段转换使用 Hutool `BeanUtil`；少量不同字段在 Service Impl 中显式赋值，不额外建立转换包。
- DTO/VO 可使用 Lombok `@Data`/`@Builder` 简化实体；也可使用 record 声明不可变类型，springdoc 对 record 上的 Swagger 注解（`@Schema`、`@NotNull` 等）与 `@Data` 类同等支持，两种风格在模块内保持一致。
- Entity 优先 `@Getter`/`@Setter`，谨慎生成包含全部字段的 `equals` 和 `hashCode`。

### 6.1 工具类与开源框架复用

- 优先使用技术基线内的成熟工具类与开源框架，禁止重复造轮子：字符串/日期/集合/加密/HTTP 等通用能力首选 Hutool，分页与 CRUD 使用 MyBatis-Plus 内置能力，参数校验使用 Bean Validation。
- 引入新的开源依赖必须先验证与 JDK 25 / Spring Boot 4.1 的兼容性，在父 POM 统一声明版本，并在提交说明中说明用途与替代方案。
- 不为单一场景自造工具类；确有必要的通用工具只放模块内 `utils` 或 common 基座（仅限真正跨模块通用）。

### 6.2 注释要求

- 代码必须有必要的注释：类与公共接口方法必须有 Javadoc（职责、关键参数与返回）；复杂业务逻辑、状态机流转、幂等与并发处理、非直觉的实现必须写行内注释说明"为什么"。
- 注释与代码同步更新，禁止保留失效注释；不为 getter/setter、自解释代码写冗余注释。

## 7. 数据库与初始化 SQL

MySQL 使用单实例、单 Schema；无数据库外键（逻辑外键通过业务主键、Api 校验和业务事件保持一致性）。表前缀与所属模块：identity `iam_`、platform `plt_`（identity 模块）、content `cnt_`、analytics `analytics_`（content 模块）、publishing `pub_`、engagement `eng_`（publishing 模块）、knowledge `kb_`、ai `ai_`、chat `chat_`、ai-enhance `ai_enhance_`（ai 模块）。

初始化脚本固定为（`backend/xlumen-server/sql/init/`）：

```text
00_database.sql       10_identity.sql     20_knowledge.sql    30_ai.sql
40_content.sql        50_publishing.sql   60_engagement.sql   70_chat.sql
80_ai_enhance.sql     85_platform.sql     95_analytics.sql
```

- **新模块 SQL 按编号插入，禁止跳号**：新增模块脚本按编号顺序插入现有序列（如 80 与 85 之间新增取 81~84_xxx.sql），不得复用、跳号或重排已有编号；V2/V3 模块脚本（90/95）在对应阶段创建，不提前建空表。
- **存量迁移脚本**（`backend/xlumen-server/sql/migration/`，如 `85_kb_migration.sql`）：用于已初始化的开发库/测试库结构演进，独立于 init 链（`init-db.ps1` 只执行 `sql/init/`），编号沿用 init 编号段；迁移脚本必须幂等可重跑（变更前查 information_schema）。
- `00_database.sql` 默认创建开发数据库、字符集和通用设置；服务器已预建数据库或账号无建库权限时允许跳过。
- 每个模块脚本只创建本模块拥有的表、索引和必要系统数据（内置角色、权限、基础配置；不含演示用户和演示内容）。
- 建表使用 `IF NOT EXISTS`；必要系统数据使用唯一键配合 `INSERT ... ON DUPLICATE KEY UPDATE`，按文件名前缀顺序执行。
- 表命名规则：单 Schema、无外键、主键为 BIGINT 非自增 ID（雪花/`IdUtil` 生成）、业务表含 `workspace_id` 列与 `(workspace_id, status)` 联合索引、唯一键使用 `uk_` 前缀。
- 当前阶段直接维护初始化脚本，不建立数据库升级机制（决策 D5）。
- 测试环境使用独立的 `xlumen_test` 数据库并执行相同脚本，禁止测试连接开发数据库。

## 8. 数据模型与索引策略

### 8.1 核心表清单

以下为各模块核心表（逻辑外键关联，标注所属模块与引入阶段；具体列以各模块初始化 SQL 为准）：

| 表 | 所属模块 | 阶段 | 说明 |
| --- | --- | --- | --- |
| `iam_user` / `iam_workspace` / `iam_workspace_member` / `iam_role` | identity | MVP | 用户（密码 BCrypt 哈希）/ 工作空间（全局隔离维度）/ 成员角色绑定 / 角色定义（OWNER/ADMIN/EDITOR/AUTHOR/VISITOR） |
| `kb_knowledge_base` / `kb_directory` | knowledge | MVP | 知识库（公开/私有/回收站状态，F-0308；V2 增加 slug 列）/ 多级目录树（parent_id，按名称排序，F-0309） |
| `kb_kb_follow` | knowledge | V2 | 知识库关注关系（F-0211） |
| `kb_kb_grant` | knowledge | V2 | 私有库授权名单（F-0106） |
| `kb_chunk` / `kb_index_version` | knowledge | MVP | 知识切片元数据（向量在 Milvus，关联知识已发布版本，含 `kb_id` 按库过滤）/ 索引版本与活动指针（F-0403） |
| `ai_task` | ai | MVP | AI 任务（状态机见第 14 节） |
| `cnt_knowledge` / `cnt_knowledge_version` | content | MVP / V2 | 知识主体（含 `kb_id`/`directory_id` 归属；V2 增加 pinned 置顶/slug 列）/ 历史版本（F-0303） |
| `pub_review` / `pub_release` | publishing | MVP | 审核记录（F-0902）/ 发布记录（F-0905） |
| `eng_feedback` / `eng_comment` | publishing 模块 | MVP / V2 | 读者纠错（F-1001）/ 评论（F-0203） |
| `chat_message` | ai 模块 | MVP | 对话消息 |
| `plt_quota` / `plt_notification` / `plt_activity_log` | identity 模块 | V2 | 配额用量（F-0504）/ 站内通知（F-1004）/ 审计日志（F-1202） |
| `analytics_visit` | content 模块 | V2 | 访问统计明细（F-1101） |

### 8.2 索引策略

- 业务表统一含 `workspace_id`，工作空间维度查询条件以 `workspace_id` 开头的联合索引支撑（如 `(workspace_id, status)`、`(workspace_id, created_at)`），禁止无 `workspace_id` 前缀的等值条件扫全空间。
- 唯一键统一 `uk_` 前缀命名，承担幂等与业务唯一约束（如 `uk_workspace_slug`）；唯一索引可解决的问题不使用分布式锁。
- 避免隐式转换：索引列保持类型一致（BIGINT 列不用字符串比较、时间列不用函数包裹），否则索引失效。
- 状态类查询（待审核/待发布）优先覆盖 `(workspace_id, status)` 并配合分页，状态枚举值不使用魔法数字。

### 8.3 大表治理

- Outbox 事件表：按 `occurredAt` 定期归档清理（超过保留期移到归档表），避免无限增长。
- 审计日志 `plt_activity_log`：只增不改，按季度归档，访问走归档查询。
- 访问统计 `analytics_visit`：明细按日汇总到趋势表后滚动清理，保留最近 N 天明细。
- 知识版本 `cnt_knowledge_version`：已发布版本正文快照只增不删（更新闭环依赖），回收站软删除内容按策略定期物理清理（默认 30 天，F-0305）。
- 回收站软删列（已确认）：知识/知识库回收用独立 `recycle_status` + `deleted_at` 列，**不扩展 8 状态机**（状态机是发布审核闭环概念）；超期清理任务按 `deleted_at` 扫描执行。

## 9. 多租户与权限（双层校验）

- 登录成功后建立用户和当前工作空间上下文（`WorkspaceContext`）。
- **双层校验**：Controller 校验接口权限（第一层），Service 校验资源归属、操作者角色、状态和数据范围（第二层）。
- 所有工作空间业务查询必须包含 `workspace_id` 条件；新增 Mapper 方法时必须测试跨工作空间数据不可见。
- 工作空间 ID 来自可信会话上下文，不直接信任 URL、Header 或 DTO 中的值；权限变化即时生效。
- 平台级跨工作空间操作使用独立接口、独立权限和审计日志。
- 职责分离：作者默认不能直接发布；编辑不能审核自己提交的知识（F-0903）；发布入口先提交独立 AI Reviewer 任务，结果为 `READY` 才允许发布，`BLOCKED/FAILED` 自动退回草稿；旧审核中心与发布自动 AI 审核开关暂时隐藏（F-0907）。
- **知识库可见性（F-0307/F-0308）**：知识可见性由所属知识库决定，公开库知识对所有人可见、私有库仅库主可见；公开读与检索必须同时校验知识状态（已发布）与库可见性，私有库知识对外不可见（404 语义，不暴露存在性）；库越权访问必须由 Service 层强制执行并专项测试。
- **多用户公开读（D9 改写）**：首页知识流、公开库发现页与公开搜索**跨空间聚合全平台公开库**（不再绑定默认空间）；私有库知识仍按「空间归属 + 库可见性 + 授权名单」过滤；跨空间公开读接口必须显式标注并测试越权场景。
- **可见库集合推导收敛**：身份→可见知识库集合的推导（公开库全集 + 自己私有库 + 授权库 V2）统一收敛为单一服务能力，公开读、搜索、RAG 检索与知识列表共用同一推导结果，禁止各模块散落重复过滤条件（F-0407 单一实现）。

## 10. REST API 规范

- 统一前缀 `/api/v1`，资源使用复数名词；查询用 GET，创建用 POST，全量更新用 PUT，局部动作用明确动作端点。
- **知识资源统一路径 `/api/v1/knowledge`**（决策 D17：概念统一后不可数名词；旧 `/api/v1/articles` 直接废弃，不保留兼容期，前后端同仓同 PR 切换）。知识库资源 `/api/v1/knowledge-bases`，目录资源 `/api/v1/knowledge-bases/{kbId}/directories`，回收站资源 `/api/v1/recycle-bin`（type=kb|knowledge，恢复 `/{type}/{id}/restore`、彻底删除 `/{type}/{id}?confirm=CONFIRM`）。
- **回收站聚合层在 publishing**：knowledge 模块依赖方向受限（content→ai→knowledge 环）无法直连 cnt_knowledge，回收站统一编排收敛到 publishing（RecycleBinController + RecycleBinFacadeService，同时依赖 content+knowledge 无环）；kb 侧委托 KnowledgeApi（listRecycledKbs/restoreRecycledKb/purgeRecycledKb），knowledge 侧委托 ContentApi（listRecycledKnowledge/getRecycledKnowledge/restoreKnowledge/purgeKnowledge，恢复含冲突判定：原目录已删→挂库根、原库已彻底删除→409「原知识库不存在，无法恢复」）。
- **索引补跑**：`POST /api/v1/knowledge/{knowledgeId}/reindex`（仅已发布知识；2026-08-17 BUG-004 修复引入，publishing `IndexBackfillService` 承载，正文经 ContentApi 获取），用于 Milvus 接入后或索引修复后的存量向量重建；`reindex()` 走强制重建通道（先失效旧切片/版本，绕过 hash 幂等命中）。
- **自动审核发布（F-0907）**：`POST /api/v1/reviews/auto` 创建 Reviewer 任务，`GET /api/v1/reviews/{id}` 返回 `REVIEWING/READY/BLOCKED/FAILED/PUBLISHED` 状态；仅 `READY` 可调用 `POST /api/v1/reviews/{id}/publish`，结果含 `error` 或任务失败会幂等退回草稿。发布接口按知识版本返回已有发布记录，避免重复创建。
- OpenAPI 是前端生成接口类型的唯一来源（决策 D4）；Controller、DTO 和 VO 提供准确的 Schema 与校验规则。
- 分页参数统一为 `pageNo`、`pageSize`，并限制最大 `pageSize`（上限见第 18 节）。

统一响应（`code`/`message`/`data`/`requestId`）：

```json
{ "code": "SUCCESS", "message": "操作成功", "data": {}, "requestId": "01J..." }
```

错误规则：

- 400：参数错误。401：未登录或会话失效。403：权限不足。404：资源不存在。
- 409：版本、状态或幂等冲突。429：请求过多或配额不足；**使用字面值 429，不用常量**（`HttpServletResponse` 等 API 无 `SC_TOO_MANY_REQUESTS` 常量，引用常量会编译失败）。503：外部服务暂时不可用。

业务异常包含稳定错误码和安全提示；未知异常只返回 `requestId`，不返回堆栈、SQL、密码和内部地址。

## 11. 事务、幂等与并发

- 事务放在 Service Impl 的公共写方法，统一 `@Transactional(rollbackFor = Exception.class)`；Controller、Job 和消息消费者不展开业务事务。
- 同一模块的相关数据在一个本地事务内完成；外部调用不能长时间占用数据库事务——先保存任务，再异步调用。
- 审核、发布、配额结算和任务恢复使用业务幂等键；捕获重复请求时返回已有结果，不重复创建版本、发布内容或扣减配额（F-0905 发布幂等）。自动审核发布重复调用按知识版本返回已有发布记录。
- 知识和发布计划使用乐观锁（版本号），审核、发布、回滚、下架必须校验版本，冲突返回 409，禁止静默覆盖。
- 唯一索引可以解决的问题不使用分布式锁；分布式锁只用于短临界区，设置合理超时和安全释放逻辑。

## 12. RocketMQ 与 Outbox

业务事件信封：

```json
{ "eventId": "01J...", "eventType": "content.knowledge.approved", "schemaVersion": 1,
  "workspaceId": 10001, "bizId": "20001", "occurredAt": "2026-08-12T10:30:00+08:00",
  "traceId": "8f...", "payload": {} }
```

- 业务数据和对应 Outbox 记录在同一本地事务写入；事件由发出模块定义和维护，使用清晰的版本号。
- Publisher 将待发送记录发布到 RocketMQ，成功后更新发送状态；消费者按 `eventId` 和业务幂等键去重（F-1304）。
- 消费入口放在 `job/`，解析消息后调用 Service；普通消息用于解析、向量化、AI、统计和通知。
- 延迟消息只用于短期重试和计划触发，长期计划仍保存在 MySQL；消费失败有限重试，超过阈值进入死信和人工处理（PRODUCT 第 9 节）。

## 13. 知识库、目录与 RAG

发布触发的自动索引流水线（F-0402，决策 D13/D16）：

```text
发布成功事件 → 取已发布版本正文 → 清洗 → 切片 → Embedding
→ 写入新索引（按知识库 kb_id 切分）→ 检索校验 → 激活索引 → 清理旧版本索引
```

- 由发布成功事件触发，无需人工导入；删除/下架同步移出索引；旧知识更新的新版本发布后自动重建（不提供外部资料导入与 URL 抓取，F-0401/F-0406 已随产品变更移除）。
- 知识库与目录管理（F-0308/F-0309）：`kb_knowledge_base`（公开/私有/回收站状态）与 `kb_directory`（parent_id 多级树，目录按名称排序，走数据库排序规则）由 knowledge 模块承载；content 模块的知识 CRUD 通过 `KnowledgeApi` 校验库/目录归属（单库单目录），跨库移动不提供（仅复制或重新发布）。
- MySQL 保存切片元数据、知识库/目录与活动索引指针；Milvus 保存向量和检索过滤字段（workspace_id、知识库 kb_id、知识 ID、版本）。
- **索引版本管理（F-0403）**：切换 Embedding 模型只允许创建新索引版本，不覆盖活动索引；新版本经检索校验通过后才激活，激活后清理旧索引。
- 检索按可见库集合过滤（F-0407）：访客仅命中公开库已发布知识，库主命中全部含自己私有库；引用必须定位到篇名与段落并可跳转原文（F-0405）。检索请求携带身份推导出的可见库集合，`KnowledgeApi.search` 入参含 `kbIds` 过滤维度。

## 14. AI 与外部服务

- 统一 `ModelGateway` 隔离不同供应商（百炼/DeepSeek，F-0501），多供应商隔离、独立配置。
- **场景级模型配置（F-0502）**：Writing、Reviewer、问答、摘要、Embedding 各自独立配置模型与参数；Research/Outline 为 V2 可选场景。
- **SSE 流式（F-0503）**：AI 输出分章节流式推送；流式输出开始后不得自动切换模型续写（PRODUCT 第 8 节）；断线后由用户明确发起新任务或按 sequence 续传（见第 18 节）。
- **AI 任务异步（F-1302）**：AI 长任务必须异步执行并展示进度；任务状态机 `QUEUED → RUNNING → WAITING_APPROVAL（V2 大纲可选确认 F-0602）→ COMPLETED`，失败分支 `FAILED`，人工可 `CANCELLED`；支持有限重试、检查点、取消、死信与人工补偿；任务事实以 MySQL 为准，Redis 只存短期进度。
- **知识增强写作（F-0603，V2 可选）**：启用时写作阶段 RAG 检索结果必须携带证据（页码/标题/段落 + 不可变快照），AI 输出引用必须关联证据；无法溯源的内容必须明确标注为模型生成而非事实（F-0405、PRODUCT 第 8 节）。
- **AI 审校（F-0604）**：Reviewer 与 Writing 使用不同供应商/模型，保证审校独立性（模型异源）；审校输出结构化（严重度/位置/证据/建议）并经 Schema 校验。
- 结构化输出必须通过 Schema 校验和有限修复；权限、参数和内容安全错误不能通过切换模型绕过；降级与熔断按场景策略执行（如 Reviewer 切换备用模型），降级原因进入 AI Trace（F-0505，V3）。
- Content 通过 `AiApi` 创建任务；AI 通过 `KnowledgeApi` 检索资料（携带可见库集合）；AI 完成后发布结果业务事件，由 Content 消费、校验并保存知识版本。AI 不反向依赖 Content，也不能直接修改内容表。

## 15. Redis、日志与安全

### 15.1 Redis 使用边界

- 用于：会话、权限短缓存、热点只读数据（热点知识，F-1301）、限流、锁、短期任务状态（如 SSE 断点与进度）。
- 缓存键包含应用、环境、工作空间和业务主键；缓存不可用时按业务风险选择降级或拒绝。
- 热点知识缓存按**库/目录维度分片**（如 `xlumen:knowledge:{kbId}:{directoryId}`，已确认）：跨空间公开流聚合场景禁止单键缓存全站列表；公开库转私有、知识发布/下架时按维度失效。
- 审核、发布、配额和任务事实不能只存在 Redis，业务事实以 MySQL 为准（决策 D6）。

### 15.2 日志与可观测性

- 日志包含 `requestId`、`traceId`、`workspaceId`、`userId`、模块和必要业务 ID，保证请求链路可定位。
- 不记录访问令牌、刷新令牌、密码、模型密钥、完整敏感 Prompt 和文件正文。
- 记录 HTTP、数据库、消息积压、检索、AI 调用和发布核心指标。
- **日志 Appender 激活策略**：`logback-spring.xml` 中 Appender 必须按实际启用的 profile 显式激活（仅声明不激活会导致"有配置无日志"）；日志级别经 `.env` 变量控制，不写死。
- 健康检查分级：区分应用存活（liveness）与依赖可用（readiness）状态。

### 15.3 安全约定

- 密码使用 BCrypt 存储；访问令牌（JWT）短时效，刷新令牌短时效且支持撤销。
- 刷新令牌不存明文：存储 SHA-256 哈希值；轮换使用 Redis `GETDEL` 原子操作，旧令牌一次性失效（防重放）；多步原子操作（如限流计数、令牌轮换）绑定 Lua 脚本执行，保证原子性。
- 会话 Cookie 必须 `httpOnly + SameSite=Lax`（PRODUCT 第 10 节）。
- 登录、注册、密码重置失败返回统一消息与统一延迟，不暴露账号是否存在（防枚举）。
- 文件上传校验大小、类型、扩展名、内容特征和恶意内容；Markdown 渲染执行 XSS 清洗；网页内容视为不可信输入，检测提示词注入。
- 数据导出、成员管理、密钥、审核、发布和治理操作写审计日志；API 按用户、IP、工作空间和场景限流。
- 用户可见错误信息不能暴露内部实现和敏感数据。

## 16. 简洁代码规范

### 16.1 工具与注解

- 字符串/集合/对象处理优先使用 Hutool `StrUtil`、`CollUtil`、`ObjectUtil`；简单同名属性复制使用 `BeanUtil`；ID 使用 `IdUtil`。
- 日志使用 `@Slf4j`，依赖注入使用字段级 `@Resource`；DTO/VO 可按需使用 Lombok `@Data`/`@Builder`；Entity 优先 `@Getter`/`@Setter`。
- 参数校验使用 `@Validated` 和 Bean Validation 注解；数据库映射优先使用 MyBatis-Plus 注解和 Lambda Wrapper。
- 事务使用 Spring `@Transactional`（`rollbackFor = Exception.class`），明确回滚边界；已有 JDK、Spring、Hutool 或 MyBatis-Plus 能力时，不创建重复工具类和包装层。

### 16.2 简洁性

- 方法只完成一个明确任务，优先使用提前返回减少嵌套；不创建万能 BaseController/BaseService、复杂泛型框架和无实际用途的抽象类。
- 不用过长 Stream 链表达包含异常、状态变化或副作用的业务流程；不返回 `null` 集合，不使用魔法值表示状态。
- 时间统一使用 `java.time`，对外使用 ISO 8601；金额和结算使用最小货币单位整数或 `BigDecimal`，明确舍入方式。
- 异常必须处理、转换或继续抛出，禁止空 `catch`。

### 16.3 注释

每个 Java 文件中的顶级类、接口、枚举、注解和记录类型都必须提供类注释，说明类型作用，并包含作者和日期标签，如：`/** 知识审核服务。负责知识版本校验、审核状态流转和审核记录保存。 @author calwen @date 2026/8/12 */`（按标准 Javadoc 多行书写）。

- 作者统一为 `calwen`；日期使用代码首次创建日期，格式为不补零的 `yyyy/M/d`；修改已有文件时保留原创建日期。
- 类注释说明职责和边界，不使用"用于处理相关业务"等空泛描述；对外 Api 和重要 Service 方法说明用途、关键限制和异常条件。
- 不直观的业务规则、状态限制、安全、并发、幂等和异步处理必须写行内注释；行内注释解释"为什么"和约束，不复述代码语句。

方法内行内注释示例：`approve(dto)` 中先写 `// 审核结论必须绑定提交时版本，避免批准审核期间产生的新内容。` 再执行版本校验，最后更新审核状态；方法使用 `@Transactional(rollbackFor = Exception.class)`。

## 17. 配置管理

> 决策 D8：**配置唯一载体为 `.env`**，禁止第二种配置载体（禁止 yq、禁止 `application-local.yml` 等环境 YAML）。

- 唯一载体：复制 `backend/xlumen-server/config/.env.example` 为 `backend/xlumen-server/config/.env`（真实值不得提交，`.env` 加入 `.gitignore`，仅 `.env.example` 允许提交）。
- 加载方式：`application.yml` 通过 `spring.config.import: optional:file:config/.env[.properties]`（含 `../config/`、`backend/xlumen-server/config/` 相对路径回退）加载；变量命名统一 `${XLUMEN_XXX}` 风格（如 `XLUMEN_DB_URL`、`XLUMEN_JWT_SECRET`、`XLUMEN_BAILIAN_API_KEY`）。
- 环境差异（数据库、Redis、MinIO、Milvus、模型密钥、日志级别等）全部走 `.env`，代码与资源目录不散落配置读取逻辑；配置使用 `@ConfigurationProperties` 映射，并在启动时校验必要字段。
- 敏感信息（密码、密钥）不能出现在资源目录、提交记录、日志、异常、接口响应和测试快照中。
- Windows 下修改 `.env` 必须使用 UTF-8 无 BOM 编码，否则 Spring Boot 解析占位符失败。

### 17.1 SQL 初始化脚本

`scripts/init-db.ps1`（仓库根 `scripts/` 目录）真实参数为 `-EnvFile`（默认 `../backend/xlumen-server/config/.env`），脚本直接解析 `.env` 的 `KEY=VALUE` 行（跳过 `#` 注释），读取 `XLUMEN_DB_URL`/`XLUMEN_DB_USERNAME`/`XLUMEN_DB_PASSWORD` 后按编号顺序执行 `backend/xlumen-server/sql/init/` 全部脚本；`-Reset` 要求数据库名必须是个人开发库或 `xlumen_test`，执行前显示服务器地址和数据库名并要求二次确认；禁止对共享或正式数据执行重置。该脚本随 M01 代码骨架落地。

## 18. 性能编码规范

- **N+1 查询禁止**：循环内逐条查库禁止；列表场景用 `IN` 批量查询或联表一次取回，一对多结构用两次查询组装，不用嵌套循环查 Mapper。
- **分页上限**：列表分页统一 `pageNo`/`pageSize`，`pageSize` 上限 100；超出按上限截断并返回提示。
- **深分页**：禁止大 `OFFSET` 深翻页（如超过 1 万行）；大结果集用游标分页（以稳定唯一键或时间定位，配合唯一排序），或走索引覆盖。
- **连接池**：连接池参数按并发假设配置（见 PRODUCT 第 11 节 NFR），明确初始/最小/最大连接数与获取超时，避免连接池耗尽；长事务禁止占用连接等待外部调用。
- **慢查询治理**：开启 MySQL 慢查询日志（阈值建议 ≤ 1s），慢查询必须走执行计划审查索引；状态类高频查询优先覆盖索引（见 8.2）。
- **SSE 长连接治理**：SSE 连接数按用户/空间限制上限；空闲连接定时心跳（如 30s ping）；流式输出按 `sequence` 编号，断线重连时客户端携带 `lastSequence`，服务端从断点续传，避免整篇重推。

## 19. 测试策略

| 层级 | 工具 | 重点 |
| --- | --- | --- |
| 单元测试 | JUnit 5、AssertJ、Mockito | Service 规则、状态转换、路由和幂等 |
| 模块集成测试 | Spring Boot Test | Controller、Service、事务、权限和 Mapper |
| 外部服务测试 | WireMock | 模型、搜索、邮件和发布渠道 |
| 边界测试 | ArchUnit | 禁止跨模块访问 Mapper 和 Entity |
| 压力测试 | k6 | REST、SSE、检索和任务提交 |

- 普通质量门禁运行单元测试、静态检查、模块边界测试和不依赖外部中间件的测试；完整集成测试使用 `full-it` Profile 连接测试服务器的中间件和 `xlumen_test` 数据库，启动时拒绝数据库名不是 `xlumen_test` 的配置。命令见 GLOBAL.md，本文档不重复。
- 最高优先级场景：多租户隔离、知识版本冲突、库级可见性越权、AI 任务恢复、消息重投、发布幂等、配额结算、模型降级、权限变化和会话撤销。
- 普通测试使用 WireMock 或固定响应替代付费服务，不能产生真实费用。

## 20. AI 代码生成指南

AI 每次只实现一个可验证的纵向功能，执行顺序为：

1. 阅读产品设计、本后端文档和全局本地运行说明。
2. 确认功能所属 Maven 模块、表前缀和是否需要对外 Api（以 PRODUCT.md 第 5 节总表 F-xxx 为准）。
3. 创建或更新所属模块初始化 SQL（按第 7 节编号契约）。
4. 实现 Entity、Mapper、Service 接口与 Impl。
5. 需要跨模块调用时实现 Api 和 ApiImpl。
6. 实现 Controller、DTO、VO 和必要转换。
7. 为每个 Java 类型补充符合 16.3 的类注释。
8. 补充单元测试、集成测试和接口文档。
9. 运行模块测试，再运行完整质量门禁（命令见 GLOBAL.md）。

AI 开始编码前应检查当前环境可用 Skills；存在适合 Java、Spring、数据库、测试或安全任务的 Skill 时可以使用，但 Skill 不能覆盖本文约束。

AI 禁止：

- 引入本文没有确定的新框架或复杂架构模式。
- 一次生成整个系统后再集中修错。
- 虚构依赖、配置项、表或接口。
- 绕过编译、测试、静态检查和失败用例。
- 为展示设计能力添加无实际需求的抽象层。

## 21. 备份与恢复

- MVP 阶段不提供备份恢复脚本，数据库与对象存储备份由运行环境/运维手动处理；**备份恢复脚本 V2 补充**。
- 方案要点（V2 落地时遵循）：备份 MySQL 与 MinIO，恢复会覆盖目标数据库，执行前必须确认数据库名称和备份文件；恢复后检查关键表数量、文件引用和核心接口；备份文件存放在项目目录之外。

## 22. 质量门禁与完成定义

- 质量门禁命令见 GLOBAL.md，本文档不重复命令。
- 一个后端功能完成必须满足：

1. 模块归属、表归属、Api 边界和事务边界明确。
2. 权限、租户、幂等、并发和异常路径已处理（双层校验、409 版本冲突、429 字面值）。
3. Controller、Service、Mapper、Entity、DTO 和 VO 职责清晰。
4. API、业务事件和错误码具有稳定契约（OpenAPI 唯一来源，决策 D4）。
5. 日志和 Trace 能定位问题且不泄露敏感数据。
6. 单元测试、集成测试和关键异常测试通过。
7. 初始化 SQL、`.env` 配置和 OpenAPI 已同步更新。

> 产品侧完成定义（含文档同步要求）见 PRODUCT.md 第 12 节；内容状态机文字版见 PRODUCT.md 第 4 节，状态转换逻辑集中在所属 Service 实现中，Controller、Job 和消息消费者不能复制状态判断逻辑。
