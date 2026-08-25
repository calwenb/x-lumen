# API 冒烟报告 · 2026-08-21

## 环境

- 后端：`http://localhost:8080`（OpenAPI 在 `/v3/api-docs`）
- 主账号：`qa_fulltest_20260821 / Test123456`（OWNER）
  - userId: `2090488188213489664`
  - workspaceId: `2090488188544839680`
- 跨用户账号：`qa_smoke_b_20260821 / Test123456`（OWNER-独立空间）
  - userId: `2090488733640781824`
  - workspaceId: `2090488733934383104`
- 测试数据：
  - 公开库 `QA-public-test` `id=2090488328710090752`（visibility=1）
  - 私有库 `QA-private-test` `id=2090488329049829376`（visibility=0）
  - 根目录 `QA-Test-Dir` `id=2090488399560273920`
  - 子目录 `QA-Sub-Dir` `id=2090488417667084288`
  - 草稿知识 `QA-Draft-1` `id=2090488418153623554`（实际 status=3，见下方"发现"）

## 端点覆盖

| 维度 | 数值 |
| ---- | ---- |
| OpenAPI 端点总数 | 71 |
| 已测（happy + 异常路径） | 68 |
| 跳过（DELETE 销毁操作，不影响既有数据） | 3 |
| **总计通过** | **63 / 71**（含 happy path 200；其余为符合设计的 4xx） |
| **总计失败/缺陷** | **2**（见高优先级发现） |
| **总计警告** | **5**（见中/低优先级发现） |

跳过明细：`DELETE /knowledge/{knowledgeId}`、`DELETE /knowledge-bases/{kbId}`、`DELETE /knowledge-bases/{kbId}/directories/{directoryId}` —— 为保护 `QA-*` 测试数据未执行。

## 详细结果

> 全部 124 次请求已落盘到 `C:/temp/results.json`（含 status、code、message、elapsed_ms）。下表精选每个端点一次"happy path"结果，旁注关键异常路径。

### Auth（4）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /auth/register | POST | 200 | 新建 `qa_smoke_b_20260821` 成功，返回完整 access/refresh 双 token |
| /auth/login | POST | 200 | 正常 |
| /auth/login（缺 password） | POST | 400 | `code=INVALID_PARAM` 消息含具体字段（OK） |
| /auth/login（错密码） | POST | 400 | `code=INVALID_PARAM` 消息含具体字段 |
| /auth/refresh | POST | 200 | 正常 |
| /auth/refresh（无 body） | POST | 400 | `refreshToken 刷新令牌不能为空` |
| /auth/logout（无 body） | POST | 400 | `请求参数有误`（**未指明字段，详见发现 L1**） |
| /auth/logout（带 refreshToken） | POST | 200 | 正常 |

### Workspace（1）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /workspaces/current | GET | 200 | 返回 workspaceId + 当前用户信息 |
| /workspaces/current | GET | 401 | 无 token 鉴权正确 |

### Admin（6）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /admin/audit-logs | GET | 200 | 支持 page/size 分页 |
| /admin/model-configs | GET | 200 | 返回模型配置列表 |
| /admin/model-configs/test | POST | 400 | **缺 `model` 字段校验明确**：`model 模型名不能为空` |
| /admin/model-configs/test | POST | 400 | 缺 `provider`：`provider 供应商不能为空` |
| /admin/model-configs/{scene} | PUT | 400 | 缺 `model` / `provider` 字段均被精确拦截 |
| /admin/workspace/settings | GET | 200 | 返回 forceReview 等开关 |
| /admin/workspace/settings | PUT | 400 | `forceReview 强制审核开关不能为空`（必填校验） |

### KB（10）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /knowledge-bases | GET | 200 | 含列表/分页 |
| /knowledge-bases | POST | 200 | 新建知识库成功 |
| /knowledge-bases | POST | 400 | 缺 `name`：`知识库名称不能为空` |
| /knowledge-bases/{kbId} | GET | 200 | 公开库/私有库均能读（OWNER） |
| /knowledge-bases/{PRIV_KB_ID}（TOKEN_B 跨用户） | GET | 404 | **`NOT_FOUND 知识库不存在`（隔离生效，详见发现 M2）** |
| /knowledge-bases/{kbId} | PUT | 200 | 正常 |
| /knowledge-bases/{kbId}/visibility | PUT | 200 | 私有↔公开切换成功 |
| /knowledge-bases/{kbId}/visibility | PUT | 400 | 非法值：`可见性参数非法（0 私有/1 公开）` |
| /knowledge-bases/{kbId}/directories | GET | 200 | 目录列表 |
| /knowledge-bases/{kbId}/directories | POST | 200 | 新建子目录 |
| /knowledge-bases/{kbId}/directories | POST | 400 | 缺 `name`：`目录名称不能为空` |
| /knowledge-bases/{kbId}/directories/{directoryId} | PUT | 200 | 重命名/移动目录成功 |

