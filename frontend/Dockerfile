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

# ===========================================
# 日志：统一格式 + 按天一个文件（容器内部 /app/logs）
# ===========================================
# 官方镜像把 /var/log/nginx/access.log 做成指向 stdout 的符号链接。
# 这里移除该符号链接，改由 nginx.conf 直接写 /app/logs/frontend-<日期>.log
# （日期由 $time_iso8601 经 map 提取，nginx 每次 reopen 时重新求值）。
# stdout 侧由 nginx.conf 的第二条 access_log 保留，docker compose logs 仍可用。
# error.log 也统一到 /app/logs（nginx 的 error_log 路径不支持变量，故用固定名）。
#
# 目录必须归 nginx 用户所有：nginx 的 master 以 root 启动，但 **worker 以 nginx 用户运行**，
# 新日志文件是由 worker 首次写入时打开的 —— 目录若属 root:root，
# worker 就会报 "open() ... failed (13: Permission denied) while logging request"。
RUN rm -f /var/log/nginx/access.log /var/log/nginx/error.log \
    && mkdir -p /app/logs \
    && chown nginx:nginx /app/logs

# 按天切换脚本 + busybox crond 任务表
COPY docker/log-rotate.sh /usr/local/bin/log-rotate.sh
COPY docker/crontab /etc/crontabs/root
RUN chmod +x /usr/local/bin/log-rotate.sh

EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1/health || exit 1

# crond 负责每日执行日志归档；nginx 仍以前台方式运行（PID 1）
CMD ["sh", "-c", "crond && exec nginx -g 'daemon off;'"]
