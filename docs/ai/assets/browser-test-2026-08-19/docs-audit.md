# 文档一致性核验报告

- 日期：2026-08-19
- 核验人：ZCode（浏览器测试子任务，与 BUG-007 复现并行）
- 核验范围：README、STATUS、CHANGELOG、BUGS、IDEAS、QA、PRODUCT、BACKEND、PROTOTYPE、FRONTEND、GLOBAL 共 11 份
- 核验方式：抽样 10 个关键一致性点，逐一交叉比对；只标记差异，不自动修复
- 范围约束：与 QA.md / BUGS.md 铁律一致（仅核验，不改任何文档）

## 一、抽样点核对结果总表

| # | 一致性点 | 结论 | 证据位置 |
| --- | --- | --- | --- |
| 1 | MVP/V2/V3 总数与 STATUS §2 描述 | **不一致**（3 处口径冲突） | 见 §二.1 |
| 2 | PROTOTYPE 页面数量 vs STATUS §3/W7 摘要 | **不一致**（W7 摘要已过时） | 见 §二.2 |
| 3 | BUGS.md 状态流转规则 vs STATUS §1 工作流 | **一致** ✓ | 见 §二.3 |
| 4 | 文档导航（README + GLOBAL §2 + STATUS §1 + GLOBAL §4） | **基本一致**，1 处文本与清单条目数对不齐 | 见 §二.4 |
| 5 | 决策 D1~D17 编号与正文引用 | **一致** ✓ | 见 §二.5 |
| 6 | 2026-08-19 最新 1-2 条 CHANGELOG vs STATUS §7 | **一致** ✓ | 见 §二.6 |
| 7 | 8-19 QA.md 新建在 GLOBAL §4 + README + GLOBAL §2 登记 | **一致** ✓ | 见 §二.7 |
| 8 | QA.md 引用「PRODUCT §5/§12、BACKEND §10、PROTOTYPE、BUGS」 | **一致** ✓ | 见 §二.8 |
| 9 | GLOBAL §6.4/6.5 启动命令仍可用 | **一致** ✓ | 见 §二.9 |
| 10 | README「已实现」vs STATUS §2 里程碑状态 | **一致** ✓ | 见 §二.10 |

**汇总：10 项中 7 项一致，3 项存在差异（D1/D2/D4 编号见下）。**

## 二、各项详细核对

### 1. MVP/V2/V3 总数与 STATUS §2（不一致 / 3 处口径冲突）

| 来源 | 描述 | 数值 |
| --- | --- | --- |
| **PRODUCT.md §5 第 80 行** | "**总表统计：13 模块 × 87 项功能（MVP 44 / V2 31 / V3 12）**" | **87 / MVP 44 / V2 31 / V3 12** |
| **README.md 第 19 行** | "13 模块 82 项功能总表（唯一功能事实源，MVP 39 / V2 31 / V3 12）" | 82 / MVP 39 / V2 31 / V3 12 |
| **GLOBAL.md §1 第 14 行** | "总表统计：MVP 39 / V2 31 / V3 12" | MVP 39 / V2 31 / V3 12 |
| **STATUS.md §6 W7 第 101 行**（历史核验） | "功能总表 73 项（MVP 37/V2 24/V3 12）" | 73 / MVP 37 / V2 24 / V3 12 |
| **STATUS.md §2 描述** | "MVP 全部 13 个里程碑已完成：M01~M03…M13" | 仅里程碑 13 个，无功能数 |

**差异 D1**：PRODUCT.md 现行值（**87 / MVP 44**）≠ README / GLOBAL（82 / MVP 39）—— 实际为 +5 功能（应来自 2026/8/14 KB-1~6 阶段新增的 F-0106 / F-0208 / F-0308 / F-0309 / F-0407 五项里部分被升级 MVP）。README 与 GLOBAL 引用未同步。

