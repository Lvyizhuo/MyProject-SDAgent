#!/bin/sh
# ===========================================
# 后端容器启动脚本
# ===========================================
# 职责：
#   1) 注入当天日期 LOG_DATE —— 活动日志文件名 backend-<日期>.log
#   2) 注入日志目录 LOGGING_FILE_PATH
#   3) 清理超过保留期的旧日志文件（默认 30 天）
#   4) 以 exec 启动 JVM（保证 java 是 PID 1，容器停止信号能直达 JVM）
set -e

# 当天日期（形如 2026-11-10）。允许外部覆盖，便于测试。
export LOG_DATE="${LOG_DATE:-$(date +%Y-%m-%d)}"

# 日志目录
export LOGGING_FILE_PATH="${LOGGING_FILE_PATH:-/app/logs}"

# 日志保留天数
LOG_RETENTION_DAYS="${LOG_RETENTION_DAYS:-30}"

# 清理过期日志（只删日志目录顶层的 .log 文件，不动其它内容）
if [ -d "$LOGGING_FILE_PATH" ]; then
  find "$LOGGING_FILE_PATH" -maxdepth 1 -type f -name '*.log' \
    -mtime "+${LOG_RETENTION_DAYS}" -delete 2>/dev/null || true
fi

exec java -jar -Xms512m -Xmx1024m app.jar
