#!/usr/bin/env bash
# 脚本第一行：告诉系统用哪个解释器跑（/usr/bin/env bash = 从系统路径找 bash）

# ============================================================
# 博客前台部署：拉代码 → 打包 → 清旧 → 部署新包 → 查状态
# 说明：前端是静态文件，清旧产物即"停旧"，复制新 dist 即"起新"，
#       产物目录就是 nginx 站点根目录，部署完立即生效
# ============================================================

# set 安全开关：-e 出错即停；-u 变量未定义报错；pipefail 管道任一段失败都算失败
set -euo pipefail

# ============================================================
# 配置区：所有路径和参数都集中在这里，按需修改
# ============================================================
REPO_URL=https://github.com/calwenb/x-lumen.git           # 你的仓库地址（首次 clone 用）
SRC=/wen/project/frontend/xlumen-frontend-blog            # 源码目录（测试/正式共用）
APP=/wen/app/frontend/xlumen-frontend-blog                # 产物目录（nginx root 指向它）
ADMIN_URL=http://159.75.6.183:8082                        # 构建期写入 VITE_ADMIN_URL（后台入口；无域名用 IP+端口，有域名后改 https://admin.域名）

# ============================================================
# 下面基本不用改
# ============================================================

# 日志函数：每行带时间戳
log() { echo "[$(date '+%F %T')] $*"; }

# ================= 1. 拉代码 =================
log "== [1/5] 拉取最新代码（master 分支）=="
if [[ -d "$SRC/.git" ]]; then                       # 目录里已有 git 仓库
    cd "$SRC"
    git pull --ff-only origin master                # 拉最新；--ff-only 有冲突直接失败不乱合
else                                                # 目录为空 / 没有 git 文件
    log "源码目录为空，首次部署自动 clone ..."
    mkdir -p "$(dirname "$SRC")"                    # 先建上层目录，否则 git clone 报错
    git clone "$REPO_URL" "$SRC"                    # clone 整个仓库到目标目录
    cd "$SRC"
fi

# ================= 2. 打包 =================
log "== [2/5] 打包 =="
pnpm install                                        # 装依赖（没变化时几秒过）
# 构建期写入"后台入口"地址，编译时打进页面（.env.production 留在仓库里，构建完不用还原）
echo "VITE_ADMIN_URL=$ADMIN_URL" > frontend/xlumen-frontend-blog/.env.production
pnpm --dir frontend/xlumen-frontend-blog build      # 编译产物在 frontend/xlumen-frontend-blog/dist

# ================= 3. 停掉之前服务（清旧产物） =================
log "== [3/5] 清掉旧产物 =="
rm -rf "$APP"

# ================= 4. 启动新包（部署新产物） =================
log "== [4/5] 部署新产物 =="
mkdir -p "$(dirname "$APP")"
cp -r frontend/xlumen-frontend-blog/dist "$APP"     # 整个 dist 复制为站点目录，nginx 立即生效

# ================= 5. 查询新包状态 =================
log "== [5/5] 查询新包状态 =="
echo "文件数：$(find "$APP" -type f | wc -l)  大小：$(du -sh "$APP" | cut -f1)"
ls -l "$APP/index.html"
