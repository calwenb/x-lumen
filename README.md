# xLumen

xLumen 是多用户 AI 知识平台：注册用户创建自己的知识库（公开/私有），用多级目录组织知识（即文章），前台主页为知识列表页（按身份聚合，公开库知识带库标记），AI 对话（AI小光）为前台菜单入口（RAG 按身份检索可见知识库生成回答并引用来源），知识的创建、编辑、发布、阅读与互动全部在前台完成；围绕"输入 → AI 写作 → 审校 → AI 自动审核 → 发布（发布即索引，按知识库切分）→ 反馈 → 更新"内容闭环，AI 直接输出完整知识并配合知识对话与内容增值能力；管理后台仅供管理员进行空间、模型与审计等配置管理。

> **本仓库专属**。

## AI 开发者入口

本仓库由多个 AI 工具协作开发，上下文同步以 [`docs/ai/`](docs/ai/) 为准。**任何 AI 工具开始工作前必须通读 [开发状态与交接文档](docs/ai/STATUS.md)**（强制工作流：阅读 → 认领 → 实现与验证 → 收尾），结束时更新该文件，并在 [AI 变更日志](docs/ai/CHANGELOG.md) 顶部追加本次会话条目。

## 文档导航

完整文档导航表（各文档的文件、职责与权威范围，AI 代理/人类两条阅读路径，冲突裁决规则与引用不复制原则）见 [全局文档](docs/global/GLOBAL.md) 第 2 节。

## 文档与交付状态

文档体系 12 份（产品/全局/后端/前端/原型 + AI 协作 STATUS/CHANGELOG/CHANGELOG-ARCHIVE/BUGS/IDEAS/QA），各文档职责与权威范围见 [全局文档](docs/global/GLOBAL.md) 第 2 节：

- [产品设计文档](docs/product/PRODUCT.md)：13 模块 99 项功能总表（唯一功能事实源，MVP 47 / V2 27 / V3 25，V3 含 6 项暂缓）
- [全局文档](docs/global/GLOBAL.md)：总体架构、仓库结构、技术基线、本地运行与质量门禁命令
- [后端开发文档](docs/backend/BACKEND.md) / [前端开发文档](docs/frontend/FRONTEND.md) / [前端原型文档](docs/frontend/PROTOTYPE.md)：工程实现与页面规范
- [开发状态与交接文档](docs/ai/STATUS.md)：强制工作流、能力基线、待办认领、决策 D1~D19（开工前必读）
- [AI 变更日志](docs/ai/CHANGELOG.md)（最近约 14 天）+ [归档](docs/ai/CHANGELOG-ARCHIVE.md)：按会话倒序的变更记录
- [待修问题清单](docs/ai/BUGS.md)：用户自测缺陷记录（仅按明确要求修复）
- [功能想法池](docs/ai/IDEAS.md)：用户功能想法收集与评估状态（采纳须登记 PRODUCT 功能总表）
- [AI 浏览器测试指南](docs/ai/QA.md)：浏览器测试的发起模式、环境自检与结果流转

代码交付状态以 [开发状态与交接文档](docs/ai/STATUS.md) 为准：**MVP 全部 13 个里程碑（2026-08-12~13）与知识平台化重构 KB-1~KB-6（2026-08-14）已全部交付并通过运行时验证**；当前待办为 OPT-1（虚拟线程评估）与 V2 批次，见 STATUS 第 5 节。

## 本地快速开始

前置环境、配置准备（`.env`）、数据库初始化、后端启动（fat jar 方式，**不要**用 `mvn -pl xlumen-boot -am spring-boot:run`，原因见 GLOBAL §6.4）与双前端启动的完整命令与注意事项见 [全局文档](docs/global/GLOBAL.md) 第 6 节。

## 技术栈

Monorepo（pnpm 管理 blog + admin 双应用）：后端 Spring Boot 4.1.0 模块化单体（JDK 25 / Maven 3.9），前端 Vue 3.5 + Vite 7 + TypeScript 5.8 strict，中间件 MySQL 8.4 / Redis / RocketMQ / Milvus / MinIO。完整技术基线（依赖版本清单与测试栈）见 [全局文档](docs/global/GLOBAL.md) 第 5 节。
