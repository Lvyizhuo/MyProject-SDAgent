#!/usr/bin/env bash
# ===========================================
# 公共库：跨平台依赖检测、容器等待、健康校验
# 由 scripts/deploy.sh 与 scripts/dev.sh 共同 source
# 本文件只提供函数，不应直接执行
# ===========================================

# ---------- 日志 ----------

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

log_step() {
  printf '\n[%s] ===== %s =====\n' "$(date '+%F %T')" "$*"
}

log_warn() {
  printf '[%s] WARN: %s\n' "$(date '+%F %T')" "$*" >&2
}

log_error() {
  printf '[%s] ERROR: %s\n' "$(date '+%F %T')" "$*" >&2
}

die() {
  log_error "$*"
  exit 1
}

# ---------- 全局错误陷阱 ----------
#
# 背景：脚本使用 `set -Eeuo pipefail`。当某条命令返回非 0 且没有被 `||` / `if` 保护时，
# shell 会直接退出。问题在于 **bash 对某些失败（比如无参数时的 `shift`）不打印任何错误**，
# 表现为"脚本跑了一下就没反应、退出码 1、stdout/stderr 全空"，极难排查。
#
# 这里用 ERR trap 把失败点（行号 + 原始命令 + 退出码）打印出来，
# 保证任何未预期的失败都至少留下一条可读线索。

_on_err() {
  local code="$1" line="$2" cmd="$3"
  # 先关掉 trap，避免 log_error 自身失败时递归触发
  trap - ERR
  # 只打印诊断，不主动 exit：
  # 退出由 set -e 负责，退出码仍是原始命令的退出码，控制流保持不变。
  log_error "命令失败（退出码 ${code}）| 行 ${line} | ${cmd}"
}

trap '_on_err "$?" "$LINENO" "$BASH_COMMAND"' ERR

# ---------- 平台识别 ----------

# detect_platform → linux | macos | windows
detect_platform() {
  local os
  os="$(uname -s 2>/dev/null || echo unknown)"
  case "$os" in
    Linux*)  echo "linux" ;;
    Darwin*) echo "macos" ;;
    MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
    *)      echo "unknown" ;;
  esac
}

# 供 compose 使用：Windows Git Bash 需要显式关闭路径转换，否则 /api/ 会被转成 Windows 路径
compose_path_args() {
  if [[ "$(detect_platform)" == "windows" ]]; then
    echo "MSYS_NO_PATHCONV=1"
  fi
}

# ---------- 依赖检测 ----------

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

# require_cmd <命令> <提示信息>
require_cmd() {
  local cmd="$1" hint="$2"
  if ! has_cmd "$cmd"; then
    log_error "缺少命令: $cmd"
    [[ -n "$hint" ]] && log_error "$hint"
    exit 1
  fi
}

# ---------- 过期日志清理 ----------

# purge_expired_logs <目录> [保留天数] [文件名通配]
# 删除目录下超过保留期的日志文件（按 mtime 判断）。
# 不压缩策略下磁盘增长较快，启动时清理一次即可保证不无限累积。
purge_expired_logs() {
  local dir="$1" days="${2:-30}" pattern="${3:-*.log}"
  [[ -d "$dir" ]] || return 0
  find "$dir" -maxdepth 1 -type f -name "$pattern" -mtime "+${days}" -delete 2>/dev/null || true
}

# ---------- 端口检测 ----------

