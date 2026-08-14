# xLumen AI 变更日志

> 更新日期：2026/8/13 14:00
> **本仓库专属**。
> 按时间倒序记录（最新在顶部），每次 AI 会话结束必须追加一条；代码与文档更新同一提交，禁止虚构进度。

条目模板（表格形式，追加时复制以下表头与分隔行，并置于本说明之下、所有旧条目之上）：

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |

说明：变更内容写模块/文件/接口级别的主要变更；影响文档列受影响的文档相对路径；决策摘要列相关决策编号（D1~D15，见 STATUS.md 第 8 节），无则写"无"；时间精确到分钟（yyyy/M/d HH:mm）。

## 2026/8/13 19:10 · Qoder 代理（修复测试数据乱码）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/13 19:10 | 修复开发库测试数据乱码：cnt_article 中 id=2087770970748989441 的文章标题「M04 ??????」与分类「??」（验证脚本插入的坏数据，非字符集问题），按确认方案改为「M04 发布验证文章」/「测试」；同步 pub_review（2 行）与 pub_release（1 行）的 article_title；验证：全库乱码残留 0、前端首页正常显示 | CHANGELOG | 无 |

## 2026/8/13 18:45 · Qoder 代理（前端 UI 升级：接入 Element Plus 统一双端风格）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/13 18:45 | 双前端 UI 精细打磨：①修复博客端 tokens.css 缺失 5 个 token（--xl-radius/--xl-radius-sm/--xl-bg-secondary/--xl-color-danger/success/warning）导致的 8 处样式失效（工作台直角、AI 气泡透明、徽标消失等）；②tokens.css 扩展阴影体系（--xl-shadow-sm/md/lg）与过渡 token（--xl-transition）；③Element Plus 全量接入（main.ts 注册）+ element-theme.css 将 --el-* 映射到 --xl-* 体系 + @element-plus/icons-vue 图标库（两前端同步）；④管理后台全面 EP 化（el-container/el-menu 侧栏、el-form 登录/设置、el-table 模型/审计 + el-dialog 详情 + el-pagination + el-skeleton）；⑤博客前台交互组件 EP 化（顶栏胶囊激活态+图标+Logo、登录 el-tabs/el-form、el-pagination、文章管理 el-select/el-tag/el-skeleton、聊天气泡修复+双头像、详情页正文卡片+TOC 滚动高亮、搜索/首页卡片化+标签胶囊+空态图标）；⑥原生 confirm/alert 全部替换为 ElMessageBox/ElMessage（5 文件 10 处）；⑦全局 :focus-visible 焦点环与 ::selection；验证：双前端 lint/stylelint/typecheck/build 全绿、单测 6 通过、E2E 9 通过（含登录/注册/评论/点赞流程）；修复 CRLF 行尾警告（本次改动文件）；构建产物主 chunk 因 EP 全量引入增至 ~1MB（MVP 可接受） | FRONTEND | 无 |

## 2026/8/13 14:00 · Qoder 代理（MVP 全量交付：M04~M13 + F-1301）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/13 14:00 | MVP 全部里程碑交付：M04 内容管理（CRUD+自动保存+乐观锁 version+8 状态机一次定版）；M06+M12 AI 基座（ModelGateway 供应商解析+熔断、AiTask 任务底座幂等+Redis 进度+SSE、场景配置表优先 .env 回退）；M07 AI 创作（写作异步任务+审校异源校验 checkHeterogeneous，默认写作 qwen-plus/审校 qwen-max）；M05 RAG（发布即索引：事件→切片→Embedding 32 片/批→kb_chunk/kb_index_version ACTIVATING→ACTIVE→STALE；NoopVectorStore 降级待 Milvus）；M08 AI 对话（小光 SSE chunk/citation/done+会话/消息落库+文章级问答）；M09 增值（摘要/SEO 结构化落库 ai_enhance_result）；M10 审核发布（双闸门 force_review 开关+驳回三要素+立即/定时发布 PublishJob 每分钟扫描+发布事件/审计/缓存失效）；M11 纠错（匿名提交+追踪号+Redis 限流 429）；F-1301 热点缓存（详情 cache-aside 空值哨兵+分类/标签聚合+evictAll+降级回源）；M13 管理后台（工作区设置/模型配置+连通性测试/审计日志 + admin 前端四页）；修复：Boot 4 .env 大写属性 relaxed binding 失效（AiProperties/KnowledgeAiProperties 改 @Value 显式绑定）、GlobalExceptionHandler 补 404/JSON 解析异常、ai 与 publishing 同名 Bean 显式命名、hutool-json 缺失、审校模型默认 qwen-max；验证：mvn verify BUILD SUCCESS+3 单测、双前端门禁全绿、运行时全链路（真实百炼审校/写作/摘要/SEO/Embedding/连通性测试、发布→索引→公开可见、定时发布到期执行、SSE 流式、匿名纠错+限流、Redis 缓存键落盘）；里程碑顺序变更（M05 后置、M06/M12 提前）已记录 | STATUS/BACKEND | D8（.env 唯一载体）/D13（发布即索引）/D14（小光命名）/D10（阶段调整） |