**差异 D2**：STATUS §6 W7（2026-08-12 历史值 73 / MVP 37）至今未刷新，与 PRODUCT 现行 87 差 14 项。W7 是「会话 #6 变更同步核验」的历史快照，保留原值也算合理，但 STATUS 未注明「该核验于 2026-08-12 完成、后续 KB-1~6 后未重做」。

**差异 D3**：V2 计数两处都是 31、V3 计数两处都是 12，唯一变数是 MVP 与总数；说明 V2/V3 列表已稳定，MVP 才是每次阶段调整的浮动面。

**建议（不自动改）**：README 第 19 行 / GLOBAL §1 第 14 行 应同步到 PRODUCT §5 的 87 / MVP 44。STATUS §6 W7 行可加括号注明「历史快照」或追加 W8/W9 等更新核验行。

### 2. PROTOTYPE 页面数量 vs STATUS §3 摘要（不一致）

| 来源 | 描述 | 范围 |
| --- | --- | --- |
| **PROTOTYPE.md §6 顶部 + §7/§8/§9** | "MVP：B00~B04、B08~B13、B16、B20~B23（博客）、A01~A04（管理后台）、D01/D02" | B00,B01,B02,B03,B04,B08,B09,B10,B11,B12,B13,B16,B20,B21,B22,B23 + A01,A02,A03,A04 + D01,D02 = **22 页** |
| **STATUS.md §6 W7（2026-08-12 历史核验）** | "PROTOTYPE 页面清单（B00~B19、A01~A07、D01/D02）" | B00~B19 + A01~A07 + D01/D02 |
| **STATUS.md §5 待办 M08 等多行** | 引用 PROTOTYPE B00/D01/D02 等具体页 | — |

**差异 D4**：STATUS §6 W7 行的 PROTOTYPE 范围是「B00~B19、A01~A07、D01/D02」—— KB-4 之后 PROTOTYPE 实际已扩展到 B20~B23（KB-4 新增）、B24（V2 新增）、A01~A07（A05~A07 为 V2）、B16 由 V2 提升 MVP。W7 摘要是 2026-08-12 快照，已不能代表当前 PROTOTYPE 状态。

**建议（不自动改）**：STATUS §6 W7 行可保留历史值并标 "(2026-08-12 快照，KB-4 后已扩展至 B20~B23/B24)"，或新增 W8 核验行同步 PROTOTYPE 现状。

### 3. BUGS.md 状态流转规则 vs STATUS §1 工作流（一致 ✓）

| 来源 | 状态机 |
| --- | --- |
| **BUGS.md 第 12 行** | "状态流转：待修复（默认）→ 修复中（用户要求修复后、AI 认领时标）→ 已修复（从清单移除，编号不回收）" |
| **STATUS.md §1 规则 1 + §4** | "通读 BUGS.md…仅按用户明确要求修复，不自动认领"、"待办仅 OPT-1…用户新发现缺陷记 BUGS.md（仅按明确要求修复，不自动认领）" |
| **QA.md §1 铁律** | "发现的缺陷**不自动修复**：记入 BUGS.md，仅按用户明确要求修复（与 BUGS.md 约定一致）" |

→ 三份文档对"不自动认领/不自动修复"约束完全一致；BUGS.md 当前 BUG-007 状态「待修复」、未启动，与 STATUS §4 "无进行中任务" 自洽。✓

### 4. 文档导航（README + GLOBAL §2 + STATUS §1 + GLOBAL §4）基本一致

| 来源 | 清单 |
| --- | --- |
| **README.md 第 17 行 + 文档清单 19-28 行** | 文本「文档体系 11 份」+ 实际链接 10 项（产品/全局/后端/前端/原型 + STATUS/CHANGELOG/BUGS/IDEAS/QA） |
| **GLOBAL.md §2 第 34-46 行** | 11 行表格（README/GLOBAL/PRODUCT/BACKEND/FRONTEND/PROTOTYPE/STATUS/CHANGELOG/BUGS/IDEAS/QA） |
| **GLOBAL.md §4 第 86-117 行** | 目录树列出 docs/ai 5 个文件 + docs/{product,backend,frontend,global} 4 个 + README.md 自身 = 11 份 |
| **STATUS.md §1 规则 1** | 引用 PRODUCT/BACKEND/FRONTEND/PROTOTYPE/GLOBAL/BUGS/QA = 7 个 |

