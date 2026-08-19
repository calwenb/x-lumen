# BUG-007 根因复现报告

- 日期：2026-08-19
- 复现人：ZCode（浏览器测试子任务）
- 数据源：xlumen_dev（159.75.6.183:3306，root/WHLwhl123456.，见 `backend/xlumen-server/config/.env`）
- 后端状态：`http://localhost:8080/actuator/health` = UP（运行中，未重启）
- 范围：仅复现 + 根因定位，**不修复、不改 BUGS.md**（QA.md / BUGS.md 铁律）

## 一、复现步骤与 SQL 证据

### 1. 知识 2089895161090592769 主体数据

```sql
SELECT id, workspace_id, author_id, author_name, kb_id, directory_id, status, version,
       recycle_status, deleted_at, title, content, published_at, created_at, updated_at
FROM cnt_knowledge WHERE id = 2089895161090592769;
```

| 字段 | 值 | 解读 |
| --- | --- | --- |
| id | 2089895161090592769 | 雪花 ID（2026-08-19 10:00:09 UTC+8 生成） |
| workspace_id | 2087500158406074368 | 归属「qoder_test」工作空间（见 §3） |
| author_id | 2087500158078918656 | 作者 = qoder_test |
| author_name | qoder_test | 冗余展示字段 |
| **kb_id** | **2090000000000000001** | **「默认公开库」（qoder_test 空间的）** |
| directory_id | 0 | 库根目录 |
| title | java | 标题 |
| content | `javajavajavajavajavajava` | 调试用占位正文 |
| tags | `["java"]` | 单标签 |
| **status** | **4** | **「已通过」**（按 PRODUCT §4 状态机：`1 构思 / 2 草稿 / 3 待审核 / 4 已通过 / 5 定时发布 / 6 已发布 / 7 更新中 / 8 已下架`） |
| version | 2 | 乐观锁 |
| view_count | 0 | 阅读量 |
| recycle_status | 0 | 正常（不在回收站） |
| deleted_at | NULL | 未删除 |
| **published_at** | **NULL** | **从未发布**（已通过 ≠ 已发布） |
| created_at / updated_at | 2026-08-19 10:00:09 | 同时间创建并通过 |

附加佐证：存在 `pub_review` 记录（id=2089895165456863233，workspace=2087500158406074368，knowledge_title=java），说明走过了审核 → 通过流程，但未走到发布。

### 2. 默认公开库（kb_knowledge_base）

`kb_knowledge_base` 共 ~33 条「默认公开库」（多用户平台 D9 改写：每个工作空间一条默认公开库 + 一条默认私有库）。与目标知识匹配的那条：

| 字段 | 值 |
| --- | --- |
| id | 2090000000000000001 |
| workspace_id | 2087500158406074368（qoder_test 空间） |
| name | 默认公开库 |
| visibility | 1（公开） |
| status | 0（正常） |
| deleted_at | NULL |

→ 知识确实归属 qoder_test 空间的「默认公开库」（kb_id 匹配、库可见性 = 公开、状态 = 正常）。

### 3. owner 链路（与 BUGS.md 怀疑的「按 owner_user_id 推导」无关）

```sql
SELECT id, owner_user_id, name FROM iam_workspace WHERE owner_user_id = 2087500158078918656;
-- -> 2087500158406074368 | 2087500158078918656 | qoder_test
SELECT id, username, status FROM iam_user WHERE id = 2087500158078918656;
-- -> 2087500158078918656 | qoder_test | 1（正常）
```

owner_user_id 推导正常：`qoder_test (user=2087500158078918656) → workspace 2087500158406074368 → 默认公开库 2090000000000000001`。知识归属与可见库推导**全部正确**。

### 4. 知识 2089895161090592769 在 kb 内的状态分布

`cnt_knowledge` 中 `kb_id=2090000000000000001` 共 12 条：

| status | count | published_at |
| --- | --- | --- |
| 2（草稿） | 1 | NULL |
| 3（待审核） | 1 | NULL |
| **4（已通过）** | **5**（含目标 2089895161090592769） | **全 NULL** |
| 6（已发布） | 5 | 非 NULL |

「已通过」≠「已发布」：这 5 条知识（包括 2089895161090592769）走完了审核但没走到「发布」步骤，所以 published_at=NULL 且不进任何公开读路径。

## 二、curl 复现步骤

> 全部测试于 2026-08-19 11:33 在运行的 `http://localhost:8080` 上完成（健康 UP），未触发任何状态变更、未重启。

### A. 准备：登录 qoder_test（知识 owner）

