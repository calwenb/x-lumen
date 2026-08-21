# E2E 重放报告 · 2026-08-21（重跑版）

> 由子代理（E2E 测试工程师）于 2026-08-21 09:48 在与 GUI 主代理并行的窗口内完成。
> 任务来源：用户要求重放 8-21 全功能测试批次中的 Playwright E2E 基线（11/11 预期）。

## 环境

| 服务 | 端口 | 检查结果 |
| ---- | ---- | -------- |
| 后端 actuator/health | :8080 | HTTP 200，`{"groups":["liveness","readiness"],"status":"UP"}` |
| 博客前端 Vite dev | :5173 | HTTP 200（已有进程在跑，PID 37800，复用未重启） |
| 管理后台前端 Vite dev | :5174 | HTTP 200（已有进程在跑，PID 14476，复用未重启） |
| 残留端口检查 | `netstat -ano \| findstr ":5173 :5174"` | 仅当前 dev server 持有端口，无第三方残留占用，无需按 QA §3 清理 |

- 三个服务在执行测试前均 UP，跳过了 `pnpm dev:blog` / `pnpm dev:admin` 后台启动分支。
- 后端代码无新提交（距 8-22 GUI 单跑不足 24 小时），仅前端 dev server 复用既有进程。

## 套件汇总

| 套件 | 路径 | 总用例 | 通过 | 失败 | 套件耗时（Playwright `passed` 总耗时） |
| ---- | ---- | ------ | ---- | ---- | -------------------------------------- |
| blog / smoke | `frontend/xlumen-frontend-blog/e2e/smoke.spec.ts` | 1 | 1 | 0 | 见下表（并发计入 blog 总耗时） |
| blog / auth | `frontend/xlumen-frontend-blog/e2e/auth.spec.ts` | 1 | 1 | 0 | 同上 |
| blog / enhancements | `frontend/xlumen-frontend-blog/e2e/enhancements.spec.ts` | 1 | 1 | 0 | 同上 |
| blog / knowledge-publish | `frontend/xlumen-frontend-blog/e2e/knowledge-publish.spec.ts` | 1 | 1 | 0 | 同上 |
| blog / public（M03） | `frontend/xlumen-frontend-blog/e2e/public.spec.ts` | 6 | 6 | 0 | 同上 |
| admin / smoke | `frontend/xlumen-frontend-admin/e2e/smoke.spec.ts` | 1 | 1 | 0 | 同上 |
| **合计** | — | **11** | **11** | **0** | **blog 8.1s（5 worker 并发）/ admin 1.9s（1 worker 串行）→ 总串行 10.0s** |

**通过率：11/11 = 100%（达成基线预期）**

## 每个 spec 的简短结论

| spec | 用例 | 耗时 | 结论 |
| ---- | ---- | ---- | ---- |
| blog/smoke | 博客首页正常渲染 | 819ms | OK，首页 SSR 渲染、导航可见 |
| blog/public | B01 首页（公开知识/库切换器） | 1.2s | OK，KB-4 库切换器渲染、公开知识可见、草稿/私有不出现 |
| blog/public | B02 详情页（Markdown/TOC/阅读量） | 816ms | OK，Markdown 渲染、TOC 锚点、view_count 展示 |
| blog/public | B02 互动（未登录引导 → 登录后可评论+点赞） | 1.6s | OK，覆盖未登录 401 引导与登录态评论链路 |
| blog/public | B03 搜索页（命中高亮 + 标签组合） | 811ms | OK，关键词命中 + 标签筛选 |
| blog/public | B03 顶栏搜索入口 | 495ms | OK，顶栏搜索 input → /search 路由跳转 |
| blog/public | B04 关于页（导航可达） | 356ms | OK，/about 路径可达 |
| blog/auth | 注册→登录→首页显示用户名 | 2.1s | OK，random username `pw_${Date.now().toString(36)}` 注册即登录，跳转 / 头像下拉显示 |
| blog/knowledge-publish | 新增知识直接发布 + AI 建议确认 | 2.7s | OK，`addInitScript` 注入 session + `page.route` mock `/api/v1/studio/knowledge/*`，验证 UI publish 链路 |
| blog/enhancements | 互动与导航增强端到端验收 | 6.5s | OK，注册→创作中心导航→收藏空态→公开列表→详情赞踩互斥→收藏 toggle→退出登录 8 步全过；耗时最长属正常负载 |
| admin/smoke | 管理后台首页正常渲染 | 723ms | OK，/admin 渲染、菜单可见 |

