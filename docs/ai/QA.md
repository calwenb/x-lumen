# xLumen AI 浏览器测试指南（QA）

> 更新日期：2026/8/21
> **本仓库专属**。
> 适用范围：用户发起、AI 代理使用会话内置 browser-use 能力操作真实浏览器，对本地运行中的双前端与后端做黑盒测试（全功能巡检 / 指定模块 / 缺陷复现）的发起方式、环境自检、操作规范与结果流转。
> 引用原则：**引用不复制**——功能范围与验收基准以 PRODUCT.md 第 5 节总表与第 12 节完成定义为准，页面结构以 PROTOTYPE.md 为准，运行与质量门禁命令以 GLOBAL.md 第 6/7 节为准，缺陷记录约定以 BUGS.md 为准；本文不重复维护这些内容。

## 1. 定位与分工

AI 浏览器测试由**用户发起**（AI 不自行发起）：AI 用 browser-use 打开真实浏览器执行登录、点击、输入、滚动、截图等用户可见操作，对运行中的系统做探索性 / 验收性黑盒测试。与既有质量手段分工：

| 手段 | 定位 | 触发方式 |
| --- | --- | --- |
| 单元/集成测试（JUnit、Vitest） | 开发内建，覆盖业务规则与状态机 | 质量门禁（GLOBAL §7） |
| Playwright E2E（各应用 `e2e/*.spec.ts`） | 随代码提交的回归防护网，覆盖关键链路 | 质量门禁（`pnpm test:e2e`） |
| AI 浏览器测试（本文档） | 探索性 / 全功能 / 验收性黑盒测试，覆盖 E2E 盲区与视觉、交互细节 | 用户按需发起 |

铁律：

- AI 浏览器测试**不替代质量门禁**，测试通过不等于免跑 GLOBAL §7 命令。
- 发现的缺陷**不自动修复**：记入 BUGS.md，仅按用户明确要求修复（与 BUGS.md 约定一致）。
- 环境/配置原因导致的假缺陷（如中间件未就绪）必须先排除并注明依据，再决定是否记录。

## 2. 发起模式

| 模式 | 用户指令示例 | AI 范围推导 |
| --- | --- | --- |
| 全功能巡检 | 「做一轮全功能测试」 | PRODUCT §5 总表中全部已实现功能 + STATUS §3 能力基线，按第 5 节模块顺序逐项推进 |
| 指定模块/功能 | 「测知识库模块」「测 F-0212」「测 B20 库页」 | 对应 PRODUCT §5 模块行 + PROTOTYPE 页面小节 |
| 缺陷复现 | 「复现 BUG-007」「帮我看看这个问题」 | BUGS.md 对应小节的复现步骤与期望 |

## 3. 环境自检（开测前逐项确认，任一项不过先解决再开测）

1. **后端健康**：`http://localhost:8080/actuator/health` 返回 UP（启动命令见 GLOBAL §6.4；编译与运行 JAVA_HOME 必须指向 JDK 25）。
   开发环境如需自动处理端口冲突，复制 `.env.example` 后设置 `XLUMEN_DEV_PORT_GUARD=true`；守卫会展示 PID/进程名并等待输入 `y`，生产默认关闭。
2. **双前端可达**：blog `http://localhost:5173`、admin `http://localhost:5174`（命令见 GLOBAL §6.5）。
3. **端口陷阱**：Vite 端口被占用时自动递增（5174/5175/5176…），开测前确认浏览器访问的是当前实例端口；反复启停残留的旧 dev server 先清掉，防止测到旧代码。
4. **Redis**：本地须无密码启动（`.env` 密码为空；带密码启动会导致注册/登录 500）。
5. **向量库降级判定**：Milvus 不可用时检索以 NoopVectorStore 降级，「AI 对话无引用命中」可能是环境预期而非缺陷；检索恒空时先用知识索引状态接口与 reindex 补跑端点（BACKEND §10）区分 降级 / 环境问题 / 功能缺陷，必要时查后端日志。
6. **AI 真实调用**：AI 写作/审校/对话/摘要走真实供应商，有真实耗时与费用；纯阅读/互动链路尽量复用既有已发布知识，减少不必要的模型调用。
7. **测试账号与数据安全**：只用新建测试账号（推荐 `qa_` 前缀 + 时间戳，密码统一 `Test123456`）；注册即建空间并绑定 OWNER（决策 D9），同一账号可测 blog 全链路与 admin 四页；**不得读写用户真实账号的数据**；admin 写操作（模型配置、空间设置）影响全局，测后恢复原值或仅做表单校验不提交。
8. **已知问题**：先通读 BUGS.md 待修复清单，已知问题不重复报。
9. **请求路径分歧预判（2026-08-21 经验沉淀）**：写接口缺陷报告前必须先区分四类客户端编码路径，否则易误判「N/N 端点全失败」：
   - **浏览器 SPA JSON POST**：`Content-Type: application/json; charset=UTF-8` 是 axios/fetch 默认值，Jackson 按 UTF-8 解码 InputStream，**直接绕开 servlet 字符编码**——多数中文场景正常
   - **curl 默认无 charset**：`curl -d '{"title":"中文"}'` 不带 charset 头部，servlet 拿到 InputStream 后按容器默认 Latin-1 解码，UTF-8 多字节序列错位触发 `Invalid UTF-8 middle byte 0xd0`——BUG-028 的真实重灾区
   - **application/x-www-form-urlencoded**：经典表单提交，字段名/值按容器默认字符编码解码——受影响
   - **multipart/form-data**：二进制上传，文件名按 RFC 5987 + 容器默认编码，部分内部解析仍走默认字符编码
   - **结论**：发现「全局 14/14 中文写失败」类现象时，先用浏览器 GUI 路径复测一次；如果 GUI 路径成功说明 Jackson 默认 UTF-8 解码绕开 servlet 编码，**真实影响域只是无 charset 头部的客户端 / form-urlencoded / multipart 三路径**，不要按 14 端点全量回归，反而要按这三路径收敛