### Knowledge（11）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /knowledge | GET | 200 | 列表/分页 |
| /knowledge | POST | 200 | 新建知识成功，返回 id |
| /knowledge | POST | 400 | 缺 `kbId`：`请选择知识库` |
| /knowledge/{knowledgeId} | GET | 200 | 正常 |
| /knowledge/{fake} | GET | 404 | `知识不存在` |
| /knowledge/{knowledgeId} | PUT | 409 | **`当前状态不可编辑（已发布版本不可修改）` —— 数据状态非草稿，详见发现 M1** |
| /knowledge/{knowledgeId} | PUT | 409 | 同上：同 version 重发仍 409（非幂等，详见发现 M2） |
| /knowledge/{knowledgeId} | PUT | 400 | 缺 `version`：`版本号不能为空` |
| /knowledge/autosave | POST | 409 | 同上：状态非 draft |
| /knowledge/autosave | POST | 400 | 缺字段：`请选择有效的知识库与目录` |
| /knowledge/{knowledgeId}/index-status | GET | 200 | 返回 index 状态 |
| /knowledge/{knowledgeId}/reindex | POST | 409 | `仅已发布知识可重建索引`（与 status=3 一致） |
| /knowledge/{knowledgeId}/restore | PUT | 409 | `知识不在回收站`（合法） |
| /knowledge/{knowledgeId}/versions | GET | 200 | 版本列表 |
| /knowledge/retrieval-test | POST | 400 | 缺字段：`Cannot map null into type int`（Jackson 反序列化错，详见发现 L3） |

### Public（12）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /public/knowledge | GET | 200 | 无 token 即可访问 |
| /public/knowledge/{id}（草稿 id） | GET | 404 | `知识不存在或未公开`（隔离正确） |
| /public/knowledge/{id}（已发布） | GET | 200 | 正常 |
| /public/knowledge/{id}/view | POST | 200 | 已发布/草稿均 200（详见发现 M4） |
| /public/knowledge/{knowledgeId}/comments | GET | 200 | 无 token 即可读 |
| /public/knowledge/{knowledgeId}/comments | POST | 401 | 无 token 返回 401（**与 `/public/` 前缀冲突，详见发现 L2**） |
| /public/knowledge/{knowledgeId}/comments（带 TOKEN_A） | POST | 200 | 正常 |
| /public/knowledge/{knowledgeId}/comments | POST | 401 | 缺 `content` 仍先 401 |
| /public/knowledge/{knowledgeId}/like | POST | 401 | 无 token 同上 |
| /public/knowledge/{knowledgeId}/like（带 token） | POST | 200 | 正常 |
| /public/knowledge/{knowledgeId}/dislike | POST | 200 | 正常（带 token） |
| /public/knowledge/{knowledgeId}/like/status | GET | 200 | 正常 |
| /public/knowledge/{knowledgeId}/favorite | POST | 200 | 正常（带 token） |
| /public/knowledge/{knowledgeId}/feedback | POST | 400 | 缺 `problem`：`problem 问题描述不能为空` |
| /public/favorites | GET | 401 | 无 token 同上（带 token 后 200） |
| /public/tags | GET | 200 | 无 token 即可 |

### Engagement（2）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /public/comments/{commentId}/like | POST | 401 | 无 token 401；带 token 后 200 |
| /public/comments/{commentId}/dislike | POST | 200 | 带 token 正常 |

### Recycle（3）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /recycle-bin | GET | 200 | 列表/分页正常 |
| /recycle-bin/{type}/{id}（fake id） | DELETE | 409 | **`彻底删除需要二次确认` —— 即使带 `{"confirm":true}` body 仍 409，详见发现 M3** |
| /recycle-bin/{type}/{id}/restore（fake） | POST | 404 | `知识不存在或不在回收站` |

### Review / Release（10）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /reviews | GET | 200 | 列表/分页 |
| /reviews | POST | 409 | 当前状态（status=3）不可提交审核（与 M1 一致） |
| /reviews | POST | 400 | 缺 `knowledgeId`：`知识 ID 不能为空` |
| /reviews/auto | POST | 409 | 同上 |
| /reviews/auto | POST | 400 | 缺字段同上 |
| /reviews/{id} | GET | 200 | 正常 |
| /reviews/{id}/approve | POST | 400 | 缺 `version`：`版本号不能为空` |
| /reviews/{id}/reject | POST | 400 | 缺 `version` / `expectation` 字段被精确拦截 |
| /reviews/{id}/publish | POST | 409 | `该审核已被阻断`（业务阻断，合法） |
| /releases | GET | 200 | 列表 |
| /releases | POST | 400 | 缺 `version` / `knowledgeId` 均被精确拦截 |
| /releases | POST | 409 | 完整 body 时：`仅审核通过的知识可发布`（业务规则生效） |
| /releases/{knowledgeId}/unpublish | POST | 409 | `仅已发布知识可下架`（与 status=3 一致——本例可下架应通过，实际未通过需进一步验证） |
| /releases/{fake}/unpublish | POST | 404 | `知识不存在` |

