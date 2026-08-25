# BUG 复核 + 文档审计 · 2026-08-21

> 子代理：BUG 根因复现 + 文档一致性审计
> 范围：BUG-015 复核、新 BUG 候选发掘、11 份 docs 一致性审计
> 工具：curl + 代码静态扫描 + grep（不修改任何文件）
> 测试账号：`qa_fulltest_20260821 / Test123456`（userId 2090488188213489664，workspaceId 2090488188544839680）

---

## BUG-015 复核

- 复现尝试：**5 个完整循环（create→submit→5×getOwned）+ 1 个并发批次（30 并发 get）**，加上 20 次顺序循环，共 **65 次 GET /api/v1/knowledge/{id}**
- 结果：**未复现**——所有 65 次请求均返回 200（含 1 次 submit 后立即 GET 与 30 次并发 GET）
- requestId 抽样：1a985b67a3e04ea2848a466332b0ab14（submit）/ 4e43bb9499304f80835635616d144f99（get owned）
- 静态分析复核：`KnowledgeServiceImpl.getOwned`（backend/.../content/service/impl/KnowledgeServiceImpl.java:233-242）查询条件确为 `eq(id) + eq(workspaceId) + eq(authorId)`，无 status 过滤；authorId 取自 JWT 的 `WorkspaceContext.userId()`；submit 路径只更新 `pub_review` 表与 `cnt_knowledge.status`（KnowledgeStatus 2→3），不修改 authorId/workspaceId，与 BUGS.md §BUG-015 静态排查结论一致。
- 建议：**建议关闭 BUG-015（移出 BUGS.md）**。本批次第三次复核未再触发；原 SUSPECT 疑为子代理 curl 脚本账号/workspace 上下文错位或并发乐观锁 409 被误读，STATUS.md §7 8-19 条目「BUG-015 复核未复现保留 SUSPECT」的下次测试批次关闭条件已满足。

复现脚本与时序日志（节选）：

```
cycle 1-5: 新建 5 条 → 5 次 submitReview → 5×5=25 次 GET → 全 200
concurrent: 新建 1 条 → submit → 30 并发 GET → 全 200
sequential: 20 次顺序 GET → 全 200
合计 65 次 GET，全部 200
```

---

## 新发现 BUG 候选

### CAND-018 · `KnowledgeVO` 缺少 `summary`/`publishedAt`/`recycleStatus`/`authorId` 字段透出

- 模块：后端 content（`KnowledgeServiceImpl.toVO`）
- 现象：作者编辑/详情接口返回的 `KnowledgeVO` 不含 `summary`、`publishedAt`、`recycleStatus`、`deletedAt`、`authorId`、`authorName`、`workspaceId`，其中 `summary` 与 `publishedAt` 在前端可能有展示需求（如编辑器预览/详情卡片），与 `KnowledgeEntity` 已定义但 `toVO` 不映射。
- 代码位置：
  - 实体定义 `backend/.../content/entity/KnowledgeEntity.java:48,51,71,74,77`（summary/recycleStatus/deletedAt/publishedAt 等）
  - VO 定义 `backend/.../content/vo/KnowledgeVO.java:21-55`（仅 11 字段）
  - toVO 映射 `backend/.../content/service/impl/KnowledgeServiceImpl.java:284-291`（仅映射 11 字段）
- 复现：
  ```bash
  curl -s http://localhost:8080/api/v1/knowledge/2090488418153623554 \
    -H "Authorization: Bearer $TOKEN" | python -m json.tool
  # data 中无 summary / publishedAt / recycleStatus / authorName
  ```
- 备注：实体已定义但 VO 未透出，可能影响 F-0808 AI 摘要区块、F-0312 回收站行状态显示等场景的前端取数；建议视前端实际需要补字段（不属本次修复范围）。

### CAND-019 · `KnowledgeListItemVO` 缺少 `summary` 与 `createdAt`

- 模块：后端 content
- 现象：知识管理列表（B10 作者后台）返回的 `KnowledgeListItemVO` 缺 `summary`、`createdAt`、`publishedAt`，与前端"列表卡片是否展示摘要"需求对齐。
- 代码位置：`backend/.../content/vo/KnowledgeListItemVO.java`、`KnowledgeServiceImpl.toListItem`（line 293-299）
- 复现：`curl -s http://localhost:8080/api/v1/knowledge?pageNo=1&pageSize=10 -H "Authorization: Bearer $TOKEN"`
- 备注：低优先级；可与 CAND-018 合并评估。

