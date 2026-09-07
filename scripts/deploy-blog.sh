#!/usr/bin/env bash
# 脚本第一行：告诉系统用哪个解释器跑（/usr/bin/env bash = 从系统路径找 bash）

# ============================================================
# xLumen 博客前台一键部署：拉代码 → 打包 → 清旧产物 → 部署新产物 → 查状态
# 用法：bash deploy-blog.sh <test|prod>
#   test = 测试环境（test 分支 / 产物目录 .../test / nginx 6010 站点）
#   prod = 正式环境（master 分支 / 产物目录 .../prod / nginx 80+5010 站点）
# 说明：前端是静态文件，清旧产物即"停旧"，复制新 dist 即"起新"，
#       产物目录就是 nginx 站点根目录，部署完立即生效
# ============================================================

# set 安全开关：-e 出错即停；-u 变量未定义报错；pipefail 管道任一段失败都算失败
set -euo pipefail

# ============================================================
# 配置区：所有路径和参数都集中在这里，按需修改
# ============================================================

# ---- 公共配置 ----
REPO_URL=https://github.com/calwenb/x-lumen.git           # ← 你的仓库地址（首次 clone 用）

# ---- 测试环境 test（test 分支；产物目录由 nginx 6010 站点指向）----
BRANCH_TEST=test                                 # 测试环境 git 分支
SRC_TEST=/wen/project/frontend/xlumen-frontend-blog/test   # 测试源码目录（与正式独立，各拉各的分支）
APP_TEST=/wen/app/frontend/xlumen-frontend-blog/test       # 测试产物目录（nginx root）
ADMIN_URL_TEST=http://159.75.6.183:6011          # 测试构建期 VITE_ADMIN_URL（后台入口走测试 601x 端口段）

# ---- 正式环境 prod（master 分支；产物目录由 nginx 80+5010 站点指向）----
BRANCH_PROD=master                               # 正式环境 git 分支
SRC_PROD=/wen/project/frontend/xlumen-frontend-blog/prod   # 正式源码目录
APP_PROD=/wen/app/frontend/xlumen-frontend-blog/prod       # 正式产物目录（nginx root）
ADMIN_URL_PROD=http://159.75.6.183:5011          # 正式构建期 VITE_ADMIN_URL（后台入口 5011）

# ============================================================
# 下面基本不用改
# ============================================================

# 根据第一个参数（$1）选中环境；${1:-} 意思是"取第一个参数，没传当空串"
case "${1:-}" in
    test) ENV=test; BRANCH=$BRANCH_TEST; SRC=$SRC_TEST; APP=$APP_TEST; ADMIN_URL=$ADMIN_URL_TEST ;;
    prod) ENV=prod; BRANCH=$BRANCH_PROD; SRC=$SRC_PROD; APP=$APP_PROD; ADMIN_URL=$ADMIN_URL_PROD ;;
    *)
        echo "用法: bash $0 <test|prod>"; exit 1   # 没传参或参数不对：打印用法退出
        ;;
esac

# 日志函数：每行自动带 时间戳 + 环境名，方便区分 test/prod 输出
log() { echo "[$(date '+%F %T')] [$ENV] $*"; }

# ================= 0. 部署前确认 =================
echo "环境：$ENV | git 分支：$BRANCH | 源码目录：$SRC | 产物目录：$APP | 后台入口：$ADMIN_URL"
read -r -p "确认部署到 $ENV 环境？[y/N] " ans      # 防手滑选错环境，按 y 才继续
[[ "$ans" =~ ^[yY]$ ]] || { log "已取消"; exit 1; }  # 不是 y/Y 就取消

# ================= 1. 拉代码 =================
log "== [1/5] 拉取最新代码（$BRANCH 分支）=="
if [[ -d "$SRC/.git" ]]; then                       # 目录里已有 git 仓库
    cd "$SRC"
    git pull --ff-only origin "$BRANCH"             # 拉最新；--ff-only 有冲突直接失败不乱合
else                                                # 目录为空 / 没有 git 文件
    log "源码目录为空，首次部署自动 clone ..."
    mkdir -p "$(dirname "$SRC")"                    # 先建上层目录，否则 git clone 报错
    git clone -b "$BRANCH" "$REPO_URL" "$SRC"       # 按分支 clone 到目标目录
    cd "$SRC"
fi

# ================= 2. 打包 =================
log "== [2/5] 打包（pnpm）=="
pnpm install                                        # 装依赖（没变化时几秒过）
# 构建期写入"后台入口"地址，编译时打进页面（Vite build 自动读 .env.production）
# 该文件由脚本生成、不入库（根 .gitignore 已忽略 frontend/**/.env.production），环境专属值不会弄脏 git
echo "VITE_ADMIN_URL=$ADMIN_URL" > frontend/xlumen-frontend-blog/.env.production
pnpm --dir frontend/xlumen-frontend-blog build      # 编译产物在 frontend/xlumen-frontend-blog/dist

# ================= 3. 清旧产物 =================
log "== [3/5] 清掉旧产物 =="
rm -rf "$APP"                                       # 只删本环境的产物目录，test/prod 互不影响

# ================= 4. 部署新产物 =================
log "== [4/5] 部署新产物 =="
mkdir -p "$(dirname "$APP")"                        # 首次部署上层目录不存在，先建好
cp -r frontend/xlumen-frontend-blog/dist "$APP"     # 整个 dist 复制为站点目录，nginx 立即生效

# ================= 5. 查状态 =================
log "== [5/5] 查询新包状态 =="
echo "文件数：$(find "$APP" -type f | wc -l)  大小：$(du -sh "$APP" | cut -f1)"
ls -l "$APP/index.html"
log "== 部署完成（$ENV）== 浏览器访问对应站点验证（test=6010 / prod=80 或 5010）"