## 2026/8/13 01:20 · Qoder 代理（后端代码风格统一重构：传统 MVC 扁平化）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/13 01:20 | 后端代码风格统一重构——**去除“业务域”概念，统一传统 MVC 扁平包结构**：publishing 的 engagement 域拆为资源命名（EngagementController → CommentController + LikeController，EngagementService → CommentService + LikeService，接口 URL 不变）；identity 去 iam 域（controller/dto/entity/enums/mapper/service/vo 扁平化、WorkspaceApiImpl 移入 service/impl、TokenVO/UserProfileVO/WorkspaceVO 改 class+Lombok、测试同步 getter）；content 去 editor 域、api/dto 改 class+Lombok、ContentApi.listPublished 参数封装（ArticleQueryDTO 跨模块稳定类型）、ArticleMapper/ContentApiImpl 移位；修复历史遗留：identity 单行乱码损坏文件从 git HEAD 恢复重建（(wenhailong) 垃圾目录清理）、PublicArticleServiceImpl 适配新签名与 getter、CommentQueryDTO/ArticleQueryDTO 补 @Builder.Default；BACKEND §3/§4/§5/§7/§8 同步去域措辞 + §5.1 新增编码风格规范（参数封装 3~4 个上限、DTO/VO 统一 class+Lombok 带字段注释、命名按资源词、@Builder.Default 默认值）；验证：mvn verify BUILD SUCCESS + identity 3 单测通过（环境注意：编译需 JAVA_HOME 指向 JDK 25） | STATUS/BACKEND | D2（传统 MVC）/D15（模块内扁平结构） |

## 2026/8/12 21:40 · Qoder 代理（M03 博客前台公开页）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/12 21:40 | M03 博客前台公开页交付：F-0201 列表/详情（默认空间公开读、Markdown 渲染 markdown-it + DOMPurify XSS 清洗、标题目录导航、卡片阅读时间与互动统计）；F-0202 分类/标签/搜索（LIKE + JSON_CONTAINS/JSON_TABLE 聚合、组合筛选 + 命中高亮 + 服务端分页）；F-0203 评论/点赞/阅读量（eng_comment/eng_like 唯一键幂等切换、Redis setIfAbsent 24h 防刷 + view_count 原子自增）；40_content.sql（cnt_article）/ 60_engagement.sql 入库；B01~B04 四页 + 顶栏导航搜索；**雪花 ID 精度修复**：Long 全局序列化为 String（JacksonConfig，Jackson 3 tools.jackson 包），前端 ID string + 统计数值 API 层 Number()；LikeButton 乐观更新与 props 同步竞态修复；验证：接口全链路（含 404 过滤/防刷/点赞切换/未登录 401）+ E2E 8 个 + 门禁全绿 + 浏览器实测 | STATUS/BACKEND | D6（Redis 短期状态）/D9（默认空间） |

## 2026/8/12 19:50 · Qoder 代理（M02 身份与多租户）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/12 19:50 | M02 身份与多租户交付：F-0101 注册/登录/登出/刷新（JWT HS256 15 分钟短时效 + 刷新令牌 SHA-256 哈希存 Redis、GETDEL 轮换防重放、登出撤销；登录失败统一提示 + 300ms 统一延迟防枚举）；F-0102 注册即建空间（决策 D9）与工作空间查询；F-0103 五角色（OWNER/ADMIN/EDITOR/AUTHOR/VISITOR）体系入库 + JWT roles→ROLE 映射；F-0104 双层校验（Security 接口权限 + Service 资源归属，WorkspaceContext 来自 JWT claims）；10_identity.sql 新增 iam_role/iam_user/iam_workspace/iam_workspace_member 并入库；blog 前端 B08 登录注册页（/login）、会话 Store 原子操作、401 单飞刷新（/auth/ 豁免）、路由守卫、顶栏登录态；验证：后端 3 单测 + 接口全链路（含重放/撤销 401）+ E2E 2 个 + 浏览器实测截图全部通过 | STATUS | D9（注册即建空间） |

## 2026/8/12 19:10 · Qoder 代理（M01 代码骨架）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/12 19:10 | M01 代码骨架交付：后端 backend/xlumen-server 父 POM + 7 模块（common 基座 ApiResponse/ErrorCode/BizException/RequestId(+Filter)/WorkspaceContext，业务模块按 BACKEND §4 依赖 DAG，boot 装配含全局异常处理/MyBatis-Plus 分页/探活接口）；配置体系 .env.example/.env + application.yml（spring.config.import 加载）+ logback-spring.xml（Appender 显式激活、级别走 XLUMEN_LOG_LEVEL）；SQL 初始化链路 sql/init 00~80（编号契约，90/95 随 V2/V3）+ scripts/init-db.ps1（-EnvFile/-Reset 二次确认）；前端 pnpm Monorepo 双应用（blog :5173 10 模块目录 / admin :5174 5 模块目录，Vue3.5+Vite7+TS5.8 strict+ESLint9 flat+Stylelint+Vitest+Playwright+Design Token）；根 package.json 代理脚本/pnpm-workspace/.editorconfig/.gitignore。验证：JDK 25 下 mvn clean verify 通过；应用启动连接 MySQL（159.75.6.183/xlumen_dev 已初始化）与 Redis 健康 UP、/api/v1/system/ping 返回统一响应；前端 lint/stylelint/typecheck/test/build/test:e2e 双应用全部通过 | STATUS/README/GLOBAL | D1/D7/D8/D12/D15 |