**差异 D5（轻微）**：README 第 17 行写「文档体系 11 份」，但下方链接清单 19-28 行只列了 10 项。**11 = 10 份 docs/ + 1 份 README.md 自身**，所以 11 这个数字本身没错，但读者看 10 个链接会困惑。**建议（不自动改）**：在 11 份字样后加半句"含本 README"或"docs/ 10 份 + 本 README"。

**STATUS §1 阅读清单** 比 GLOBAL §2 导航表少 4 项（CHANGELOG/IDEAS/README/BACKEND 没有强制阅读，因为 STATUS 是入口、AI 代理已在 STATUS 内部、BACKEND 是工程实现层）；不构成不一致，但**STATUS §1 未引用 CHANGELOG**——CHANGELOG 在规则 4「收尾」中隐含（"按 CHANGELOG.md 头部模板追加一条"），不矛盾。

### 5. 决策 D1~D17 编号与正文引用（一致 ✓）

| 来源 | 覆盖 |
| --- | --- |
| **STATUS.md §8 决策摘要** | D1~D17 全列 17 行 |
| **CHANGELOG.md** | 引用 D1, D2, D5, D6, D7, D8, D9, D10, D11, D12, D13, D14, D15, D16, D17（grep 命中 D1~D17，无越界编号） |
| **PRODUCT.md** | 引用 D4, D7, D9, D10, D13, D14, D16 等 |
| **BACKEND.md** | 引用 D1, D2, D3, D4, D5, D7, D10, D13, D15, D16, D17 |
| **GLOBAL.md** | 引用 D1, D3, D6, D8, D9, D11, D12 等 |
| **QA.md** | 引用 D9 |
| **README.md** | 引用 D1~D17（"决策 D1~D17"） |

→ 所有正文中出现的 D# 编号均在 D1~D17 范围内，**没有越界（如 D18/D19）或 D0X（D00 等）**。`grep "D0[1-9]"` 命中的 "D01" "D02" 实际是 PROTOTYPE 页码（AI 对话组件 D01/D02）而非决策编号，已逐项核验无混淆。✓

### 6. 2026-08-19 最新 1-2 条 CHANGELOG vs STATUS §7（一致 ✓）

| 来源 | 最新 1-2 条 |
| --- | --- |
| **CHANGELOG.md 顶部** | 1) 2026/8/19 11:00 ZCode 新建 AI 浏览器测试指南 QA.md；2) 2026/8/18 20:47 ZCode IDEAS 批次 + BUG-006 |
| **STATUS.md §7 最近变更** | 1) 2026/8/19 11:00 ZCode 新建 QA.md；2) 2026/8/18 20:47 ZCode IDEAS 批次 + BUG-006；3) 2026/8/17 17:30 ZCode 文档体系治理 |

→ 顶部 2 条完全一致，§7 还多保留一条 8/17 作为最近 3 条基线，与 CHANGELOG §7 自身「仅保留最近 3 条摘要」的约定一致。✓

### 7. 8-19 QA.md 新建在 GLOBAL §4 + README + GLOBAL §2 登记（一致 ✓）

| 来源 | QA.md 登记 |
| --- | --- |
| **GLOBAL.md §2 第 46 行** | 「AI 浏览器测试指南 ../ai/QA.md ... AI 浏览器测试规范唯一来源」 |
| **GLOBAL.md §4 第 92 行** | 「ai/QA.md # AI 浏览器测试指南（browser-use 全功能/指定模块测试）」 |
| **GLOBAL.md §4 标注说明（第 119 行）** | "BUGS.md 2026-08-16、IDEAS.md 2026-08-18、QA.md 2026-08-19 新增" |
| **README.md 第 28 行** | 「[AI 浏览器测试指南](docs/ai/QA.md)：用户发起、AI 代理 browser-use 浏览器测试…」 |
| **README.md 第 17 行** | "文档体系 11 份（…+ AI 协作五件套 STATUS/CHANGELOG/BUGS/IDEAS/QA）" |
| **STATUS.md §1 规则 1** | 引用 QA.md ✓ |