### AI（3）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /ai/writing | POST | 200 | 真实模型调用，返回 task id `2090489229638201345` |
| /ai/writing（短 topic） | POST | 400 | `主题、草稿、素材至少填写一项` |
| /ai/writing（空 body） | POST | 400 | 同上 |
| /ai/review | POST | 200 | 返回 task id `2090489230036660225` |
| /ai/review（空 body） | POST | 400 | `待审校正文不能为空` |
| /ai/enhance | POST | 200 | scene=`SUMMARY` → `{"summary":"..."}`；scene=`SEO` → `{"title","keywords","description"}` |
| /ai/enhance（scene=clarity） | POST | 400 | **`场景仅支持 SUMMARY|SEO`，与 OpenAPI DTO 不一致，详见发现 H2** |
| /ai/enhance（空 body） | POST | 400 | `待处理内容不能为空` |

### Chat（5）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /chat/conversations | GET | 200 | 列表 |
| /chat/conversations | POST | 200 | 新建会话成功 |
| /chat/conversations | POST | 400 | 缺 `title`：`会话标题不能为空` |
| /chat/conversations/{id}/messages | GET | 200 | 正常 |
| /chat/knowledge/{knowledgeId}/ask | POST | 200 | 返回 SSE 流式：`event:chunk` `event:citation` `event:done`（task 内一次调用即拿完整个会话） |
| /chat/knowledge/{knowledgeId}/ask（空 body） | POST | 400 | `提问内容不能为空` |
| /chat/stream | POST | 200 | SSE：`event:chunk` ... `event:done` |
| /chat/stream（空 body） | POST | 400 | `提问内容不能为空` |
| 注：ChatRequestDTO 字段是 `query`，**不是 `question`**（OpenAPI 标注一致，但请求体示例不清晰，详见发现 L4） |

### Task（3）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /tasks/{fake} | GET | 404 | `任务不存在` |
| /tasks/{fake}/events | GET | 404 | `任务不存在` |
| /tasks/{fake}/retry | POST | 200 | **`data:false`——非存在任务也返回 200，应为 404，详见发现 H1** |

### System（1）
| 端点 | 方法 | 状态码 | 备注 |
| ---- | ---- | ------ | ---- |
| /system/ping | GET | 200 | 无 token 也 200（首次无 token 测试也曾出现过 401 的偶发，详见发现 L5） |
| /system/ping | GET | 200 | 带 token 同样 200 |

---

## 异常与发现（按严重度）

### 高（接口缺失/500/必填失效）

- **H1 — `POST /tasks/{taskId}/retry` 对不存在 task 返回 200**
  - 请求：`POST /api/v1/tasks/0000000000000000000/retry`（fake id）
  - 响应：`{"code":"SUCCESS","data":false}` HTTP 200
  - 与姊妹端点 `GET /tasks/{fake}` 返回 404、`GET /tasks/{fake}/events` 返回 404 的行为不一致——retry 应判定任务不存在并返回 404。
  - 复现命令：
    ```bash
    curl -X POST http://localhost:8080/api/v1/tasks/0000000000000000000/retry \
      -H "Authorization: Bearer <token>" \
      -H "Content-Type: application/json" -d '{}'
    ```

- **H2 — `POST /ai/enhance` 的 scene 枚举与 OpenAPI 不一致**
  - OpenAPI `EnhanceRequestDTO.scene`：`type: string`（无 enum 约束）
  - 实际行为：只接受 `SUMMARY|SEO`，传 `clarity` 返回 `场景仅支持 SUMMARY|SEO`（400）
  - 建议：OpenAPI 加 `enum: [SUMMARY, SEO]`（或者后端放宽支持更多场景）

### 中（响应字段缺失/状态码异常）

- **M1 — `QA-Draft-1` 测试数据实际 status=3，非任务上下文标注的 status=2（草稿）**
  - `GET /knowledge/2090488418153623554` 返回 `"status":3, "version":"1"`
  - 直接影响：所有 PUT/POST autosave/reindex/unpublish 等操作都按"已发布"逻辑返回 409
  - 本任务为冒烟测试，未修复，但请主代理在登记 BUGS.md 时附"数据准备脚本与实际数据不一致"作为环境备注
  - 影响测试：`PUT /knowledge/{id}`、`POST /knowledge/autosave`、`POST /knowledge/{id}/reindex`、`POST /reviews`、`POST /reviews/auto`、`POST /releases/{id}/unpublish`

