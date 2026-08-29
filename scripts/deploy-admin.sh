#!/usr/bin/env bash
# 管理后台部署：拉代码 → 打包 → 清旧 → 部署新包 → 查状态
set -e

# 1. 拉取最新代码
cd /opt/xlumen/repo && git pull

# 2. 打包
cd frontend/xlumen-frontend-admin
pnpm install
echo "VITE_BLOG_URL=https://www.xlumen.dev" > .env.production
pnpm build

# 3. 停掉之前服务（清旧产物）
rm -rf /opt/xlumen/dist/admin

# 4. 启动新包（部署新产物）
cp -r dist /opt/xlumen/dist/admin

# 5. 查询新包状态
ls -l /opt/xlumen/dist/admin/index.html