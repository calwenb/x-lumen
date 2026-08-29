#!/usr/bin/env bash
# 博客前台部署：拉代码 → 打包 → 清旧 → 部署新包 → 查状态
set -e

# 1. 拉取最新代码
cd /opt/xlumen/repo && git pull

# 2. 打包
cd frontend/xlumen-frontend-blog
pnpm install
echo "VITE_ADMIN_URL=https://admin.xlumen.dev" > .env.production
pnpm build

# 3. 停掉之前服务（清旧产物）
rm -rf /opt/xlumen/dist/blog

# 4. 启动新包（部署新产物）
cp -r dist /opt/xlumen/dist/blog

# 5. 查询新包状态
ls -l /opt/xlumen/dist/blog/index.html