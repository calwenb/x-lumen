# xLumen

xLumen 是多用户 AI 知识平台：注册用户创建自己的知识库（公开/私有），用多级目录组织知识（即文章），前台主页为知识列表页（按身份聚合，公开库知识带库标记），AI 对话（AI小光）为前台菜单入口（RAG 按身份检索可见知识库生成回答并引用来源），知识的创建、编辑、发布、阅读与互动全部在前台完成；围绕"输入 → AI 写作 → 审校 → AI 自动审核 → 发布（发布即索引，按知识库切分）→ 反馈 → 更新"内容闭环，AI 直接输出完整知识并配合知识对话与内容增值能力；管理后台仅供管理员进行空间、模型与审计等配置管理。

> **本仓库专属**。

## AI 开发者入口

本仓库由多个 AI 工具协作开发，上下文同步以 [`docs/ai/`](docs/ai/) 为准。**任何 AI 工具开始工作前必须通读 [开发状态与交接文档](docs/ai/STATUS.md)**（强制工作流：阅读 → 认领 → 实现与验证 → 收尾），结束时更新该文件，并在 [AI 变更日志](docs/ai/CHANGELOG.md) 顶部追加本次会话条目。

## 文档导航

完整文档导航表（各文档的文件、职责与权威范围，AI 代理/人类两条阅读路径，冲突裁决规则与引用不复制原则）见 [全局文档](docs/global/GLOBAL.md) 第 2 节。

## 已实现

文档体系 11 份（产品/全局/后端/前端/原型 + AI 协作五件套 STATUS/CHANGELOG/BUGS/IDEAS/QA），各文档职责与权威范围见 [全局文档](docs/global/GLOBAL.md) 第 2 节。

- [产品设计文档](docs/product/PRODUCT.md)：产品定位、13 模块 90 项功能总表（唯一功能事实源，MVP 47 / V2 41 / V3 2）、行为规则与完成定义
- [全局文档](docs/global/GLOBAL.md)：总体架构、仓库结构、技术基线、本地运行与质量门禁命令
- [后端开发文档](docs/backend/BACKEND.md)：7 个 Maven 模块、SQL 编号契约（00~95）、MVC 规则与编码规范
- [前端开发文档](docs/frontend/FRONTEND.md)：blog/admin 双应用工程结构、状态管理、接口与视觉规范
- [前端原型文档](docs/frontend/PROTOTYPE.md)：B/A/D 页面清单、线框与交互流程
- [开发状态与交接文档](docs/ai/STATUS.md)：强制工作流、能力基线、待办认领、决策 D1~D18
- [AI 变更日志](docs/ai/CHANGELOG.md)：按会话倒序的变更记录
- [待修问题清单](docs/ai/BUGS.md)：用户自测缺陷记录（仅按明确要求修复）
- [功能想法池](docs/ai/IDEAS.md)：用户功能想法收集与评估状态（采纳须登记 PRODUCT 功能总表）
- [AI 浏览器测试指南](docs/ai/QA.md)：用户发起、AI 代理 browser-use 浏览器测试的发起模式、环境自检与结果流转

代码交付状态（进行中状态以 [开发状态与交接文档](docs/ai/STATUS.md) 为准）：

- **MVP 全部 13 个里程碑已交付并通过运行时验证**（2026-08-12 ~ 2026-08-13）：代码骨架、身份与多租户、博客公开页、内容管理、RAG 索引（Noop 降级待 Milvus 环境）、AI 基座/创作/对话/增值、审核发布、读者纠错、管理后台与热点缓存。
- **知识平台化重构 KB-1~KB-6 已全部交付**（2026-08-14）：文章->知识全量改名、知识库/目录/回收站、库级可见性与跨空间公开读、前端 8 屏、存量迁移与全量验收。
- 2026-08-16/17：全功能测试缺陷（BUG-3~11）与 BUG-002~005 统一修复、小光回答 Markdown 渲染；当前待办仅 OPT-1（虚拟线程评估）待认领。

## 本地快速开始

### 前置环境

| 工具 | 版本要求 | 用途 |
| --- | --- | --- |
| Git | 最新稳定版 | 版本控制 |
| JDK | 25（`JAVA_HOME` 必须指向 JDK 25） | 后端编译与运行 |
| Maven | 3.9 | 后端构建 |
| Node.js | 20+ | 前端运行 |
| pnpm | 9+ | 前端依赖管理（Monorepo） |
| MySQL 客户端 | 8.4 配套 | 数据库初始化与检查 |

> 本机不安装 yq：初始化脚本直接解析 `.env`（真实参数为 `-EnvFile`）。MySQL、Redis、RocketMQ、Milvus、MinIO 使用现有服务器实例，需提前取得地址、账号、密码与访问白名单。

### 配置准备

复制模板并填写真实值（`.env` 已加入 `.gitignore`，真实值不得提交；Windows 下必须以 UTF-8 无 BOM 编码保存）：

```powershell
Copy-Item backend/xlumen-server/config/.env.example backend/xlumen-server/config/.env
```

`application.yml` 通过 `spring.config.import` 加载 `.env`（`optional:file:config/.env[.properties]`，含 `../config/`、`backend/xlumen-server/config/` 相对路径回退），变量统一 `${XLUMEN_XXX}` 命名（如 `XLUMEN_DB_URL`、`XLUMEN_JWT_SECRET`、`XLUMEN_BAILIAN_API_KEY`）。

### 初始化数据库

```powershell
./scripts/init-db.ps1 -EnvFile "./backend/xlumen-server/config/.env"
```

`init-db.ps1` 真实参数为 `-EnvFile`（默认 `../backend/xlumen-server/config/.env`）：解析 `.env` 的 `KEY=VALUE` 行（跳过 `#` 注释），读取 `XLUMEN_DB_URL` / `XLUMEN_DB_USERNAME` / `XLUMEN_DB_PASSWORD`，按编号顺序执行 `backend/xlumen-server/sql/init/` 全部脚本。

### 启动后端

```powershell
cd backend/xlumen-server
mvn -pl xlumen-boot -am spring-boot:run
```

或打包后运行（默认地址 `http://localhost:8080`，健康检查 `http://localhost:8080/actuator/health`）：

```powershell
cd backend/xlumen-server
mvn -pl xlumen-boot -am package -DskipTests
java -jar xlumen-boot/target/xlumen-boot-*.jar
```

开发环境若端口残留，可在 `backend/xlumen-server/config/.env` 设置 `XLUMEN_DEV_PORT_GUARD=true`，启动时输入 `y` 确认结束占用进程；生产默认关闭。

### 启动前端

仓库根目录执行（根 package.json 通过 `pnpm --dir` 代理两应用脚本；首次运行先在根目录 `pnpm install`）：

```powershell
pnpm --dir frontend/xlumen-frontend-blog dev     # 博客前台（含创作中心） http://localhost:5173
pnpm --dir frontend/xlumen-frontend-admin dev    # 管理后台 http://localhost:5174
```

## 技术栈

Monorepo（pnpm 管理 xlumen-frontend-blog + xlumen-frontend-admin 双应用）：后端 Spring Boot 4.1.0 模块化单体（JDK 25 / Maven 3.9 / MyBatis-Plus 3.5.16 / Hutool 5.8.38），前端 Vue 3.5 + Vite 7 + TypeScript 5.8 strict（Element Plus / Pinia 3），中间件 MySQL 8.4 / Redis / RocketMQ / Milvus / MinIO。完整技术基线见 [全局文档](docs/global/GLOBAL.md) 第 5 节。