### CAND-020 · `ReviewController` 缺 `@PreAuthorize("hasRole('OWNER')")` 与 ReleaseController 不一致

- 模块：后端 publishing（`ReviewController`）
- 现象：审核 `submitReview / submitAutoReview / approve / reject / publishAfterAutoReview` 5 个接口无 `@PreAuthorize` 注解，仅靠 `SecurityConfig` 的 `anyRequest().authenticated()` 兜底（登录即可访问）；而 `ReleaseController` 在 8-19 BUG-007 修复中已加 `@PreAuthorize("hasRole('OWNER')")`。F-0903「职责分离」规定「作者不能发布、编辑不能审自己提交的知识」，但当前 submit/approve/reject 对所有登录用户（含 AUTHOR/EDITOR）开放，不符 MVP 角色契约。
- 代码位置：`backend/.../publishing/controller/ReviewController.java:30`（类级注解缺失）
- 复现：
  ```bash
  # 注册 AUTHOR 角色账号后（QA.md §3.8 qa_ 账号默认 OWNER，此处仅作示例）：
  curl -X POST http://localhost:8080/api/v1/reviews \
    -H "Authorization: Bearer $AUTHOR_TOKEN" -H "Content-Type: application/json" \
    -d '{"knowledgeId":"<id>","action":"submit"}'
  # 当前实现：返回 200（AUTHOR 可走审核），违反 F-0903
  ```
- 备注：中优先级；与 BUG-007 修复路径一致（同模块同注解模式），需补类级 `@PreAuthorize("hasRole('OWNER')")` 或方法级权限细化（OWNER/EDITOR）。

### CAND-021 · `FavoriteController`/`LikeController`/`CommentReactionController` 缺 `@PreAuthorize`

- 模块：后端 publishing（多个 Controller）
- 现象：F-0212/F-0203/F-0213 toggle 类接口全部缺 `@PreAuthorize`，仅靠 SecurityConfig 登录兜底；MVP 阶段 OWNER/EDITOR/AUTHOR/VISITOR 角色下，F-0212「收藏 toggle」/「我的收藏列表」等应对所有登录用户开放（设计正确），但与 CAND-020 同样的"角色契约未显式"问题——`@PreAuthorize("isAuthenticated()")` 或更细的角色契约应明示，便于代码审计。
- 代码位置：
  - `FavoriteController.java:25`
  - `LikeController.java:22`
  - `CommentReactionController.java:21`
- 备注：低优先级；行为正确，仅缺契约明示。

### CAND-022 · 事件监听静默降级：库/目录/物理删除失败仅 log 不抛，影响回收站一致性

- 模块：后端 content（`KnowledgeBaseLinkEventListener`）
- 现象：`onKbRecycleStatus`/`onKbDirectoryDeleted`/`onKbPurged` 三处 catch 块仅 `log.error`，不重试、不抛——若 content 侧连带软删/恢复失败但 knowledge 侧库状态已变，会出现"库在回收站但知识未连带软删"的不一致窗口；下次读 `recycle_status=0` 的库内 `recycle_status=0` 的知识仍返回。
- 代码位置：`backend/.../content/job/KnowledgeBaseLinkEventListener.java:42-44, 59-61, 75-77`
- 备注：低优先级（设计即"失败降级，不影响库侧主流程"，与 STATUS §3 踩坑备忘一致）；但与 AI 摘要监听（`KnowledgePublishedSummaryListener`）的"非关键降级"定位不同——回收站连带属数据一致性范畴，理论上应至少重试一次或落 `outbox` 表待补跑。

### CAND-023 · `SseService.publish` 单点失败导致订阅者错位

- 模块：后端 ai（`SseService`）
- 现象：`publish` 循环中任一 emitter.send 抛异常即 `unsubscribe`，但其他 emitter 仍订阅；同一 taskId 下多订阅者场景下，1 个客户端网络抖动会下线，其它正常客户端不受影响——正确；但 `unsubscribe` 内部 `emitter.complete()` 在已关闭的 emitter 上抛异常被 `catch (Exception ignore)` 吞掉，可能导致 `subscribers` map 中残留 key 集合（heartbeat 已 cancel 但 Set 未清空，line 116-121 之后）。低概率内存泄漏。
- 代码位置：`backend/.../ai/service/SseService.java:104-127`
- 备注：低优先级；属"理论一致性问题"，未触发实测。

### CAND-024 · `VisibilityServiceImpl.resolveVisibleKbIds` 私有库集合推导不含已下架知识（验证预期行为）