```bash
curl -s -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"qoder_test","password":"Test123456"}'
# 200 SUCCESS → accessToken、workspaceId=2087500158406074368、userId=2087500158078918656
```

### B. 编辑器可读、公开读不可达

```bash
TOKEN=...   # qoder_test 的 accessToken
# 编辑器端（owner 鉴权通过）：
curl "http://localhost:8080/api/v1/knowledge/2089895161090592769" -H "Authorization: Bearer $TOKEN"
# {"code":"SUCCESS","data":{"id":"2089895161090592769","title":"java",...,"status":4,"version":"2",...}}

# 公开读端（owner 鉴权 + 知识 owner）：
curl "http://localhost:8080/api/v1/public/knowledge/2089895161090592769" -H "Authorization: Bearer $TOKEN"
# {"code":"NOT_FOUND","message":"知识不存在或未公开","data":null}
```

→ 复现了 BUG-007 现象：「编辑器可编辑 + 公开读查不到」。

### C. 默认公开库列表的过滤效果

| 调用 | total | 2089895161090592769 在结果中？ | 备注 |
| --- | --- | --- | --- |
| `GET /api/v1/knowledge?pageNo=1&pageSize=50`（qoder_test 鉴权，编辑端列表） | 14 | **是** | 编辑器按 workspace 聚合，status=4 在内 |
| `GET /api/v1/knowledge?kbId=2090000000000000001&pageNo=1&pageSize=50`（qoder_test 鉴权，编辑端按库筛） | 12 | **是** | 库内编辑态全可见 |
| `GET /api/v1/public/knowledge?kbId=2090000000000000001&pageNo=1&pageSize=50`（qoder_test 鉴权，公开读） | 5 | **否** | 5 条全是 status=6 已发布 |
| `GET /api/v1/public/knowledge?kbId=2090000000000000001&pageNo=1&pageSize=50`（无鉴权，访客） | 5 | **否** | 同上 |
| `GET /api/v1/public/knowledge?pageNo=1&pageSize=20`（无鉴客，访客） | 7 | **否** | 跨库已发布聚合 |

→ 公开读路径在 status=4 时**始终**过滤掉该知识，与 owner/库/可见性推导均无关。

## 三、代码路径根因定位

### 1. 可见库集合推导（`VisibilityServiceImpl`）—— **无 bug**

`backend/xlumen-server/xlumen-knowledge/src/main/java/com/calwen/xlumen/knowledge/service/impl/VisibilityServiceImpl.java`，第 31-55 行 `resolveVisibleKbIds(userId)`：

1. 先取 `status=0 AND visibility=1` 的全平台公开库（按 id 升序）。
2. 登录用户再 `+` 自身 workspace 下的所有 `status=0` 库（含私有库）。

**对 qoder_test（userId=2087500158078918656，workspace=2087500158406074368）**：
- 公开库集合：包含 `kb_id=2090000000000000001`（visibility=1，status=0），**正确**。
- 私有库集合：再加 workspace=2087500158406074368 下 status=0 的库。

→ 可见库集合推导**与 BUGS.md 怀疑的方向无关**，没有「漏掉默认公开库」或「按 owner 推导错」。

### 2. 公开读过滤（`ContentApiImpl.listPublished`）—— **真正的根因**

`backend/xlumen-server/xlumen-content/src/main/java/com/calwen/xlumen/content/service/impl/ContentApiImpl.java`，第 43-91 行 `listPublished(...)`：

```java
// 第 51-56 行（关键三行）：
LambdaQueryWrapper<KnowledgeEntity> wrapper = new LambdaQueryWrapper<KnowledgeEntity>()
        .eq(workspaceId != null, KnowledgeEntity::getWorkspaceId, workspaceId)
        .eq(KnowledgeEntity::getStatus, STATUS_PUBLISHED)        // ← STATUS_PUBLISHED = 6
        .eq(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_NORMAL)
        .in(KnowledgeEntity::getKbId, visibleKbIds);
...
// 第 35 行常量：
private static final int STATUS_PUBLISHED = 6;                    // 已发布
private static final int RECYCLE_STATUS_NORMAL = 0;
```

`listPublished` **只接受 `status=6`（已发布）**，等价于把 `status ∈ {1..5,7,8}` 的所有非已发布知识挡在公开读路径之外。

→ 2089895161090592769 的 `status=4`（已通过）+ `published_at=NULL` 命中该条件，被 `STATUS_PUBLISHED=6` 等值过滤掉。

### 3. 详情端 `getPublished` 同样根因

