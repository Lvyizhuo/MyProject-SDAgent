#!/bin/sh
# ===========================================
# nginx 访问日志「按天切换」+ 过期清理
# ===========================================
# 由 busybox crond 每日 00:05 调用（见 frontend/docker/crontab）。
#
# 与后端 / 重排服务保持同一套规范：
#   - 每个组件每天一个文件：/app/logs/<组件>-<YYYY-MM-DD>.log
#   - 不压缩，便于直接查看排查
#   - 保留 30 天
#
# 实现要点：
#   nginx 的 access_log 路径含变量（$log_date），只有在 reopen 时才会重新求值。
#   因此这里不发 USR1 之外做任何改名动作 —— 昨天的文件本来就叫
#   frontend-<昨天日期>.log，reopen 后 nginx 会用当天日期另开一个文件，
#   无需 mv / 无需 gzip。
#   error_log 是固定名（nginx 不支持变量路径），不需要处理。

set -eu

LOG_DIR=/app/logs
RETENTION_DAYS=30

[ -d "$LOG_DIR" ] || exit 0

# 1) 触发 nginx reopen：重新求值 $log_date，按当天日期另开一个日志文件
if [ -f /var/run/nginx.pid ]; then
  kill -USR1 "$(cat /var/run/nginx.pid)" 2>/dev/null || true
fi

# 2) 清理超过保留期的访问日志
find "$LOG_DIR" -maxdepth 1 -type f -name 'frontend-*.log' \
  -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true

exit 0