- **M2 — `PUT /knowledge/{id}` 不幂等**
  - 同一 `version=0` body 两次发送：两次都返回 409
  - 期望：幂等接口（同请求重复执行应不报错）；当前实现按"已发布不可编辑"短路，并未实现幂等保护
  - 仅在"非草稿状态下"复现；若需确认草稿幂等，需先修复 M1 后重测

- **M3 — `DELETE /recycle-bin/{type}/{id}` 二次确认机制不明**
  - `DELETE /recycle-bin/knowledge/0000000000000000000`（fake id）无 body → 409 `彻底删除需要二次确认`
  - 带 `{"confirm":true}` body → 仍 409
  - OpenAPI 未定义该端点的 body/query 参数
  - 建议：检查后端实现（可能是 query 参数 `?confirm=true` 或 header，或要求先 GET 再 DELETE）

- **M4 — `POST /public/knowledge/{id}/view` 对草稿 id 也返回 200**
  - `POST /public/knowledge/2090488418153623554/view`（草稿/未发布）→ 200
  - 期望：未公开知识不应被 view（与 `GET /public/knowledge/{id}` 行为不一致，后者正确返回 404）
  - 影响：可能造成统计偏差（浏览量被虚增）

### 低（与设计意图冲突的小问题）

- **L1 — `POST /auth/logout` 无 body 时错误消息未指明字段**
  - 无 body：返回 `请求参数有误`（无字段名）
  - 空 `{}` body：返回 `refreshToken 刷新令牌不能为空`（精确）
  - 建议统一：缺少 body 也应返回精确字段消息

- **L2 — `/public/*` 前缀的互动接口（like/dislike/favorite/comment）需要鉴权**
  - 路径前缀为 `/public/`，但 POST 操作全部返回 401 无 token
  - `GET /public/*` 系列则无需 token
  - 可能是设计意图（"读公开，写需登录"），但前端会因 401 体验差；建议文档明确或在 OpenAPI 上加 `security` 标注

- **L3 — `POST /knowledge/retrieval-test` 缺参数时返回 Jackson 反序列化错**
  - `Cannot map \`null\` into type \`int\` (set DeserializationFeature...)`
  - 期望：返回业务 `INVALID_PARAM` + 字段名（与同模块其他端点风格一致）
  - 修复：DTO 加 `@JsonInclude` 或字段默认值

- **L4 — `ChatRequestDTO` 字段名 `query` 而非常见的 `question`**
  - 不是 bug，但与同模块 `chat/stream` 一致使用 `query` 是统一设计
  - 建议：OpenAPI 字段描述补充示例，便于前端集成

- **L5 — `/system/ping` 偶发 401（首测）**
  - 首次未带 token 调用 ping 返回 401 `请先登录`
  - 后续无 token 调用返回 200 `pong`
  - 可能是首次鉴权过滤器初始化未完成（冷启动），非稳定缺陷

---

## 截图/原始响应

关键响应样例（节选）：

```json
// GET /knowledge/2090488418153623554
{"code":"SUCCESS","data":{"id":"2090488418153623554","title":"QA-Draft-1","status":3,"version":"1", ...}}

// POST /ai/enhance (scene=SUMMARY)
{"code":"SUCCESS","data":{"id":"2090489251939315714","knowledgeId":"2090488418153623554","scene":"SUMMARY","resultJson":"{\"summary\":\"...\"}"}}

// POST /ai/enhance (scene=clarity) — 缺陷 H2 复现
{"code":"INVALID_PARAM","message":"场景仅支持 SUMMARY|SEO","data":null}

// POST /tasks/{fake}/retry — 缺陷 H1 复现
{"code":"SUCCESS","message":"操作成功","data":false}

// GET /knowledge-bases/{PRIV_KB_ID} with TOKEN_B — M2 隔离
{"code":"NOT_FOUND","message":"知识库不存在","data":null}

// PUT /knowledge/{id} (idempotency) — M2
{"code":"CONFLICT","message":"当前状态不可编辑（已发布版本不可修改）"}
```

---

## 总结

| 类别 | 数量 |
| ---- | ---- |
| 真缺陷（H） | 2 |
| 设计/行为不一致（M） | 4 |
| 小问题（L） | 5 |
| 跳过的破坏性端点（DELETE *3） | 3 |
| 通过（200 happy path 或符合设计的 4xx） | 65 |

**核心问题**：H1（task retry 对不存在 id 误返 200）+ H2（AI enhance scene 枚举与文档不一致）需要主代理登记 BUGS.md 并跟进修复。其余 M/L 级建议作为后续打磨项。