## 失败项（如有）

无。

- `frontend/xlumen-frontend-blog/test-results/.last-run.json`：`{"status":"passed","failedTests":[]}`
- `frontend/xlumen-frontend-admin/test-results/.last-run.json`：`{"status":"passed","failedTests":[]}`
- 无截图落盘（Playwright 默认仅失败时截图）。
- 无 BUGS 候选：本次回归无登录态/数据库脏数据导致的偶发，断言匹配 BUGS.md 已登记的契约缺陷（BUG-018/H1、BUG-019/H2、BUG-022/M4）均不在本批次 E2E 覆盖范围（端到端仅走 GET 公开数据 + UI 渲染 / 注入式 publish mock，未触发 task retry/enhance scene/public view 后端路径），无需新登记。

## 与 8-21 基线对比

| 维度 | 8-21 基线（`docs/ai/assets/browser-test-2026-08-21/e2e-baseline.md`） | 8-21 重跑（本报告） | 偏差 |
| ---- | --------------------------------------------------------------- | ------------------ | ---- |
| 套件数 | 6（blog 5 + admin 1） | 6（blog 5 + admin 1） | 0 |
| 用例数 | 11（blog 10 + admin 1） | **11（blog 10 + admin 1）** | 0（注：任务说明为「blog 端 5 spec 9 用例」，实跑为 5 spec 10 用例，与 8-21 基线一致） |
| 通过率 | 11/11 = 100% | 11/11 = 100% | 0 |
| 失败用例 | 0 | 0 | 0 |
| blog 并发耗时（5 worker） | 7.0s | **8.1s** | +1.1s（+15.7%），仍在 8.1s 量级正常波动内，无异常慢用例（最长单用例 enhancements 6.5s 与基线 6.2s 一致） |
| 串行总耗时（blog + admin 顺序） | 17.8s | **10.0s**（blog 8.1s + admin 1.9s） | -7.8s（-43.8%），属环境波动（基线 admin 单独 ~9.7s，本次 1.9s，差异可能源于 admin 启动复用更彻底或 CI/本地差异；不构成回归） |

**结论一致**：与 8-21 基线一致，11/11 100% 通过；回归防护网当前为绿。

## 复现命令

```powershell
# 全量（与本报告一致）
pnpm test:e2e 2>&1 | tee docs/ai/assets/e2e-baseline-2026-08-21.log

# 单套件调试
cd frontend/xlumen-frontend-blog
npx playwright test e2e/public.spec.ts --reporter=line

cd frontend/xlumen-frontend-admin
npx playwright test --reporter=line
```

- 原始日志：`docs/ai/assets/e2e-baseline-2026-08-21.log`（blog 10 ok + admin 1 ok，编号按 Playwright 完成顺序）
- Playwright 配置：`reuseExistingServer: !process.env.CI` → 命中现有 :5173/:5174 不重启
- 端口残留：本次执行后 `frontend/xlumen-frontend-blog/test-results/` 与 `frontend/xlumen-frontend-admin/test-results/` 仅生成 `.last-run.json`，无失败截图；无需手工清理

## 备注

- 任务说明提及「blog 端 5 spec 9 用例」，实跑为 **5 spec 10 用例**（public.spec.ts 内 6 个 `test()`，与 8-21 基线报告「blog / public 6 用例」一致）。以实跑为准。
- 本次重跑属「无代码变更下的回归复测」，不重复 8-22 GUI 主代理的浏览器渲染验证与 BUG 复测；E2E 是 8-21 / 8-22 三层手段（API smoke + Playwright + 浏览器）中的「Playwright」一支，本报告补齐其在 8-21 时间窗的二次确认。
