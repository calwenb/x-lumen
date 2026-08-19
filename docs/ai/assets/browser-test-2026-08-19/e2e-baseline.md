# E2E 基线（2026-08-19）

> 范围：仓库 `pnpm test:e2e` 全量基线（blog + admin 双前端 Playwright）
> 目标：建立当前 E2E 套件全绿基线，验证双前端 9~10 条用例无回归
> 不涉及：浏览器探索性测试、AI 缺陷复现、代码改动

## 1. 环境预检

| 项目 | 值 | 状态 |
| --- | --- | --- |
| blog (`http://localhost:5173/`) | 200 | UP |
| admin (`http://localhost:5174/`) | 200 | UP |
| backend (`http://localhost:8080/actuator/health`) | 200 | UP |
| 5173 LISTENING PID | 14300（主实例，保留） | 正常 |
| 5174 LISTENING PID | 2888（主实例，保留） | 正常 |
| 5175/5176 残留 | 仅 TIME_WAIT 客户端连接，无 LISTENING 进程 | 无残留 |
| 8080 LISTENING PID | 58300 | UP |

预检结论：双前端 + 后端均可达，端口环境干净，无 5175/5176 残留 dev server。

## 2. E2E 用例清单

### 2.1 blog（`frontend/xlumen-frontend-blog/e2e/`）

| spec 文件 | 用例数 | 覆盖 |
| --- | --- | --- |
| `smoke.spec.ts` | 1 | 首页标题「全部知识库」渲染 |
| `auth.spec.ts` | 1 | M02 注册即登录 → 头像菜单 → 登出 → 登录入口 |
| `public.spec.ts` | 6 | M03 B01 首页公开知识可见 / B03 搜索命中 + 高亮 + 标签筛选 / B02 详情页 Markdown + 目录 / 顶栏搜索入口 / B02 互动（未登录引导 + 登录后点赞评论）/ B04 关于页 |
| `enhancements.spec.ts` | 1 | IDEAS 批次（F-0212/0213/0214/0312）：注册 → 创作中心导航 → 我的收藏 → 公开知识赞/踩互斥 + 收藏 toggle → 评论点赞 → 目录树右键增/改/删 |
| **blog 小计** | **9** | — |

### 2.2 admin（`frontend/xlumen-frontend-admin/e2e/`）

| spec 文件 | 用例数 | 覆盖 |
| --- | --- | --- |
| `smoke.spec.ts` | 1 | 骨架冒烟：管理后台首页标题「xLumen 管理后台」渲染 |
| **admin 小计** | **1** | — |

### 2.3 合计

- blog：9 条
- admin：1 条
- **总计：10 条**

## 3. 跑测命令与输出

### 3.1 命令

```bash
cd D:/calwen/project/calwen/xlumen
pnpm test:e2e
```

`test:e2e` 在根 `package.json` 定义为 `pnpm --dir frontend/xlumen-frontend-blog test:e2e && pnpm --dir frontend/xlumen-frontend-admin test:e2e`，子命令均为 `playwright test`。

### 3.2 完整 stdout 末 50 行

```
> xlumen@0.1.0 test:e2e D:\calwen\project\calwen\xlumen
> pnpm --dir frontend/xlumen-frontend-blog test:e2e && pnpm --dir frontend/xlumen-frontend-admin test:e2e


> xlumen-frontend-blog@0.1.0 test:e2e D:\calwen\project\calwen\xlumen\frontend\xlumen-frontend-blog
> playwright test


Running 9 tests using 4 workers

  ok 2 [chromium] › e2e\smoke.spec.ts:4:1 › 博客首页正常渲染 (830ms)
  ok 1 [chromium] › e2e\public.spec.ts:6:3 › 博客前台公开页（M03） › B01 首页：展示公开知识，草稿/私有不出现，标签侧栏可用（KB-4 库切换器） (910ms)
  ok 5 [chromium] › e2e\public.spec.ts:21:3 › 博客前台公开页（M03） › B03 搜索页：关键词命中 + 高亮，标签组合筛选 (703ms)
  ok 3 [chromium] › e2e\auth.spec.ts:6:3 › 注册后登录进入博客首页并显示用户名 (1.8s)
  ok 6 [chromium] › e2e\public.spec.ts:34:3 › 博客前台公开页（M03） › B02 详情页：Markdown 渲染、目录导航、阅读量展示 (488ms)
  ok 7 [chromium] › e2e\public.spec.ts:48:3 › 博客前台公开页（M03） › B03 顶栏搜索入口：输入关键词跳转搜索页 (464ms)
  ok 8 [chromium] › e2e\public.spec.ts:57:3 › 博客前台公开页（M03） › B02 互动：未登录提示登录，登录后可评论与点赞 (1.6s)
  ok 9 [chromium] › e2e\public.spec.ts:97:3 › 博客前台公开页（M03） › B04 关于页：导航可达 (328ms)
  ok 4 [chromium] › e2e\enhancements.spec.ts:5:1 › 互动与导航增强端到端验收 (6.1s)

  9 passed (7.7s)

> xlumen-frontend-admin@0.1.0 test:e2e D:\calwen\project\calwen\xlumen\frontend\xlumen-frontend-admin
> playwright test


Running 1 test using 1 worker

  ok 1 [chromium] › e2e\smoke.spec.ts:4:1 › 管理后台首页正常渲染 (458ms)

  1 passed (1.6s)
```

### 3.3 关键耗时

- blog 套件：4 workers 并发，总耗时 7.7s（最慢用例 enhancements 6.1s）
- admin 套件：1 worker，总耗时 1.6s
- 两套合计墙钟时间：约 10s

## 4. 失败汇总

无失败。

| spec 文件 | 测试名 | 失败类型 | 关键错误 |
| --- | --- | --- | --- |
| — | — | — | — |

## 5. 后端日志核查

`backend/xlumen-server/logs/xlumen.log`（今日激活日志，共 5 行）：

```
2026-08-19 11:23:30.905  INFO  springdoc OpenAPI 初始化（746ms）
2026-08-19 11:25:08.983  INFO  KnowledgeBaseLinkEventListener 目录删除知识上挂完成
2026-08-19 11:25:08.983  INFO  DirectoryServiceImpl 目录已删除（子树知识上挂待 content 侧监听处理）
2026-08-19 11:25:26.201  INFO  KnowledgeBaseLinkEventListener 目录删除知识上挂完成
2026-08-19 11:25:26.201  INFO  DirectoryServiceImpl 目录已删除（子树知识上挂待 content 侧监听处理）
```

- `grep -E "ERROR|Exception"` 结果：**0 命中**
- 11:25:08 / 11:25:26 两条目录删除事件与 `enhancements.spec.ts` 中 F-0312 目录树右键删除节点动作时序吻合，为预期业务日志（INFO 级），非异常。
- 无 5xx、无 stacktrace、无 WARN。

## 6. 新发现 SUSPECT

无。

跑测过程中各测试断言均按既有规格（用户可见语义）通过；后端业务日志仅出现与 E2E 触发的删除事件对应的 INFO 条目，未观察到任何「预期外行为」需登记为 SUSPECT。

## 7. 总体结论

**全绿（10/10 通过，0 失败）**

- blog：9/9
- admin：1/1
- 后端日志干净，0 个 ERROR/Exception
- 端口环境干净，未发现 5175/5176 残留

可作为后续浏览器探索性测试与代码改动的「全绿基线」。