## 4. browser-use 操作规范（踩坑备忘）

- **登录态**：会话保存在内存（SPA 内导航不丢）；刷新令牌不持久化（M02 约束），**整页刷新或新开浏览器会话后必须重新登录**。
- **截图**：fullPage 截图在部分页面有平铺伪影，关键结论用当前视口或分区域截图；缺陷截图存 `docs/ai/assets/bugs/`（沿用既有目录）。
- **IAB 真实点击路径（2026-08-21 单浏览器回归沉淀）**：直接对 Element Plus / Vue 组件点击容易超时或被丢弃，按以下优先级尝试：
  1. **首选**：用 `dom_cua.get_visible_dom()` 拿 aria-snapshot，从中按 `role + name` 找 `ref`（形如 `e18`），再 `tab.click(ref)` —— 在 SPA 里成功率最高（实测 8-21 全程走此路径）
  2. **次选**：Playwright 的 `getByText` / `getByRole` / `locator`：`tab.playwright.getByRole("button", { name: "发布", exact: true })` 在某些组件上仍超时，可与 `tab.click` 路径互为补
  3. **不要用**：页面级 `playwright.evaluate` 写副作用（btn.click() / dispatchEvent）—— IAB 拒绝 `EvalError: Possible side-effect in debug-evaluate`，只能读路径（count / textContent / getAttribute / isVisible）
  4. **不要用**：`tab.cua.click({ x, y })` 视觉坐标点击——按钮命中区域可能在屏幕外或被遮挡，且无法触发 Element Plus 内部事件链
- **IAB 标签稳定性陷阱**：同一 tab 多次 `tab.goto` / `tab.click` 之后偶发「browser tab '...' could not be restored for activation」报错；遇到时**开新 `about:blank` tab + `tab.goto(targetUrl)` 跳到目标页**，不要反复重试坏 tab
- **登录点击成功路径**：ref 流程 `tab.dom_cua.get_visible_dom()` → 找 `role=button & name=登录` 的 `ref` → `tab.click(ref)`；Element Plus 的 `el-button` 通过这条路径稳定
- **表单提交**：若 `tab.click` 失败，**fallback 是 `playwright.getByLabel(...).press("Enter")`**——例如密码框回车提交
- **流式 SSE**（AI 对话 / 写作 / 摘要）：等待完成事件而非固定等待，真实模型响应 10 秒以上属正常，超时要放宽。
- **雪花 ID**：URL 与接口中的知识/库 ID 为超长数字串（后端序列化为 String），复现步骤必须复制完整 ID，不得手工截断。
- **页面级 fallback 验证**：测试私有资源访问（A 的私有 KB / 评论 / 知识）时，**先用同一浏览器匿名/换账号访问同一 URL**，确认是否是 404 / 403 / 静默回退到公共占位组件——后者极易被误判为「页面正常」（2026-08-21 BUG-030 教训）

## 5. 模块测试要点（入口速查）

> 只列测试入口与链路骨架；验收基准统一为 PRODUCT §12 完成定义（正常、空数据、错误、无权限、冲突五类状态均可理解、可恢复），页面行为细节以 PROTOTYPE 对应小节为准。