# port_in_use <端口> → 返回 0 表示被占用
#
# 判定同时做两件事，缺一不可：
#   1) bind 测试 —— 能否真正绑定该端口
#      （lsof 在受限环境/容器里常常不可用或权限不足，会误报"空闲"，
#        因此这里不依赖 lsof；绑 0.0.0.0 与 :: 两个地址以覆盖 IPv6-only 服务）
#   2) connect 测试 —— 能否连上
#      某些环境（IDE 代理、端口转发、sidecar）会"接管"一个端口：
#      bind 依然成功，但连接会被转发到别处（典型表现是返回 502）。
#      只做 bind 会漏判，必须补一次 connect。
#
# 判定规则：bind 失败 → 占用；bind 成功但 connect 成功 → 占用（被接管）；否则空闲。
# SO_REUSEADDR 保证处于 TIME_WAIT 的端口不算占用。
port_in_use() {
  local port="$1"

  if has_cmd python3; then
    python3 - "$port" <<'PY'
import socket
import sys

port = int(sys.argv[1])

# 1) bind 测试
bind_ok = True
for family, addr in ((socket.AF_INET, "0.0.0.0"), (socket.AF_INET6, "::")):
    try:
        s = socket.socket(family, socket.SOCK_STREAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((addr, port))
        s.close()
    except OSError:
        bind_ok = False
        break

if not bind_ok:
    sys.exit(0)  # 绑不上 → 被占用

# 2) connect 测试（捕捉"端口被代理接管"的情况）
try:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(1.5)
    s.connect(("127.0.0.1", port))
    s.close()
    sys.exit(0)  # 能连上 → 有人在服务这个端口
except OSError:
    pass

sys.exit(1)  # 既能绑又连不上 → 空闲
PY
    return $?
  fi

  # 没有 python3 时退回 lsof / netstat / curl
  if has_cmd lsof; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && return 0
    return 1
  fi
  if has_cmd netstat; then
    netstat -ano 2>/dev/null | grep -q ":${port} .*LISTENING" && return 0
    return 1
  fi
  if has_cmd curl; then
    curl -fsS --max-time 1 "http://127.0.0.1:${port}" >/dev/null 2>&1 && return 0
  fi
  return 1
}

# port_owner <端口> → 打印占用进程（不可用时输出空，不影响主流程）
port_owner() {
  local port="$1"
  if has_cmd lsof; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | tail -n +2 | head -3
  fi
}

# ---------- 容器状态等待 ----------

# container_status <容器名>
container_status() {
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$1" 2>/dev/null || true
}

# wait_container_healthy <容器名> <超时秒> <服务名>
# 返回 0 = 已 healthy；1 = 超时
wait_container_healthy() {
  local container="$1" timeout_sec="$2" label="$3"
  local start_ts current
  start_ts="$(date +%s)"
  log "等待 $label ($container) 就绪..."
  while true; do
    current="$(container_status "$container")"
    if [[ "$current" == "healthy" ]]; then
      log "$label 已就绪"
      return 0
    fi
    if (( $(date +%s) - start_ts >= timeout_sec )); then
      log_warn "$label 未在 ${timeout_sec}s 内变为 healthy（当前状态: ${current:-unknown}）"
      return 1
    fi
    sleep 5
  done
}

# wait_container_exited <容器名> <超时秒> <服务名>
wait_container_exited() {
  local container="$1" timeout_sec="$2" label="$3"
  local start_ts state
  start_ts="$(date +%s)"
  log "等待 $label ($container) 执行完毕..."
  while true; do
    state="$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null || true)"
    if [[ "$state" == "exited" ]]; then
      log "$label 已执行完毕"
      return 0
    fi
    if (( $(date +%s) - start_ts >= timeout_sec )); then
      log_warn "$label 未在 ${timeout_sec}s 内结束（当前状态: ${state:-unknown}）"
      return 1
    fi
    sleep 5
  done
}

# wait_http_200 <URL> <超时秒> <标签>
wait_http_200() {
  local url="$1" timeout_sec="$2" label="$3"
  local start_ts
  start_ts="$(date +%s)"
  log "等待 $label 就绪..."
  while true; do
    if curl -fsS --max-time "${HTTP_TIMEOUT_SEC:-10}" "$url" >/dev/null 2>&1; then
      log "$label 已就绪"
      return 0
    fi
    if (( $(date +%s) - start_ts >= timeout_sec )); then
      log_warn "$label 未在 ${timeout_sec}s 内可访问"
      return 1
    fi
    sleep 5
  done
}

# ---------- 健康校验 ----------

# verify_backend_health：后端健康接口返回 status=UP
#
# 注意：application.yml 里 management.endpoint.health.show-details=never，
# 健康响应只有 {"status":"UP"}，不包含 knowledgeMigration 等子项
# （旧脚本校验 `knowledgeMigration":{"status":"UP"` 属于永不成立的失效检查）。
# 知识库迁移结果改由 /api/chat/health 探测。
verify_backend_health() {
  local health_json
  health_json="$(curl -fsS --max-time "${HTTP_TIMEOUT_SEC:-10}" "${1:-http://127.0.0.1:8080/actuator/health}" 2>/dev/null || true)"
  [[ "$health_json" == *'"status":"UP"'* ]]
}

# verify_knowledge_migration：探测知识库迁移状态（独立端点，prod 下也可用）
verify_knowledge_migration() {
  local url="${1:-http://127.0.0.1:8080/api/chat/health}"
  local body
  body="$(curl -fsS --max-time "${HTTP_TIMEOUT_SEC:-10}" "$url" 2>/dev/null || true)"
  [[ -n "$body" ]] || return 1
  # 端点可达且未报告 DOWN 即视为通过
  [[ "$body" != *'"status":"DOWN"'* ]]
}

# verify_ollama_model：bge-m3 优先，nomic-embed-text 作为降级也算通过（仅告警）
verify_ollama_model() {
  local model_list
  model_list="$(docker exec "${1:-policy-agent-ollama}" ollama list 2>/dev/null || true)"
  [[ "$model_list" == *'bge-m3'* ]] && return 0
  [[ "$model_list" == *'nomic-embed-text'* ]] && return 0
  return 1
}

# ---------- 失败上下文 ----------

# dump_failure_context <compose调用前缀...> <服务列表...>
dump_failure_context() {
  local compose_prefix="$1"; shift
  local services=("$@")
  log "========== 容器状态快照 =========="
  ${compose_prefix} ps 2>/dev/null || true
  local svc
  for svc in "${services[@]}"; do
    log "---------- ${svc} 日志（最近 100 行）----------"
    ${compose_prefix} logs --tail=100 "$svc" 2>/dev/null || true
  done
}
