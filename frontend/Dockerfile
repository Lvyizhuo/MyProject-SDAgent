# ===========================================
# 前端 Dockerfile
# 包管理器：pnpm（版本由 package.json 的 packageManager 字段锁定）
# Node 版本与 CI 保持一致：22
# ===========================================

# 构建阶段
FROM node:22-alpine AS builder

WORKDIR /app

# 通过 Corepack 启用 pnpm，版本取 package.json -> packageManager
# COREPACK_ENABLE_DOWNLOAD_PROMPT=0 避免构建时出现交互式下载确认
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable

# 先只复制依赖清单，最大化利用层缓存
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

# 复制源码并构建（.dockerignore 已排除 node_modules/dist 等本地产物）
COPY . ./
# 生产环境需要设置 API 代理地址
ENV VITE_DEV_PROXY_TARGET=http://backend:8080
RUN pnpm build

# 运行阶段 - 使用 Nginx
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