## 2026/8/12 18:03 · Qoder 代理（Maven 模块压缩 12→7）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/12 18:03 | Maven 模块 12→7：按未来微服务拆分边界合并——identity（含 platform 域）、content（含 analytics 域）、publishing（含 engagement 域）、knowledge、ai（含 chat/enhance 域）+ common/boot；模块内按业务域分包（分包边界即未来拆分边界），表前缀全部保留；BACKEND §3/§4/§5 模块表与依赖 DAG 重写、§7/§8 表归属同步；GLOBAL 架构图与结构树、FRONTEND 前后端模块映射、README 同步 | BACKEND/GLOBAL/FRONTEND/README/STATUS | D15 新增 |

## 2026/8/12 17:58 · Qoder 代理（菜单标签调整：AI 助理）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/12 17:58 | 前台导航 F-0701 菜单标签由 [AI 对话] 改为 [AI 助理]，位置调整为首页右侧、分类左侧（导航顺序：首页 / AI 助理 / 分类 / 标签 / 关于）；PROTOTYPE §3/§5.1/B00/B01/B09/D01 线框与描述同步；FRONTEND 应用边界与模块表同步（功能名 F-0701 仍为 AI 对话，仅菜单标签变更） | PROTOTYPE/FRONTEND | 无 |

## 2026/8/12 17:51 · Qoder 代理（AI 命名确定：小光）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/12 17:51 | 产品内所有面向用户的 AI 能力（AI 对话、文章级问答、访客助手、AI 写作与审校反馈等）统一称呼为**小光**；PRODUCT §8 新增 AI 命名规则；PROTOTYPE B00 欢迎语、B07 访客助手、D01/D02 弹窗标题文案同步 | PRODUCT/PROTOTYPE/STATUS | D14 新增 |

## 2026/8/12 17:46 · Qoder 代理（前后端职责重组 + 发布即索引 + 目录结构重组）

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |
| 2026/8/12 17:46 | 应用职责重组：blog（:5173）承载文章创建/编辑/发布/阅读/互动与 AI 对话全链路（含创作中心 B08~B13）；web 管理后台改为 admin（:5174）仅管理员配置管理（空间/成员/角色、模型配置、审计日志），不参与内容流转 | PRODUCT/PROTOTYPE/FRONTEND/GLOBAL/README/STATUS | D11 新增 |
| 2026/8/12 17:46 | 主页调整：B01 博客首页恢复为产品主页（文章展示），B00 降为前台 [AI 对话] 菜单入口页；PROTOTYPE 页面清单重组（B00~B19、A01~A07、D01/D02，验收清单同步） | PROTOTYPE/GLOBAL/PRODUCT | F-0701 定位调整 |
| 2026/8/12 17:46 | 功能总表重构：移除 F-0401 资料导入与 F-0406 URL 安全；F-0402 改为文章自动索引流水线（发布触发）；新增 F-0307 文章可见性（公开/私有，私有仅博主可见但建索引）；F-1201/F-1202 由 V2 提升 MVP；统计 74→73 项（MVP 37 / V2 24 / V3 12）；模块四更名"文章知识索引（RAG）" | PRODUCT/BACKEND/GLOBAL/README/STATUS | D10 阶段调整、D13 新增 |
| 2026/8/12 17:46 | RAG 检索规则：检索按身份过滤（访客仅公开已发布、博主全部含私有）；引用溯源改为篇名/段落定位可跳转原文；PRODUCT §3 业务闭环改为三主线（新增主线三反馈与保鲜闭环） | PRODUCT/BACKEND | D13 |
| 2026/8/12 17:46 | 仓库目录重组：server/→backend/xlumen-server/，web/→frontend/xlumen-frontend-admin/，blog/→frontend/xlumen-frontend-blog/；scripts、package.json、pnpm-workspace.yaml 等工程文件留仓库根；GLOBAL/README 全部运行与质量门禁命令路径同步（两文档逐字同源） | GLOBAL/BACKEND/FRONTEND/README/STATUS | D12 新增 |
| 2026/8/12 17:46 | BACKEND §13 重写为"文章知识索引与 RAG"（发布触发流水线）；kb_document/kb_snapshot 表移除，保留 kb_chunk/kb_index_version；STATUS 待办新增 M13（管理后台配置管理），决策新增 D11~D13 | BACKEND/STATUS | D11~D13 |
| 2026/8/12 17:46 | 技术约定补充：前端界面生成必须使用 frontend-design 类 Skill；前后端代码优先复用成熟工具类与开源框架（后端 Hutool、前端 VueUse/Element Plus）；代码必须有必要的注释（类/公共方法/复杂逻辑）；CHANGELOG 历史记录清空，仅保留本条，时间列改为精确到分钟 | FRONTEND/BACKEND/CHANGELOG | 无 |