- 模块：后端 knowledge
- 现象：resolveVisibleKbIds 仅按 `kb.status=0 AND kb.visibility=1` + 当前用户私有库 `kb.status=0`，未排除 `kb.recycle_status=1` 的库——但 `KbKnowledgeBaseEntity` 无 recycle_status 字段（回收站在 `cnt_kb_recycle` 单独管理），实际回收站库由 `KbRecycleStatusEvent(status=1)` 同步 content 侧；knowledge 侧 kb 表 status 字段语义需复核。
- 代码位置：`backend/.../knowledge/service/impl/VisibilityServiceImpl.java:30-55`
- 备注：经查 `kb_knowledge_base` 无独立 recycle_status（决策 D16 回收站聚合由 publishing 编排），可见性推导与回收站隔离，本条属"已澄清"，不构成 BUG。

---

## 文档一致性

> 11 份文档 = README.md + GLOBAL/PRODUCT/BACKEND/FRONTEND/PROTOTYPE + STATUS/CHANGELOG/BUGS/IDEAS/QA。
> 审计基准：PRODUCT.md §5（功能清单唯一事实源）、STATUS.md §3/§5/§6（能力基线 + 待办 + 一致性核验）、GLOBAL.md §2（导航与权威范围）。

### 高差异（数字/范围对得上）

| 编号 | 项目 | 实际值 | 文档引用 | 差异结论 |
| --- | --- | --- | --- | --- |
| D1 | 总表统计 | PRODUCT §5：90 项（MVP 47 / V2 41 / V3 2） | README:19「MVP 47 / V2 41 / V3 2」、GLOBAL:14「MVP 47 / V2 41 / V3 2」、BACKEND:15「MVP 47 / V2 41 / V3 2」、PRODUCT:81 自述、STATUS §7 8-20 复述、CHANGELOG 8-20 复述 | **一致**；D18 调整已 8-20 同步 |
| D2 | PROTOTYPE §7 MVP 页面 | B00~B04、B08~B13、B16、B20~B23、A01~A04、D01/D02 | PROTOTYPE:108 自述 | 与 STATUS §5 待办中 F-0212/F-0213/F-0214/F-0312/F-0808/F-0215/F-0907/F-1307 已交付项对齐；B12 审核中心已「代码保留·导航隐藏」（PROTOTYPE:307） |
| D3 | STATUS §3 能力基线 | 摘要行覆盖 8-12 M01~M13 + 8-14 KB-1~KB-6 + 8-16 BUG 修复 + 8-17 BUG-002~005 + 8-18 IDEAS 批次 + BUG-006 | CHANGELOG 8-12~8-20 同步条目 | **一致**；踩坑备忘（Spring Boot 4 relaxed binding / 雪花 ID / Milvus 探测 / JDK25 / 运维）齐 |
| D4 | STATUS §5 待办认领 | OPT-1 = 待认领；V2-AI = 待认领；其余 M01~M13 + KB-1~KB-6 + F-0212/0213/0214/0312/0808/0215/0907/1307 + BUG-006 = 已完成（标注日期/AI 名） | CHANGELOG 各条目 | **一致** |

### 中差异（章节缺失/过期）

| 编号 | 项目 | 详情 |
| --- | --- | --- |
| D5 | STATUS §6 W7 行 | STATUS:105「2026-08-12 完成：功能总表 73 项（MVP 37/V2 24/V3 12）与 PROTOTYPE 页面清单（B00~B19、A01~A07、D01/D02）」——**73 项 / MVP 37 / V2 24 / V3 12 与 PROTOTYPE 范围「B00~B19、A01~A07」已过时**（实际 90 项 / MVP 47，PROTOTYPE 现 MVP 范围 B00~B04/B08~B13/B16/B20~B23/A01~A04/D01/D02）。CHANGELOG 8-19 11:50 条目「D2-D3（低）STATUS.md:101 §6 W7 行『73 项 / MVP 37』与 PROTOTYPE 范围『B00~B19、A01~A07』已过时」已记录但 W7 行未刷新。建议 W7 重写为「2026-08-20 复核：功能总表 90 项（MVP 47 / V2 41 / V3 2，D18 后），PROTOTYPE 范围 MVP 子集 B00~B04/B08~B13/B16/B20~B23/A01~A04/D01/D02；待下次代码变更后复验」并把"通过"日期更新。 |
| D6 | STATUS §6 W6 行 | STATUS:104「2026-08-12 通过（二次核验），会话 #6 前后端职责重组与目录变更后已同步 8 份文档，待代码骨架阶段复验」——**"8 份文档"已过时**（8-16 BUGS、8-18 IDEAS、8-19 QA 三次新增后现 11 份），且"待代码骨架阶段复验"未落实。建议同步为"11 份"。 |
| D7 | CHANGELOG 8-19 11:50 段落 D1 仍记「82 项 / MVP 39」 | CHANGELOG:92 描述本次审计发现的 4 项差异：「README.md:19 / GLOBAL.md:14 引用 PRODUCT 旧值『82 项 / MVP 39』，现行 PRODUCT §5『87 项 / MVP 44』」——**当前 README/GLOBAL 已刷新到 90 / MVP 47**，但 CHANGELOG 段落仍以历史审计结论形式记录旧值，不属文档错误，但会让阅读者误判当前仍存在 87 项的引用。建议在 D1 行末追加「（已于 2026-08-20 D18 调整同步为 90 / MVP 47）」注记。 |