`ContentApiImpl.java` 第 94-117 行 `getPublished(...)` 同样 `.eq(getStatus, STATUS_PUBLISHED)`，所以 `GET /api/v1/public/knowledge/2089895161090592769` 直接 `return null` → 上层 `PublicKnowledgeServiceImpl.getKnowledge` 抛 `NOT_FOUND 知识不存在或未公开`（`PublicKnowledgeServiceImpl.java` 第 126-128 行）。

### 4. 编辑端可读（旁证）

`KnowledgeServiceImpl.get(...)` 走 `knowledgeMapper.selectById(2089895161090592769)`，**无 status 过滤**，所以 owner 在编辑器能正常打开并保存。这与 BUGS 描述「文章存在 + 可编辑」吻合，进一步证实是「状态机停在 status=4」而非「可见性推导错」。

## 四、根因结论

| 项 | 结论 |
| --- | --- |
| **根因** | 知识 2089895161090592769 当前处于「**已通过 (status=4) 但未发布 (published_at=NULL)**」的中间状态；`ContentApiImpl.listPublished` 与 `getPublished` 强制等值过滤 `status=6`，因此所有公开读接口（列表/详情/库内列表）一律不返回。 |
| **触发条件** | 用户走完「草稿 → 提交审核 → 审核通过」流程后，未点击「立即发布」或「定时发布」将状态推到 status=6；或（可能性更低）发布动作存在缺陷，未把 status 从 4 推到 6、未落 `published_at`、未触发 `KnowledgePublishedEvent`（KB-1 后）→ 索引/缓存/计数全空。 |
| **BUGS.md 原怀疑方向** | 「可见库集合按 owner_user_id 推导」—— **不成立**。owner / workspace / kb_id / visibility 全部正确，可见库集合 `resolveVisibleKbIds(qoder_test)` 确实包含 `2090000000000000001`。 |
| **影响面** | 知识在编辑器存在、创作中心列表、回收站等编辑态链路均可见；**所有公开读路径（公开列表 / 公开详情 / 知识库页 B20 / AI 对话 RAG 引用）均不可见**，等于"半发布"幽灵状态。 |
| **影响数量（DB 当前）** | `kb_id=2090000000000000001` 内 status=4 的知识 5 条（含目标），全部命中同样根因。 |

## 五、修复候选（**仅列，不动手**）

> QA.md 铁律：发现缺陷不自动修复，仅按用户明确要求；本节仅供用户决策时参考，不构成修复方案。

候选方向（需用户拍板，根因不同，方案不同）：

- **方向 A（产品行为问题，确认需求）**：补「立即发布」按钮流，把 status=4 推到 status=6 + 落 published_at + 触发发布事件与索引流水线。
- **方向 B（产品体验问题）**：B01 默认公开库列表/库页 B20 在知识「已通过但未发布」时给一条「待发布」角标（不真正公开，让用户知道为何不显示），消除「已通过却看不到」的认知差。
- **方向 C（防御性兜底）**：`listPublished`/`getPublished` 在 `status=4` 且 owner 鉴权时允许可见（库主自见）—— 但会破坏「公开读与编辑读严格分离」原则，需先产品评审。

> 实际修复路径与排期须经用户在 BUGS.md 上明确要求（`/bug-fix BUG-007` 或同等指令），并按 CHANGELOG 模板记入 2026/8/19+ 条目。

## 六、附：相关文件路径

- 复现代码：
  - `D:\calwen\project\calwen\xlumen\backend\xlumen-server\xlumen-knowledge\src\main\java\com\calwen\xlumen\knowledge\service\impl\VisibilityServiceImpl.java`（第 31-55 行）
  - `D:\calwen\project\calwen\xlumen\backend\xlumen-server\xlumen-content\src\main\java\com\calwen\xlumen\content\service\impl\ContentApiImpl.java`（第 35、43-56、94-105 行，根因所在）
  - `D:\calwen\project\calwen\xlumen\backend\xlumen-server\xlumen-publishing\src\main\java\com\calwen\xlumen\publishing\service\impl\PublicKnowledgeServiceImpl.java`（第 113-134、126-128 行 NOT_FOUND 抛出点）
- 知识原文：
  - `D:\calwen\project\calwen\xlumen\backend\xlumen-server\sql\init\40_content.sql`（`cnt_knowledge` 表结构，status 列注释定义 1~8 状态）
  - `D:\calwen\project\calwen\xlumen\backend\xlumen-server\sql\init\20_knowledge.sql`（`kb_knowledge_base` 表结构，visibility/status 列定义）
- 缺陷登记（**未改，仅引用**）：
  - `D:\calwen\project\calwen\xlumen\docs\ai\BUGS.md`（第 31-38 行 BUG-007 原文；本报告不写入 BUGS）