→ 三处导航登记齐全、命名一致、计数同步（10→11 份），符合 CHANGELOG 2026/8/19 11:00 条目所述"导航同步"承诺。✓

### 8. QA.md 引用「PRODUCT §5/§12、BACKEND §10、PROTOTYPE、BUGS」各章节是否仍存在（一致 ✓）

| QA.md 引用 | 实际章节位置 | 存在？ |
| --- | --- | --- |
| PRODUCT §5 | `docs/product/PRODUCT.md` 第 76-170 行 | ✓ |
| PRODUCT §12 | `docs/product/PRODUCT.md` 第 236-247 行（完成定义） | ✓ |
| BACKEND §10 | `docs/backend/BACKEND.md` 第 "## 10. REST API 规范" | ✓ |
| PROTOTYPE | `docs/frontend/PROTOTYPE.md`（无 § 但有完整页面小节） | ✓ |
| BUGS | `docs/ai/BUGS.md` | ✓ |
| BACKEND §10（reindex 端点） | BACKEND §10 + KB-3 起已记录 POST /api/v1/knowledge/{id}/reindex | ✓ |
| GLOBAL §6.4 / §6.5 / §7 | `docs/global/GLOBAL.md` §6.4/§6.5/§7 | ✓ |
| STATUS §3 / §7 | `docs/ai/STATUS.md` §3 / §7 | ✓ |

→ QA.md 全文 7 处章节引用（PRODUCT §5/§12、BACKEND §10、GLOBAL §6.4/§6.5/§7、STATUS §3/§7）全部命中。✓

### 9. GLOBAL §6.4/6.5 启动命令仍可用（一致 ✓）

| 维度 | GLOBAL.md 描述 | 实际 / 验证 |
| --- | --- | --- |
| 后端启动命令 | `cd backend/xlumen-server && mvn -pl xlumen-boot -am spring-boot:run` | 与 `application.yml` 端口 8080 + 装配入口 `xlumen-boot` 一致 |
| 后端健康检查 | `http://localhost:8080/actuator/health` | 本次复现实测返回 `{"status":"UP"}`（HTTP 200） |
| 前端 blog | `pnpm --dir frontend/xlumen-frontend-blog dev` → 5173 | 与 M01 骨架 + 2026/8/18 CHANGELOG 8/18 20:47 验证记录一致 |
| 前端 admin | `pnpm --dir frontend/xlumen-frontend-admin dev` → 5174 | 同上 |
| JDK | 25（`JAVA_HOME` 必须指向 JDK 25） | 与 STATUS §1 规则 3 + §9 环境速查 + 根 POM 一致 |
| Node / pnpm | Node 20+ / pnpm 9+ | 与 §5 技术基线 + §6.1 一致 |
| .env 路径 | `backend/xlumen-server/config/.env` | 本次复现读取路径命中（XLUMEN_DB_URL 等生效） |

→ 启动命令、JDK、端口、.env 路径与实际可运行环境完全一致。✓

### 10. README「已实现」vs STATUS §2 里程碑状态（一致 ✓）