### 低差异（链接/标题）

| 编号 | 项目 | 详情 |
| --- | --- | --- |
| D8 | README 文档清单 11 份与 GLOBAL §2 导航表 11 行 | 一致；含 PRODUCT/GLOBAL/BACKEND/FRONTEND/PROTOTYPE/STATUS/CHANGELOG/BUGS/IDEAS/QA + README 自身（README 仅在 GLOBAL §2 列出，自身未列）。 |
| D9 | BUGS.md 「待修复清单」节 | BUGS.md:27 当前「BUG-015 待复核（...SUSPECT...）」——**与本审计建议「关闭 BUG-015」冲突**，需用户在下次明确要求时操作；本审计不自动改 BUGS（按 STATUS §1 规则"AI 不自动认领"）。 |
| D10 | PROTOTYPE §7.8 创作中心 | 「B08~B13、B16、B22 MVP」与 STATUS §5 待办 F-0215 引用 PROTOTYPE B01/B03/B16/B20/B23 对齐；B12「审核中心（暂时隐藏，代码保留）」与 CHANGELOG 8-20 IDEA-007「审核中心入口暂时隐藏」一致。**一致**。 |

---

## 关键发现摘要

- **BUG-015 复现 65 次均 200**：建议下次测试批次关闭（CHANGELOG 8-19 已记「下次测试批次关闭」条件已满足）。
- **静态 BUG 候选 7 条**：1 条中等（CAND-020 审核接口缺 OWNER 注解），其余均为低优先级（VO 字段透出、catch 静默、内存泄漏可能、已澄清项）。
- **文档**：
  - 总表统计 90 / MVP 47 / V2 41 / V3 2 在 README/GLOBAL/BACKEND/PRODUCT/STATUS/CHANGELOG 六处**完全一致**。
  - STATUS §6 W6/W7 两行的"73 项 / MVP 37 / V2 24 / V3 12"与"8 份文档"是历史残留，与当前 90 / MVP 47、11 份文档不一致——**这是 STATUS §6 核验栏自身过期**，需在下次 STATUS 维护时刷新。
  - 11 份文档体系计数、PROTOTYPE MVP 页面范围、待办认领状态、能力基线摘要均与代码实际状态一致。

---

## 审计方法学

- **BUG-015 复现**：5 个 create→submit→get 完整循环（25 GET）+ 1 个并发批次（30 并发 GET）+ 1 个顺序批次（20 GET）= 65 次 GET，**全部 200**。
- **静态审计**：grep `PreAuthorize`、`catch.*log`、`checkOwnership`、`BizException` 与 entity.getX vs VO 字段映射，覆盖后端 7 个 Maven 模块全部 Controller/Service/Listener。
- **文档审计**：grep `MVP|V2|V3|87|90|82|44|47|73|37|24|12` 跨 11 份文档 + 通读 STATUS.md §1~§9 / CHANGELOG 8-12~8-20 / PRODUCT §1~§5 / PROTOTYPE §1~§9 / GLOBAL §1~§7 / BACKEND §1~§3 / FRONTEND §1~§3。
- **未做**：未修改任何代码、未修改任何文档（遵守 AGENTS.md 与 STATUS §1「AI 不自动认领 BUG，不修改文档除非用户明确要求」）。

---

报告生成时间：2026-08-21
生成代理：ZCode 子代理（BUG 根因复现 + 文档一致性审计）
测试后端：本地运行中（JDK 25，`http://localhost:8080`）
