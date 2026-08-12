# xLumen AI 变更日志

> 更新日期：2026/8/12 19:50
> **本仓库专属**。
> 按时间倒序记录（最新在顶部），每次 AI 会话结束必须追加一条；代码与文档更新同一提交，禁止虚构进度。

条目模板（表格形式，追加时复制以下表头与分隔行，并置于本说明之下、所有旧条目之上）：

| 时间 | 变更内容 | 影响文档 | 决策摘要 |
| --- | --- | --- | --- |

说明：变更内容写模块/文件/接口级别的主要变更；影响文档列受影响的文档相对路径；决策摘要列相关决策编号（D1~D15，见 STATUS.md 第 8 节），无则写"无"；时间精确到分钟（yyyy/M/d HH:mm）。

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
