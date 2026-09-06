# xLumen 上线部署文档

> 适用范围：生产环境部署（本文档为权威来源，构建命令与 [GLOBAL.md](../global/GLOBAL.md) §6 同源一致）。
> 交付形态：**后端单一 fat jar + 前端静态打包产物**，域名经 Nginx 反向代理。
> 依赖边界（已源码核验）：MySQL、Redis 为**必装**；Milvus 可选（不可达自动降级 Noop，语义检索失效但关键词检索可用）；AI 供应商（百炼/DeepSeek）与 SMTP 按需配置。无 RocketMQ / MinIO / WebSocket 运行时依赖，通知与对话流式全部走 HTTP SSE。

## 1. 目标架构

```text
                         ┌─────────────────────── 服务器 ───────────────────────┐
 用户浏览器 ──HTTPS──▶ Nginx (80/443)                                          │
                          │                                                      │
                          ├─ /        → xlumen-frontend-blog/dist   （博客前台） │
                          ├─ /api/    → 反代 127.0.0.1:8080        （后端 API + SSE）│
                          ├─ :8082            → admin/dist          （管理后台） │
                          └─ /api/    → 反代 127.0.0.1:8080                      │
                                                      │                          │
                                    java -jar xlumen-boot-*.jar (:8080)          │
                                                      │                          │
                        ┌─────────────┬─────────────┼────────────┐               │
                     MySQL 8.x      Redis        Milvus(可选)  云 AI / SMTP      │
                     （业务事实）  （短期状态）   （向量，降级）  （外部 HTTPS）      │
                        └─────────────┴─────────────┴────────────┘               │
                        └────────────────────────────────────────────────────────┘
```

要点：

- 前端 API 基址为同源相对路径 `/api/v1`（[http.ts](../../frontend/xlumen-frontend-blog/src/api/http.ts)），**无需跨域配置**，Nginx 把 `/api/` 反代给后端即可。
- 两个前端都是 Vue Router history 模式，Nginx 必须配置 SPA fallback（`try_files ... /index.html`）。
- 对话流式、写作进度、通知推送均为 SSE 长连接（fetch + ReadableStream 消费），Nginx 对该代理**必须关闭缓冲**，否则流式输出被攒批、页面表现为“一直不吐字”。
- 当前无域名：博客 `http://159.75.6.183`（80 端口）、后台 `http://159.75.6.183:8082`，双端互跳地址在**前端构建时**通过 `VITE_ADMIN_URL` / `VITE_BLOG_URL` 写入（部署脚本顶部配置区）。

## 2. 前置条件

| 项 | 要求 | 说明 |
| --- | --- | --- |
| 服务器 | Linux x86_64（本文以 Ubuntu/Debian 为例）| 也可 Windows，命令见附录 A |
| JDK | **25**（`JAVA_HOME` 必须指向 JDK 25）| 后端编译与运行，低于 25 无法启动 |
| Maven | 3.9+（仅构建机需要，运行机不需要）| |
| Node.js / pnpm | Node 20+ / pnpm 9+（仅构建机需要）| |
| MySQL | 8.x，UTF-8（utf8mb4）| 业务事实库，必装 |
| Redis | 6+/7+ | 会话/验证码/限流，必装；可 127.0.0.1 无密码 |
| Milvus | 可选 | REST v2（默认 19530），不可达自动降级，不影响启动 |
| 域名 | 当前无域名：博客 80 端口、后台 8082 端口，IP 直连 `159.75.6.183` | 有域名后改为 server_name 分流 + HTTPS |
| HTTPS 证书 | 无域名暂不配置（certbot 需要域名），先 HTTP 直连 | 有域名后用 certbot 免费证书 |
| 防火墙 | 公网放行 80、8082（前台/后台）；8080/8081 只允许本机/内网 | 后端端口不对公网暴露 |

## 3. 构建产物（在构建机执行）

