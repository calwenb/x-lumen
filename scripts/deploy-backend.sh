#!/usr/bin/env bash
# 脚本第一行：告诉系统用哪个解释器跑（/usr/bin/env bash = 从系统路径找 bash）

# ============================================================
# xLumen 后端一键部署：拉代码 → 打包 → 停旧 → 起新 → 查状态
# 用法：bash deploy-backend.sh <test|prod>
#   test = 测试环境   prod = 正式环境
# ============================================================

# set 安全开关：-e 出错即停；-u 变量未定义报错；pipefail 管道任一段失败都算失败
set -euo pipefail

# ============================================================
# 配置区：所有路径和参数都集中在这里，按需修改
# ============================================================

# ---- 公共配置 ----
REPO_URL=https://github.com/calwenb/x-lumen.git     # ← 你的仓库地址（首次 clone 用）
JAVA_HOME_DIR=/wen/env/jdk-25                  # JDK 25 安装目录（项目强制要求）
JAR=xlumen-boot.jar                                 # 部署到产物目录的 jar 稳定名

# ---- 测试环境 test（git 分支 test / 端口 8081，与 application-test.yml 的 server.port 一致）----
BRANCH_TEST=test                                 # 测试环境 git 分支
SRC_TEST=/wen/project/backend/xlumen/test        # 测试环境源码目录
APP_TEST=/wen/app/backend/xlumen/test            # 测试产物目录（jar + config/application-test.yml，D30 配置不入库）
LOG_TEST=/wen/log/backend/xlumen/test            # 测试环境日志目录
PORT_TEST=8081                                   # 测试端口

# ---- 正式环境 prod（git 分支 master / 端口 8080，Nginx 反代目标）----
BRANCH_PROD=master                               # 正式环境 git 分支
SRC_PROD=/wen/project/backend/xlumen/master      # 正式环境源码目录
APP_PROD=/wen/app/backend/xlumen/prod            # 正式产物目录
LOG_PROD=/wen/log/backend/xlumen/prod            # 正式环境日志目录
PORT_PROD=8080                                   # 正式端口

# ============================================================
# 下面基本不用改
# ============================================================

# 根据第一个参数（$1）选中环境；${1:-} 意思是"取第一个参数，没传当空串"
case "${1:-}" in
    test) ENV=test; BRANCH=$BRANCH_TEST; SRC=$SRC_TEST; APP=$APP_TEST; LOG=$LOG_TEST; PORT=$PORT_TEST ;;
    prod) ENV=prod; BRANCH=$BRANCH_PROD; SRC=$SRC_PROD; APP=$APP_PROD; LOG=$LOG_PROD; PORT=$PORT_PROD ;;
    *)
        echo "用法: bash $0 <test|prod>"; exit 1   # 没传参或参数不对：打印用法退出
        ;;
esac

# 日志函数：每行自动带 时间戳 + 环境名，方便区分 test/prod 输出
log() { echo "[$(date '+%F %T')] [$ENV] $*"; }

# 环境 profile 校验（决策 D30：profile 不入库、不打进 jar，放产物目录 config/ 外部加载）
# 放在停旧进程之前：缺配置就中止，避免"旧的停了、新的起不来"的两不管
if [[ ! -f "$APP/config/application-$ENV.yml" ]]; then
    log "错误：缺少 $APP/config/application-$ENV.yml"
    log "请从仓库 application-demo.yml 复制并按实际值填写（该文件不入库，需手工放置）"
    exit 1
fi

# ================= 0. 部署前确认 =================
echo "环境：$ENV | git 分支：$BRANCH | 产物目录：$APP | 日志目录：$LOG | 端口：$PORT"
read -r -p "确认部署到 $ENV 环境？[y/N] " ans      # 防手滑选错环境，按 y 才继续
[[ "$ans" =~ ^[yY]$ ]] || { log "已取消"; exit 1; }  # 不是 y/Y 就取消

# ================= 1. 拉代码 =================
log "== [1/5] 拉取最新代码（$BRANCH 分支）=="
if [[ -d "$SRC/.git" ]]; then                     # 目录里已有 git 仓库
    cd "$SRC"
    git pull --ff-only origin "$BRANCH"           # 拉最新；--ff-only 有冲突直接失败不乱合
else                                              # 目录为空 / 没有 git 文件
    log "源码目录为空，首次部署自动 clone ..."
    mkdir -p "$(dirname "$SRC")"                  # 先建上层目录，否则 git clone 报错
    git clone -b "$BRANCH" "$REPO_URL" "$SRC"     # 按分支 clone 到目标目录
    cd "$SRC"
fi

# ================= 2. 打包 =================
log "== [2/5] 打包（maven）=="
export JAVA_HOME="$JAVA_HOME_DIR"                 # 让 maven/java 找到 JDK 25
export PATH="$JAVA_HOME/bin:$PATH"                # JDK25 的 bin 置前，确保不用旧版 java
cd "$SRC/backend/xlumen-server"
mvn -pl xlumen-boot -am package -DskipTests  # -DskipTests 跳过测试
JAR_FILE="$(ls -t xlumen-boot/target/xlumen-boot-*.jar | head -1)"   # 取最新生成的 jar
log "打包完成：$(basename "$JAR_FILE")"

# ================= 3. 停旧 =================
log "== [3/5] 停掉旧进程 =="
pkill -f "$APP/$JAR" 2>/dev/null || true   # 按 jar 路径发 SIGTERM 优雅停机；没在跑就跳过
sleep 3                                    # 等 3 秒让 Spring 收尾并释放端口

# ================= 4. 起新 =================
log "== [4/5] 启动新包 =="
mkdir -p "$APP" "$LOG"                            # 首次部署目录不存在，先建好
cp -f "$JAR_FILE" "$APP/$JAR"                     # 新 jar 复制到产物目录（稳定名）
cd "$APP"                                         # 工作目录=jar 目录（logback 日志相对落盘；Spring 从 ./config/application-<env>.yml 外部读配置，D30）
# 堆内存 256M；显式时区 Asia/Shanghai 防 UTC 差 8 小时；显式 UTF-8 与编码铁律一致
# 注意：--spring.profiles.active 必须放在 -jar 之后（-jar 前的参数由 JVM 解析，放前面会报 Unrecognized option 直接起不来）
nohup "$JAVA_HOME_DIR/bin/java" \
    -Xms256m -Xmx256m \
    -Duser.timezone=Asia/Shanghai \
    -Dfile.encoding=UTF-8 \
    -jar "$APP/$JAR" \
    --spring.profiles.active="$ENV" > "$LOG/app.log" 2>&1 &
log "新进程已启动，日志：$LOG/app.log"

# ================= 5. 查状态 =================
log "== [5/5] 查询新包状态 =="
for i in $(seq 1 10); do                          # 最多查 20 轮 = 60 秒（Spring 冷启动要二三十秒）
    STATUS="$(curl -fsS "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null || true)"
    # curl：-f 非 2xx 当错误、-s 静默、-S 出错仍显示；|| true 保证接口没起来不触发 set -e
    [[ "$STATUS" == *'"status":"UP"'* ]] && break # 返回 UP 即部署成功，跳出循环
    sleep 3
done
log "健康检查：$STATUS"
log "== 部署完成（$ENV）== 实时日志：tail -f $LOG/app.log"