| 模块（PRODUCT §5） | 入口 | 核心链路 |
| --- | --- | --- |
| 身份与多租户 | blog `/login` | 注册（即登录、即建空间）-> 首页头像菜单 -> 登出 -> 再登录；admin 登录复用同一账号 |
| 博客公开阅读 | `/`、`/knowledge/:id`、`/search`、`/kb/:id`、`/knowledge-bases`、`/about` | 访客视角知识流按身份聚合（公开库 + 库 badge）、详情渲染与目录导航、搜索组合筛选、发现页、库页目录过滤 |
| 互动与反馈 | `/knowledge/:id`、`/favorites` | 知识赞/踩互斥、收藏 toggle、评论发表与评论赞踩、纠错匿名提交（同 IP 每分钟 1 条，超限 429）；B23 收藏页取消收藏即时移除 |
| 内容管理 | `/studio`、`/studio/knowledge`、`/studio/knowledge/new` 与 `/:id/edit` | 创作中心 -> 知识列表（自动瀑布流）-> 编辑器：选库/目录、自动保存、版本冲突 409、发布前自动 AI 审核 |
| 知识库体系 | `/studio/knowledge-bases`、`/studio/recycle-bin`、`/kb/:id`（访客态） | 建库（公开/私有）、目录树右键 增/删/改、删库连带回收站、回收站恢复与彻底删除（默认 30 天）；**访客直链 `/kb/{private_id}` 必测 fallback**（2026-08-21 BUG-030：应回 404 / 知识库不可访问，而非静默渲染"公开知识库"空页） |
| 审核与发布 | `/studio/knowledge/:id/edit`、`/studio/releases` | 编辑器发布触发 AI 审核：error/失败阻断，warning/info 作者确认后继续 -> 立即/定时发布；审核中心入口暂时隐藏 |
| AI 对话 | `/chat`、详情页「问这篇 AI」 | 流式回答、检索范围选择、引用编号展开溯源、知识级问答；访客受限预览引导登录 |
| AI 写作 | `/studio/writing` | topic/draft 输入 -> 结构化产出 -> 选库保存进内容管理 |
| AI 内容增值 | `/knowledge/:id` | 详情页 AI 摘要区块（发布事件异步生成；存量旧知识无摘要属预期） |
| RAG 索引 | 发布链路 + `/chat` | 发布即索引（按知识库切分）；引用命中验证见第 3 节第 5 条降级判定 |
| 管理后台 | admin `/settings`、`/models`、`/audit-logs` | OWNER/ADMIN 准入（非管理员被清出回登录页）、空间设置、模型配置（连通性测试会触发真实调用，谨慎）、审计日志检索 |
| 多用户可见性 | 两个测试账号切换 | 跨空间公开读：A 的公开库出现在 B 的发现页并可读；A 的私有库对 B 不可见 |

## 6. 结果记录与流转

- **缺陷**：按 BUGS.md 记录约定记入该清单（编号顺延不回收、模块归属、复现步骤、现象 vs 期望、截图相对路径）；AI 测试发现的问题同样遵守「仅按用户明确要求修复」。
- **会话结论**：按 CHANGELOG.md 头部模板在顶部追加一条，注明测试范围、通过项、新记缺陷编号、降级与排除项。
- **回归沉淀**：用户要求修复浏览器测试发现的缺陷时，修复会话应评估把该场景沉淀为 Playwright E2E 用例（2026-08-16 全功能测试曾暴露 E2E 验收盲区，教训见 STATUS §3 对应行）。
- **测试数据**：测试账号与产生的知识/库/评论可保留在 xlumen_dev，命名带 `qa_` 前缀便于识别与后续清理；清理须用户明确要求。
- **请求路径分歧溯源（2026-08-21 沉淀）**：写接口缺陷卡片必须附「客户端路径」字段（curl / 浏览器 / 表单 / 客户端 SDK），并在现象栏注明 `Content-Type` 与 charset 头部情况；如果通过浏览器 SPA 复测未触发，记录「GUI 路径未复现，仅 curl 默认无 charset 路径触发」——避免后续修复者按 14 端点全量回归浪费工时
- **前端 fallback/路由盲区补查**：每发现一个前端 fallback / 渲染空白类缺陷（如 BUG-029 注册路由、BUG-030 私有 KB），**在同次测试中做一次"邻近路径"扫查**（其他 SPA 路由 404、公开/私有权限边界、tab 切换的目标 URL），往往能一次抓出多条同类缺陷

## 7. 典型经验案例（避免重蹈覆辙）

### 7.1 2026-08-21 · 推翻"BUG-028 全局 14/14 失败"适用结论

**上下文**：8-22 全功能测试报告 P0 缺陷 BUG-028「所有 POST/PUT body 含中文均 400 Invalid UTF-8 middle byte 0xd0」，用了 14 端点全量回归的工作量预估。