> **两条部署路径，任选其一**：
> - **方式 A（本手册，手动）**：按本节构建，产物上传服务器，再按第 6/7 节手动部署。
> - **方式 B（一键脚本）**：直接在服务器上执行 [`scripts/deploy-backend.sh`](../../scripts/deploy-backend.sh)、[`scripts/deploy-blog.sh`](../../scripts/deploy-blog.sh)、[`scripts/deploy-admin.sh`](../../scripts/deploy-admin.sh)，后端起停服务、前端直接换静态产物（第 11 节，服务器需装有构建工具链）。
>
> 两种方式最终都以稳定名 **`xlumen-boot.jar`** 落入服务器 `$APP_DIR/`。

### 3.1 后端 fat jar

```bash
# 构建机：export JAVA_HOME=<JDK25 路径>（Windows PowerShell: $env:JAVA_HOME=...）
cd backend/xlumen-server
mvn -T 1C -pl xlumen-boot -am package -DskipTests   # -T 1C 每核一线程并行编译，8 模块明显提速
# 产物：backend/xlumen-server/xlumen-boot/target/xlumen-boot-0.1.0-SNAPSHOT.jar（约 100+MB，内置全部依赖）
# 上传服务器后存为稳定名：cp xlumen-boot/target/xlumen-boot-*.jar /wen/app/backend/xlumen/prod/xlumen-boot.jar
```

### 3.2 前端两个应用

构建前先写好构建期环境变量（决定“后台入口”“返回前台”按钮指向的正式地址；默认值指向 localhost，**生产必须覆盖**）：

```bash
# frontend/xlumen-frontend-blog/.env.production
VITE_ADMIN_URL=http://159.75.6.183:8082
```

```bash
# frontend/xlumen-frontend-admin/.env.production
VITE_BLOG_URL=http://159.75.6.183
```

构建（仓库根目录，首次先 `pnpm install`）：

```bash
pnpm --dir frontend/xlumen-frontend-blog build     # 产物：frontend/xlumen-frontend-blog/dist
pnpm --dir frontend/xlumen-frontend-admin build    # 产物：frontend/xlumen-frontend-admin/dist
```

> 构建命令已内置 `vue-tsc` 类型检查；构建失败即类型问题，先修再上岗。

### 3.3 服务器目录规划（示例）

```text
/wen/                                  # 服务器根目录（脚本配置区里的默认值）
├─ project/
│  ├─ backend/xlumen/test、backend/xlumen/master        # 后端双环境源码（脚本自动 clone/pull）
│  └─ frontend/xlumen-frontend-blog、xlumen-frontend-admin   # 前端源码
├─ app/
│  ├─ backend/xlumen/test、backend/xlumen/prod         # 后端产物：xlumen-boot.jar（环境配置内置 jar）
│  └─ frontend/xlumen-frontend-blog、xlumen-frontend-admin  # 前端产物 = nginx 站点根目录
└─ log/
   └─ backend/xlumen/test、backend/xlumen/prod         # 后端运行日志 app.log
```

## 4. 后端配置（环境 profile，随 jar 内置）

数据库/Redis/密钥等环境差异全部配置在 `backend/xlumen-server/xlumen-boot/src/main/resources/application-<env>.yml`（dev/test/prod 三份 + 模板 application-demo.yml，随仓库提交并打进 fat jar）：

- 启动时用 `--spring.profiles.active=<env>` 选择环境（部署脚本已自动带上）。
- 改配置 = 改对应 profile 文件 → 重新打包部署（配置在 jar 里，不发新包不生效）。
- 键名映射：代码里的 `${XLUMEN_XXX}` 占位符在 YAML 里写作小写点号键（`XLUMEN_BAILIAN_API_KEY` → `xlumen.bailian.api-key`、`XLUMEN_SERVER_PORT` → `server.port`）。

| profile 文件 | 用途 | 关键差异 |
| --- | --- | --- |
| `application-demo.yml` | 模板（占位符） | 导出字段参考，勿直接用于启动 |
| `application-dev.yml` | 开发机 | 本地库/Redis，验证码可落日志 |
| `application-test.yml` | 测试环境 | 库 `xlumen_test`、端口 8081、Redis 逻辑库 1 隔离 |
| `application-prod.yml` | 正式环境 | 库 `xlumen_dev`、端口 8080、正式 SMTP；**上线前把 JWT/AI 密钥替换为全新值** |

