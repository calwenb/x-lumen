# E2E 重放报告 · 2026-08-21

## 环境

- 后端 :8080 — `actuator/health` 返回 200，已运行（最新代码，IDEAs 8-20 批次已落地）
- 博客前端 :5173 — 200（Vite dev，PID 25840，单 worker 启动可复用）
- 管理后台前端 :5174 — 200（Vite dev，PID 14476/19052）
- 测试账号 `qa_fulltest_20260821 / Test123456` 已就绪（实际未在本批测试中直接登录，所有用例走随机用户名或本地 mock session）
- Playwright Chromium，单 worker / spec（隔离执行）；并发 5 worker 全量博客套件复测 7.0s

## 套件汇总

| 套件 | 路径 | 总用例 | 通过 | 失败 | 耗时 |
| ---- | ---- | ------ | ---- | ---- | ---- |
| blog / smoke | `frontend/xlumen-frontend-blog/e2e/smoke.spec.ts` | 1 | 1 | 0 | 1.2s |
| blog / auth | `frontend/xlumen-frontend-blog/e2e/auth.spec.ts` | 1 | 1 | 0 | 2.0s |
| blog / enhancements | `frontend/xlumen-frontend-blog/e2e/enhancements.spec.ts` | 1 | 1 | 0 | 6.2s |
| blog / knowledge-publish | `frontend/xlumen-frontend-blog/e2e/knowledge-publish.spec.ts` | 1 | 1 | 0 | 2.5s |
| blog / public | `frontend/xlumen-frontend-blog/e2e/public.spec.ts` | 6 | 6 | 0 | 4.7s |
| admin / smoke | `frontend/xlumen-frontend-admin/e2e/smoke.spec.ts` | 1 | 1 | 0 | 1.2s |
| **合计** | — | **11** | **11** | **0** | **17.8s（串行） / 7.0s（并发 5 worker）** |

**通过率：11/11 = 100%**

## 失败用例详情

无失败。

## 通过用例（异常耗时 / 警告）

- `enhancements.spec.ts` 单用例耗时 6.2s，是其它单用例（1–2s）的 3–6 倍。原因：覆盖范围最广，包含注册 → 创作中心导航 → 收藏空态 → 公开列表 HTTP 请求 → 详情页赞/踩互斥 → 收藏 toggle → 退出登录等 8 步交互，外加 90s 超时预算。属于正常负载，非警告。
- `public.spec.ts` 套件 6 个用例在并发 worker 下相互独立（每个用例只 GET 公开数据 + 渲染断言），无串行依赖，可安全并行。
- `knowledge-publish.spec.ts` 走 `addInitScript` 注入 `xlumen.session` localStorage 与 `page.route` 拦截 `/api/v1/studio/knowledge/*` 请求，**不实际调用后端**；验证 UI 层 publish 链路 + AI 建议确认对话框。
- `auth.spec.ts` 中用户名采用 `pw_${Date.now().toString(36)}` 随机生成，注册成功后即登录、退出、再进登录页。`qa_fulltest_20260821` 账号未参与本次回归。

## 复现命令（留存）

```powershell
# 单套件（推荐调试用）
cd frontend/xlumen-frontend-blog
npx playwright test e2e/public.spec.ts --reporter=line

# 全量博客
cd frontend/xlumen-frontend-blog
npx playwright test --reporter=line

# 管理后台
cd frontend/xlumen-frontend-admin
npx playwright test --reporter=line
```

Playwright 配置：`reuseExistingServer: !process.env.CI` → 本地命中现有 :5173 / :5174 不会重复拉起。无需手工清理残留端口。

## 结论

- 6 个 E2E 套件共 11 用例全部通过，回归防护网当前为绿。
- 无截图落盘（无失败用例）。
- 无 BUGS 候选、无任务清单断言偏差；测试假设（路由 `/studio` `/favorites`、标题「全部知识库」、公开知识标题「Spring Boot 4 模块化单体实践 / RAG 检索增强生成入门 / Vue 3 组合式 API 设计心得」、公开知识 ID `2091000000000000001` 等）与后端 + 前端实际渲染一致。
- 后续 IDEAs 批次回归可直接复用本报告模板。