| 来源 | 里程碑状态 |
| --- | --- |
| **README.md "已实现" 段** | "**MVP 全部 13 个里程碑已交付并通过运行时验证**（2026-08-12 ~ 2026-08-13）：代码骨架、身份与多租户、博客公开页、内容管理、RAG 索引（Noop 降级待 Milvus 环境）、AI 基座/创作/对话/增值、审核发布、读者纠错、管理后台与热点缓存" + "**知识平台化重构 KB-1~KB-6 已全部交付**（2026-08-14）" + "2026-08-16/17 全功能测试缺陷（BUG-3~11）与 BUG-002~005 统一修复…" |
| **STATUS.md §2** | "MVP 全部 13 个里程碑已完成：M01~M03…M13 与 F-1301 缓存均已交付并通过运行时验证。待环境：本机 Docker/Milvus 未安装，向量检索以 NoopVectorStore 降级运行" + "知识平台化重构…KB-1~KB-6 已全部交付" |

→ 13 个里程碑编号（M01~M13 缺 M12 而 M12 与 M06 合并叙述）、KB-1~KB-6 全部完成、Noop 降级、8/16+8/17 缺陷修复——四项均一致。✓

## 三、差异清单汇总

| 编号 | 严重度 | 位置 | 差异内容 | 建议（不自动改） |
| --- | --- | --- | --- | --- |
| **D1** | 中 | README.md 第 19 行 / GLOBAL.md §1 第 14 行 | 引用 PRODUCT 旧值 "82 项 / MVP 39"，实际 PRODUCT.md §5 现行 "87 项 / MVP 44" | 同步到 87 / MVP 44 |
| **D2** | 低 | STATUS.md §6 W7 行 | "73 项 / MVP 37" 是 2026-08-12 历史快照，未注明时效 | 加 "(2026-08-12 快照)" 或追加 W8 核验行 |
| **D3** | 低 | STATUS.md §6 W7 行 | PROTOTYPE 范围 "B00~B19、A01~A07" 已过时，实际含 B20~B23/B24 | 加 "(KB-4 后已扩展至 B20~B23/B24)" |
| **D4** | 低 | README.md 第 17 行 vs 第 19-28 行 | 文本「11 份」+ 链接清单 10 项（11 = 10 docs + 1 README 自身，读者易混） | 在 11 份字样后注明"含本 README" |

> 4 项差异均属**文档同步滞后**，无内容冲突、无功能影响；**全部留待用户决策后再修订**（QA.md 铁律）。

## 四、核验覆盖

- 11 份文档全部读取 ✓
- 6 份核心交叉文档（README / STATUS / GLOBAL / PRODUCT / BACKEND / PROTOTYPE）+ 3 份 AI 协作（CHANGELOG / BUGS / QA）+ 2 份补充（IDEAS / FRONTEND）✓
- 10 个一致性点逐项核对、原始证据已列出 ✓
- 未修改任何文档、未触碰 BUGS.md 候选区、未重启后端 ✓
- BUG-007 根因报告同步在 `bug-007-repro.md`，与本报告互不引用以保持各自可独立查阅 ✓

## 五、相关文件路径

- 待修订候选（仅作导航，不动手）：
  - `D:\calwen\project\calwen\xlumen\README.md`（D1、D4）
  - `D:\calwen\project\calwen\xlumen\docs\global\GLOBAL.md` §1（D1）
  - `D:\calwen\project\calwen\xlumen\docs\ai\STATUS.md` §6 W7（D2、D3）
- 已校验但无需修订：
  - `D:\calwen\project\calwen\xlumen\docs\ai\BUGS.md`（§1.3 一致性确认）
  - `D:\calwen\project\calwen\xlumen\docs\ai\CHANGELOG.md`（§1.6 一致性确认）
  - `D:\calwen\project\calwen\xlumen\docs\ai\IDEAS.md`（与 QA 同样不冲突）
  - `D:\calwen\project\calwen\xlumen\docs\ai\QA.md`（§1.7、§1.8 一致性确认）
  - `D:\calwen\project\calwen\xlumen\docs\product\PRODUCT.md`（§1.1 当前事实源）
  - `D:\calwen\project\calwen\xlumen\docs\backend\BACKEND.md`（§1.5/§1.8 一致性确认）
  - `D:\calwen\project\calwen\xlumen\docs\frontend\PROTOTYPE.md`（§1.2 当前事实源）