> 注意：四份 profile 随仓库提交（依赖仓库私密性），jar 内含有全部环境配置——若未来仓库公开，先轮换所有密钥。

## 5. 初始化数据库

服务器 MySQL 建好后，按编号顺序执行仓库 `backend/xlumen-server/sql/init/` 全部脚本（`00_database.sql` 负责建库与字符集，其余为各模块 DDL）：

```bash
for f in sql/init/*.sql; do
  mysql -h<DB_HOST> -u<DB_USER> -p<DB_PASSWORD> < "$f"
done
# 或用单条：mysql ... < sql/init/00_database.sql && mysql ... xlumen_dev < 其余脚本
```

> Windows 构建机可用 `scripts/init-db.ps1 -Profile "./backend/xlumen-server/xlumen-boot/src/main/resources/application-dev.yml"`（决策 D29：解析 profile YAML 的 `spring.datasource` 段取连接参数）。幂等可重复执行；老库升级只需执行新增/变更脚本。

## 6. 启动后端（systemd 示例 · 可选替代方式）

> 部署脚本 `deploy-backend.sh` 默认用 nohup 后台方式启动（见第 11 节），不依赖 systemd；本节的 systemd 为可选替代（进程守护/开机自启更强），若采用需先手工建好服务文件。

```ini
# /etc/systemd/system/xlumen.service
[Unit]
Description=xLumen AI Knowledge Platform
After=network.target mysqld.service redis.service

[Service]
Type=simple
User=xlumen
WorkingDirectory=/wen/app/backend/xlumen/prod
# JVM 参数说明见下方「参数调整建议」
ExecStart=/usr/local/jdk-25/bin/java \
  -Xms512m -Xmx1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:MaxMetaspaceSize=512m \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/wen/log/backend/xlumen/prod/ \
  -Xlog:gc*:file=/wen/log/backend/xlumen/prod/gc.log:time,uptime:filecount=5,filesize=10m \
  -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=/wen/app/backend/xlumen/prod/xlumen.jsa \
  -Duser.timezone=Asia/Shanghai \
  -Dfile.encoding=UTF-8 \
  -jar /wen/app/backend/xlumen/prod/xlumen-boot.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

说明：

- `WorkingDirectory=/wen/app/backend/xlumen/prod` 环境配置已内置在 jar（profile 随包打进去），**不需要**任何外部配置文件，也**不需要** systemd `EnvironmentFile`；`WorkingDirectory` 只决定 logback 日志相对落盘位置。
- 日志文件：`logback-spring.xml` 固定相对路径 `logs/xlumen.log`（按日滚动 `logs/xlumen.%d{yyyy-MM-dd}.log`），落盘位置 = 工作目录下的 `logs/`（即 `/wen/app/backend/xlumen/prod/logs/`）。
- 常用运维命令：`systemctl daemon-reload && systemctl enable --now xlumen`；查看 `journalctl -u xlumen -f` 或日志文件。

### JVM 参数调整建议（本应用负载）

| 参数 | 取值 | 理由 |
| --- | --- | --- |
| `-Xms512m -Xmx1g` | 堆 512m 起、上限 1g | 个人平台 + Spring AI：请求多为短 REST + 长 SSE 挂起（不占堆），常驻 RSS 约 400~700MB，1g 堆窗口充足。并发写作/审查任务多可升 `-Xmx2g`，之后再大无收益 |
| `-XX:+UseG1GC` + `-XX:MaxGCPauseMillis=200` | G1 默认回收器 | JDK 25 默认即 G1，显式写出便于识别；200ms 暂停目标对 SSE 逐字输出足够平滑，无必要上 ZGC |
| `-XX:+UseStringDeduplication` | 开启字符串去重 | REST/SSE 的 JSON 响应产生大量重复字符串（字段名/枚举/常量文案），实测省堆明显，零风险 |
| `-XX:MaxMetaspaceSize=512m` | 元空间上限 | Spring Boot + MyBatis + 反射默认元空间无上限（吃满物理内存），512m 对本项目绰绰有余，防内存失控 |
| `-XX:+ExitOnOutOfMemoryError` | OOM 即退出 | 配合 `Restart=on-failure` 自动拉起；比挂着半死进程好排查 |
| `-XX:+HeapDumpOnOutOfMemoryError` + `-XX:HeapDumpPath` | OOM 自动留堆快照 | 事后用 MAT/JProfiler 定位，生产排查刚需 |
| `-Xlog:gc*:...` | GC 滚动日志 | 低开销可观测性；磁盘不足时可去掉 |
| `-Duser.timezone=Asia/Shanghai` | 显式时区 | 服务器若为 UTC 导致日志/定时差 8 小时 |
| `-Dfile.encoding=UTF-8` | 显式文件编码 | JDK 18+ 默认 UTF-8，显式写出防环境差异 |
| `-XX:+AutoCreateSharedArchive` + `-XX:SharedArchiveFile=xlumen.jsa` | 动态 CDS 共享归档（启动"镜像"） | 首次启动自动生成归档，之后直接内存映射复用：启动快 20~40%、类区内存省几十 MB；升级 jar 后归档自动失效并重建，无感自愈 |

CDS 补充说明（已在本机 JDK 25 二进制验证参数存在）：

- 首次启动日志出现 `CDS archive was not used` 属正常（建档中），第二次起生效。
- 升级 jar / 更换 JDK 后归档不匹配，JVM 自动忽略并以普通模式启动、随后重建，无需手工删除。
- 更可控的静态方案（构建/部署时训练一轮）：先 `java -XX:ArchiveClassesAtExit=app-cds.jsa -jar ...` 正常起一次再退出，日常启动改 `-XX:SharedArchiveFile=app-cds.jsa`（不加 AutoCreate）。
- Project Leyden AOT（`-XX:AOTMode`/`-XX:AOTCache`）JDK 25 已含但官标实验性、Spring Boot 4 仍在磨合，暂不使用。

不建议设置的项：

- **不要**在命令行重复 `--server.port`：端口由各环境 profile 的 `server.port` 唯一控制，两处并存易混乱。
- **`spring.threads.virtual.enabled=true` 暂不开**（已核验 Boot 4.1.0 默认 false）：该开关只影响 Tomcat 请求线程与 Spring 自动装配的 TaskExecutor；本应用对话/写作/通知的 SSE 生成跑在自建的平台线程池（`chatStreamExecutor` core2/max8、`aiTaskExecutor` core2/max4、工具池 4），开关管不到它们，SSE 占平台线程的瓶颈（OPT-1：改虚拟线程 + Semaphore）是代码改造而非启动参数。上线稳定后再评估。
- 不设 `-Xss`：JDK 25 默认线程栈 1m 足够（虚拟线程另有独立小栈机制）。

启动验证：

```bash
curl -s http://127.0.0.1:8080/actuator/health          # {"status":"UP"}
curl -s http://127.0.0.1:8080/actuator/health/readiness # 含 db、redis 探针
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/v3/api-docs  # 200（Swagger 契约）
```

日志中确认两条关键行：`Milvus 可达/不可达（降级为 NoopVectorStore…）`、无 `ClassNotFound`/绑定失败；日志出现 `Failed to bind` 说明 profile 键名拼写或缩进问题。

## 7. Nginx 配置（当前无域名：IP 直连）

> 当前服务器无域名，直接以 IP 访问：博客 `http://159.75.6.183`（80 端口）、管理后台 `http://159.75.6.183:8082`（nginx 独立端口）。两块各自把 `/api/` 反代到后端（正式 8080 / 测试 8081 二选一）。