**实际**：8-21 第三轮 GUI 路径全程成功发布中文知识「中文测试标题 BUG-028」——`POST /api/v1/knowledge`（保存）→ `POST /api/v1/knowledge/{id}/review`（AI 审核）→ `POST /api/v1/publishing/release`（发布）→ `GET /api/v1/admin/audit-logs`（副作用核对）全部 200。

**根因**：Jackson 默认按 UTF-8 解码 InputStream，**绕开 servlet 容器默认字符编码**。浏览器 SPA 的 `axios` 默认 `Content-Type: application/json; charset=UTF-8` 声明直接触发 Jackson 解码路径而绕开 servlet 字符编码；curl 默认无 charset 头部就把 InputStream 留给 servlet 容器按 Latin-1 解码，UTF-8 多字节序列错位触发 0xd0 报错。

**教训**：
- 发现「全局 N/N 失败」类现象时，必须**先用浏览器 GUI 路径复测一次**再下结论
- UI / API 缺陷报告要附「客户端路径 + Content-Type + charset 头部」三个字段，让后续修复者能精确收敛修复范围
- 影响域真实仅约 3 路径（form-urlencoded / multipart / curl 无 charset），不要按 14 端点全量回归

### 7.2 2026-08-21 · BUG-030 私有 KB 直链对访客静默回退

**上下文**：8-21 阶段 9 多用户可见性测试，登出态访问私有 KB `/kb/{private_id}` 期望见到 404 或「知识库不可访问」。

**实际**：页面渲染「公开知识库 / 公开 / 这个视图下还没有知识。」——URL 仍是 `/kb/{private_id}`，但 main 区被全局公共聚合占位组件替换，**无 404 / 无权限提示 / 无任何说明**。

**根因（推测）**：`/kb/[id].vue` 对 401 / 403 / 404 静默处理，转而渲染全局"公开知识库"占位组件；缺少 `response.code === 'NOT_FOUND' / 'FORBIDDEN'` 的显式错误页分支。

**教训**：
- 测试私有资源访问时，**URL 与 main 区内容一致性**必须对照检查——如果不一致说明 fallback 路径异常
- 凡是 fallback 缺陷（同次测试已发现 BUG-029 注册路由空白），要**邻近路径扫查**（其他 SPA 路由 404、权限边界、tab 切换目标 URL），可一次抓出多条同类缺陷
- 错误的 fallback 体验甚至比 404 更糟糕：用户分享私有 KB URL 时，对方打开见空白公开页误判系统故障

### 7.3 2026-08-21 · IAB 真实点击路径（DOM ref + tab.click）

**上下文**：8-22 报告 IAB 真实点击持续超时，穷尽 7 种方案后归类为「环境持久限制」，决定未来走 Playwright E2E 替代。

**实际**：8-21 第三轮通过 `tab.dom_cua.get_visible_dom()` 拿 aria-snapshot → 找 `role + name` 对应的 `ref`（形如 `e18`）→ `tab.click(ref)` 这条路径，**全程 10 个阶段 GUI 测试无失败**（含登录、编辑器、AI 审核弹窗、确认发布、管理后台、退出登录 6 个账号菜单等关键点击）。

**教训**：
- IAB 真实点击并非完全不可行，只是**要按 ref 路径走**——直接用 `tab.cua.click({ x, y })` 视觉坐标或 `evaluate` 写副作用都不行
- 页面级 `evaluate` 只能读路径（count / textContent / isVisible / getAttribute），**写路径必须走 `tab.click(ref)`**
- tab 多次导航后偶发「could not be restored」报错，**开新 about:blank tab + `tab.goto(targetUrl)` 跳到目标页**是稳定方案

### 7.4 2026-08-21 · AI 审核「建议阻断」与「确认发布」分离设计验证

**背景**：知识编辑器「发布」按钮触发 AI 审核，原设计假设审核可能 error 级阻断（不让发布）或 warning/info 级确认（弹窗让作者选择）。

**实测**：中文知识「中文测试标题 BUG-028」含技术编号 BUG-028，AI 审核返回 2 条非阻断建议：
1. 标题「中文测试标题 BUG-028」：建议移除技术性编号
2. 正文 `# 中文测试 BUG-028`：建议澄清上下文或去除编号

这两条建议都进「发布前提示」弹窗（dialog），有「返回修改 / 确认发布」两个按钮——**非阻断渲染确认是可读可恢复的 prompt**（符合 PRODUCT §12 完成定义中"五态可理解可恢复"）。

**教训**：AI 审核流的体验设计是合格的——error 级阻断 + warning/info 级确认弹窗，能让作者保留控制权。后续测试 AI 审核时重点关注：(1) error 级是否真的阻断；(2) warning 级是否真的非阻断且弹窗可读；(3) 弹窗按钮文案是否清晰（"返回修改" / "确认发布" 无歧义）。
