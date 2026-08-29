#!/usr/bin/env bash
# 脚本第一行：告诉系统用哪个解释器跑（/usr/bin/env bash = 从系统路径找 bash）

# ============================================================
# xLumen 后端一键部署：拉代码 → 打包 → 停旧 → 起新 → 查状态
# 用法：bash deploy-backend.sh <test|prod>
#   test = 测试环境（分支 test / 目录 repo-test / 端口 8081）
#   prod = 正式环境（分支 master / 目录 repo-master / 端口 8080）
# 说明：
#   - 源码目录可能为空（无 git 文件），首次部署自动 clone
#   - 不用 systemctl：nohup 后台启动，停旧用 pkill 优雅停机
# ============================================================

# set 安全开关：-e 出错即停；-u 变量未定义报错；pipefail 管道任一段失败都算失败
set -euo pipefail

# ================= 环境配置 =================
REPO_URL=https://github.com/yourname/xlumen.git    # ← 改成你的仓库地址（首次 clone 用）

case "${1:-}" in                                  # ${1:-} 取第一个参数，没传当空串
    test)
        ENV=test; BRANCH=test                     # 测试环境：git test 分支
        SRC=/opt/xlumen/repo-test                 # 测试环境源码目录
        APP=/opt/xlumen/app-test                  # 测试环境部署目录（内含自己 config/.env）
        PORT=8081                                 # 测试端口（须与 .env 的 XLUMEN_SERVER_PORT 一致）
        ;;
    prod)
        ENV=prod; BRANCH=master                   # 正式环境：git master 分支
        SRC=/opt/xlumen/repo-master               # 正式环境源码目录
        APP=/opt/xlumen/app                       # 正式环境部署目录
        PORT=8080                                 # 正式端口（Nginx 反代目标）
        ;;
    *)
        echo "用法: bash $0 <test|prod>"; exit 1  # 没传参或参数不对：打印用法退出
        ;;
esac

JAVA_HOME_DIR=/usr/local/jdk-25                   # JDK 25 安装目录（项目强制要求）
JAR=xlumen-boot.jar                               # 部署到 APP 目录的 jar 稳定名

# 日志函数：每行自动带 时间戳 + 环境名，方便区分 test/prod 输出
log() { echo "[$(date '+%F %T')] [$ENV] $*"; }

# ================= 0. 部署前确认 =================
echo "环境：$ENV | git 分支：$BRANCH | 部署目录：$APP | 端口：$PORT"
read -r -p "确认部署到 $ENV 环境？[y/N] " ans      # 防手滑选错环境，按 y 才继续
[[ "$ans" =~ ^[yY]$ ]] || { log "已取消"; exit 1; }  # 不是 y/Y 就取消

# ================= 1. 拉代码 =================
log "== [1/5] 拉取最新代码（$BRANCH 分支）=="
if [[ -d "$SRC/.git" ]]; then                     # 目录里已有 git 仓库
    cd "$SRC"
    git pull --ff-only origin "$BRANCH"           # 拉最新；--ff-only 有冲突直接失败不乱合
else                                              # 目录为空 / 没有 git 文件
    log "源码目录为空，首次部署自动 clone ..."
    git clone -b "$BRANCH" "$REPO_URL" "$SRC"     # 按分支 clone 到目标目录
    cd "$SRC"
fi

# ================= 2. 打包 =================
log "== [2/5] 打包（maven -T 1C 多线程）=="
export JAVA_HOME="$JAVA_HOME_DIR"                 # 让 maven/java 找到 JDK 25
export PATH="$JAVA_HOME/bin:$PATH"                # JDK25 的 bin 置前，确保不用旧版 java
cd "$SRC/backend/xlumen-server"
mvn -T 1C -pl xlumen-boot -am package -DskipTests  # -T 1C 每核一线程并行；-DskipTests 跳过测试
JAR_FILE="$(ls -t xlumen-boot/target/xlumen-boot-*.jar | head -1)"   # 取最新生成的 jar
log "打包完成：$(basename "$JAR_FILE")"

# ================= 3. 停旧 =================
log "== [3/5] 停掉旧进程 =="
pkill -f "$APP/$JAR" 2>/dev/null || true   # 按 jar 路径发 SIGTERM 优雅停机；没在跑就跳过
sleep 3                                    # 等 3 秒让 Spring 收尾并释放端口

# ================= 4. 起新 =================
log "== [4/5] 启动新包 =="
mkdir -p "$APP/logs"                              # 准备日志目录
cp -f "$JAR_FILE" "$APP/$JAR"                     # 新 jar 复制到部署目录（稳定名）
cd "$APP"                                         # 工作目录=jar 目录，Spring 才能找到 config/.env
nohup "$JAVA_HOME_DIR/bin/java" \
    -Xms512m -Xmx1g \                             # 堆内存：初始 512M、上限 1G（核心参数）
    -Duser.timezone=Asia/Shanghai \               # 显式时区，防服务器 UTC 差 8 小时
    -Dfile.encoding=UTF-8 \                       # 显式 UTF-8，与 .env 编码铁律一致
    -jar "$APP/$JAR" > "$APP/logs/app.log" 2>&1 & # nohup 后台运行；输出进 app.log；& 放后台
log "新进程已启动，日志：$APP/logs/app.log"

# ================= 5. 查状态 =================
log "== [5/5] 查询新包状态 =="
for i in $(seq 1 20); do                          # 最多查 20 轮 = 60 秒（Spring 冷启动要二三十秒）
    STATUS="$(curl -fsS "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null || true)"
    # curl：-f 非 2xx 当错误、-s 静默、-S 出错仍显示；|| true 保证接口没起来不触发 set -e
    [[ "$STATUS" == *'"status":"UP"'* ]] && break # 返回 UP 即部署成功，跳出循环
    sleep 3
done
log "健康检查：$STATUS"
log "== 部署完成（$ENV）== 实时日志：tail -f $APP/logs/app.log"