### 7.1 博客前台（80 端口）+ API 反代

```nginx
# /etc/nginx/sites-available/xlumen-blog.conf
server {
    listen 80 default_server;        # 默认站点：根域名/IP 直接命中
    server_name _;

    root /wen/app/frontend/xlumen-frontend-blog;   # = 部署脚本 deploy-blog.sh 的产物目录
    index index.html;
    charset utf-8;

    # API + SSE 反代（SSE 必须关缓冲，否则流式吐字被攒批卡住）
    location /api/ {
        proxy_pass http://127.0.0.1:8080;          # 正式后端；若反代测试环境改为 8081
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # SPA history 路由 fallback（/search、/kb/:id、/studio/* 等直达地址）
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### 7.2 管理后台（8082 端口）

```nginx
# /etc/nginx/sites-available/xlumen-admin.conf —— 与博客块基本相同，差异仅注出
server {
    listen 8082;                     # ← 独立端口；不能设 default_server（一台机器只允许一个）
    server_name _;

    root /wen/app/frontend/xlumen-frontend-admin;  # ← 部署脚本 deploy-admin.sh 的产物目录
    index index.html;
    charset utf-8;

    location /api/ {                 # 后台与博客共用后端，代理配置完全相同
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

启用并重载：

```bash
ln -s /etc/nginx/sites-available/xlumen-blog.conf /etc/nginx/sites-enabled/
ln -s /etc/nginx/sites-available/xlumen-admin.conf /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

> 有域名后（可选）：两个 server 各加 `server_name` 与 443 ssl 块，用 `certbot --nginx` 一键签发证书并自动改写配置；无域名时保持 HTTP 直连即可。

> 静态资源缓存可选优化：`location /assets/ { expires 30d; add_header Cache-Control "public, immutable"; }`（Vite 产物带内容哈希，可放心长缓存）。

## 8. 上线验证清单

| # | 检查项 | 方法 / 预期 |
| --- | --- | --- |
| 1 | 后端存活 | `http://159.75.6.183/api/v1/…` 任意接口或 `curl http://127.0.0.1:8080/actuator/health` → `UP` |
| 2 | 数据库/Redis 就绪 | `/actuator/health/readiness` → `UP`（含 db、redis 组） |
| 3 | 前端首页 | 打开 `http://159.75.6.183` → 首页知识列表正常渲染、无控制台报错 |
| 4 | SPA 直达 | 直接访问 `/search`、`/kb/xxx`、创作中心路径（如有）→ 不 404 |
| 5 | 登录/注册 | 真实账号登录成功；验证码邮件（若配 SMTP）或见日志 |
| 6 | 对话流式 | 打开 AI 小光发一问 → 网页**边生成边显示**（验证 SSE 未缓冲） |
| 7 | 写作/审核链路 | 创作中心走一遍 写作→审校→AI 审核；管理后台审核中心可见 |
| 8 | 后台入口互跳 | 前台头像菜单“管理后台”新标签页打开 `http://159.75.6.183:8082`；后台“前往前台”回 `http://159.75.6.183` |
| 9 | 语义检索（可选） | 若配了 Milvus+Embedding：知识库检索/问答引用正常；未配则关键词检索可用、日志有 Noop 降级提示 |
| 10 | HTTPS | 无域名暂为 HTTP（IP 直连）；有域名后证书有效、无混合内容警告 |

## 9. 更新与回滚

**推荐：一键脚本**（服务器直编，三份五步）见第 11 节。

**手动路径**：

- **前端更新**：重新 build 后 `rsync`/覆盖 `/wen/app/frontend/` 对应目录即可，无需重启后端（静态文件）。
- **后端更新**：

```bash
cp /wen/app/backend/xlumen/prod/xlumen-boot.jar xlumen-boot.jar.bak
# 上传新 jar 覆盖 xlumen-boot.jar 后
systemctl restart xlumen && journalctl -u xlumen -f
```

- **回滚**：把 `.bak` 覆盖回去再 `systemctl restart xlumen`。数据库变更脚本若有，请按 第 5 节 在发布前后执行并保持幂等。

## 10. 常见问题排查

| 症状 | 原因 / 处理 |
| --- | --- |
| 对话/写作“一直不吐字” | Nginx `proxy_buffering off` 缺失或未生效（`nginx -T` 检查）；确认 `/api/` location 内含该指令 |
| 后端启动报配置绑定失败 | profile YAML 键名拼写/缩进错误；对照 `application-demo.yml` 逐键检查，别动结构只改值 |
| 8080 端口被占用 | `ss -ltnp | grep 8080` 找占用进程；生产 `XLUMEN_DEV_PORT_GUARD` 保持 `false` 不会自动杀 |
| 中文乱码 | MySQL 连接 URL 含 `characterEncoding=utf8`，库表 utf8mb4（已由 init SQL 保证）；Nginx `charset utf-8` |
| 登录后立即 401 | JWT 密钥与签发不一致（多实例/多次发布用了不同 `XLUMEN_JWT_SECRET`），统一为一个 |
| 忘记修改 JWT/AI 密钥上线 | 用示例值上线会被盗用，上线前务必轮换 |
| 时间差 8 小时 | `ExecStart` 已带 `-Duser.timezone=Asia/Shanghai`；确认位置生效 |
| Milvus 相关 WARN 日志 | 属预期降级：`Milvus 不可达，降级为 NoopVectorStore`，功能不影响，只是语义检索不可用 |

## 11. 一键部署脚本（scripts/ 三份，极简五步）

服务器装好构建工具链（git、JDK 25、Maven 3.9+、Node 20+、pnpm 9+）后，每次发版在服务器上跑对应份脚本即可，每份脚本固定五步：**拉取最新 git 代码 → 打包 → 停掉之前服务（前端为清旧产物）→ 启动新包（前端为部署新产物）→ 查询新包状态**。

| 脚本 | 部署对象 | 五步中的差异 |
| --- | --- | --- |
| `scripts/deploy-backend.sh <test|prod>` | 后端 fat jar | 双环境选择：`test`（分支 test / 源码 `/wen/project/backend/xlumen/test` / 8081）、`prod`（分支 master / 源码 `/wen/project/backend/xlumen/master` / 8080）；不用 systemctl（nohup 起新 + pkill 停旧）；部署前打印环境信息并要 y 确认；每步带时间戳日志 |
| `scripts/deploy-blog.sh` | 博客前台 dist | 源码 `/wen/project/frontend/xlumen-frontend-blog`，产物 `/wen/app/frontend/xlumen-frontend-blog`（nginx root）；停旧=`rm -rf` 旧产物，查状态=文件校验 |
| `scripts/deploy-admin.sh` | 管理后台 dist | 源码 `/wen/project/frontend/xlumen-frontend-admin`，产物 `/wen/app/frontend/xlumen-frontend-admin`（nginx root）；同 blog |

```bash
bash scripts/deploy-backend.sh test   # 后端发版到测试环境（8081）
bash scripts/deploy-backend.sh prod   # 后端发版到正式环境（8080）
bash scripts/deploy-blog.sh           # 博客前台发版
bash scripts/deploy-admin.sh          # 管理后台发版
```

脚本就是最朴素的直写：仓库地址、部署目录、互跳地址等**集中在脚本顶部配置区**，按你的服务器实际改一处即可（前端两份只有 URL/路径；后端那份还有 JDK 路径与双环境端口）。之后再无其他参数。要点：

- 后端新 jar 以稳定名 `xlumen-boot.jar` 落入 `/wen/app/backend/xlumen/<test|prod>/`，环境配置已随 jar 打包（改配置需重新构建部署）。
- 前端构建前会写 `.env.production`（blog 写 `VITE_ADMIN_URL`、admin 写 `VITE_BLOG_URL`），指向当前配置的互跳地址（无域名为 IP+端口，如 `http://159.75.6.183:8082`）。
- 密钥在各环境 profile（仓库内，依赖仓库私密性）；部署脚本不生成配置、不触碰服务器本地文件。

## 附录 A：Windows 服务器部署（简要）

与 Linux 流程一致，差异点：

- 后端：安装 JDK 25，在 jar 所在目录执行（环境配置内置 jar，启动加 `--spring.profiles.active=prod`）：

  ```powershell
  java -Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication `
    -XX:MaxMetaspaceSize=512m -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Shanghai `
    -jar xlumen-boot-0.1.0-SNAPSHOT.jar
  ```

  自启用「任务计划程序」或 NSSM 注册为服务（OOM 退出时自动重启）。
- Nginx：官方 Windows 版，`nginx.conf` 语法同上（路径用 `C:/...`）；注意 Windows Nginx 无 systemd，守护用 NSSM。
- profile 为 YAML 文本，UTF-8 保存即可（无 .env 的 BOM 坑）。