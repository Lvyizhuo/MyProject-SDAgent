#!/usr/bin/env bash
# ===========================================
# 【方式二】本地开发启动
#
# 拓扑：中间件跑在 Docker 容器，前后端跑在宿主机（热重载）
#   - 中间件：postgres / redis / minio / ollama / ollama-init / reranker
#   - 后端：  ./mvnw spring-boot:run（Spring Boot DevTools 热重载）
#   - 前端：  pnpm dev（Vite HMR）
#
# 前后端可独立启停：
#   ./scripts/dev.sh start            启动全部（中间件 + 前后端）
#   ./scripts/dev.sh start backend    仅启动后端
#   ./scripts/dev.sh start frontend   仅启动前端
#   ./scripts/dev.sh stop backend     仅停止后端
#   ./scripts/dev.sh stop frontend    仅停止前端
#   ./scripts/dev.sh stop             停止全部
#   ./scripts/dev.sh infra            仅启动中间件
#   ./scripts/dev.sh status           查看状态
#   ./scripts/dev.sh logs backend     查看后端日志
#
# 与【方式一】完全隔离：
#   - Compose 项目名 policy-agent-dev（容器前缀 policy-agent-dev-）
#   - 配置文件 backend/.env.local（方式一用 deploy/.env）
#   - 中间件端口映射使用 dev 专用偏移，不与方式一抢占
# ===========================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"
# shellcheck source=lib/env.sh
source "$SCRIPT_DIR/lib/env.sh"
# shellcheck source=lib/compose.sh
source "$SCRIPT_DIR/lib/compose.sh"

BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
LOG_DIR="$PROJECT_ROOT/logs"

COMPOSE_FILE="$PROJECT_ROOT/backend/docker-compose.yml"
ENV_FILE="$BACKEND_DIR/.env.local"
PROJECT_NAME="policy-agent-dev"

BACKEND_PORT="${DEPLOY_BACKEND_PORT:-8080}"
FRONTEND_PORT="${DEPLOY_FRONTEND_PORT:-5173}"

INFRA_TIMEOUT_SEC="${DEPLOY_INFRA_TIMEOUT_SEC:-300}"
OLLAMA_INIT_TIMEOUT_SEC="${DEPLOY_OLLAMA_INIT_TIMEOUT_SEC:-1800}"
RERANKER_TIMEOUT_SEC="${DEPLOY_RERANKER_TIMEOUT_SEC:-900}"
BACKEND_TIMEOUT_SEC="${DEPLOY_BACKEND_TIMEOUT_SEC:-180}"
HTTP_TIMEOUT_SEC="${DEPLOY_HTTP_TIMEOUT_SEC:-10}"

# 进程 PID 文件（放在 logs/ 下，不污染源码目录）
PID_DIR="$LOG_DIR/.pids"
BACKEND_PID_FILE="$PID_DIR/backend.pid"
FRONTEND_PID_FILE="$PID_DIR/frontend.pid"

ACTION="${1:-start}"

# ---------- 依赖检测 ----------

ensure_tools() {
  require_cmd docker "请先安装 Docker Desktop / Docker Engine"
  require_cmd curl "curl 用于健康检查"

  if ! docker compose version >/dev/null 2>&1; then
    die "缺少 Docker Compose 插件"
  fi

  # 后端需要 JDK 21
  if ! has_cmd java; then
    die "未找到 java，请安装 JDK 21（推荐用 SDKMAN：sdk install java 21.0.13.0-tem）"
  fi
  local java_version
  java_version="$(java -version 2>&1 | head -1)"
  if [[ "$java_version" != *'21'* ]]; then
    log_warn "当前 Java 为 $java_version，项目要求 JDK 21"
  fi

  # 前端需要 pnpm
  if ! has_cmd pnpm; then
    if has_cmd corepack; then
      log "启用 corepack 安装 pnpm ..."
      corepack enable || die "pnpm 安装失败，可手动执行: npm i -g pnpm"
    else
      die "未找到 pnpm，请安装：npm i -g pnpm"
    fi
  fi
}

# ---------- 中间件 ----------

cmd_infra() {
  log_step "启动中间件容器"
  ensure_tools
  [[ -f "$ENV_FILE" ]] || die "未找到 $ENV_FILE，请先创建（可从 deploy/.env.example 复制并改成本地地址）"

  env_load "$ENV_FILE" "$COMPOSE_FILE" || die "环境变量加载失败"

  log_step "拉取 / 构建中间件镜像"
  compose_cmd up "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" -d --remove-orphans \
    postgres redis minio ollama ollama-init reranker

  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" postgres)" "$INFRA_TIMEOUT_SEC" "PostgreSQL" || die "PostgreSQL 未就绪"
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" redis)" "$INFRA_TIMEOUT_SEC" "Redis" || die "Redis 未就绪"
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" minio)" "$INFRA_TIMEOUT_SEC" "MinIO" || die "MinIO 未就绪"
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" ollama)" "$INFRA_TIMEOUT_SEC" "Ollama" || die "Ollama 未就绪"
  wait_container_exited "$(compose_service_container "$PROJECT_NAME" ollama-init)" "$OLLAMA_INIT_TIMEOUT_SEC" "Ollama 模型初始化" \
    || log_warn "Ollama 模型初始化超时，可手动执行: docker compose -f $COMPOSE_FILE exec ollama ollama pull bge-m3"

  # 校验嵌入模型是否真的就位。
  # ollama-init 容器的 entrypoint 用了 `|| true`，拉取失败也返回 exit 0，
  # 因此"容器退出"不等于"模型到位"。缺模型时 RAG 会整体 404，必须显式提示。
  local ollama_container
  ollama_container="$(compose_service_container "$PROJECT_NAME" ollama)"
  local model_list
  model_list="$(docker exec "$ollama_container" ollama list 2>/dev/null || true)"
  if [[ "$model_list" == *'bge-m3'* ]]; then
    log "嵌入模型 bge-m3 已就绪"
  elif [[ "$model_list" == *'nomic-embed-text'* ]]; then
    log_warn "bge-m3 缺失（仅拉取到 nomic-embed-text），RAG 嵌入将降级或失败"
    log_warn "请手动补拉: docker compose --project-name $PROJECT_NAME --env-file $ENV_FILE -f $COMPOSE_FILE exec ollama ollama pull bge-m3"
  else
    log_warn "未检测到任何嵌入模型，RAG 检索将不可用"
    log_warn "请手动拉取: docker compose --project-name $PROJECT_NAME --env-file $ENV_FILE -f $COMPOSE_FILE exec ollama ollama pull bge-m3"
  fi
  if ! wait_container_healthy "$(compose_service_container "$PROJECT_NAME" reranker)" "$RERANKER_TIMEOUT_SEC" "Reranker"; then
    # 不直接干等/静默降级：把最常见的成因和排查命令直接打出来
    local rc
    rc="$(compose_service_container "$PROJECT_NAME" reranker)"
    log_error "Reranker 未在 ${RERANKER_TIMEOUT_SEC}s 内就绪（检索会降级为纯向量，质量下降但不影响启动）"
    log_error "  最常见原因：bge-reranker-v2-m3 模型（568MB）尚未下载完成，国内网络直连 huggingface.co 较慢"
    log_error "  查看下载进度：docker logs ${rc} 2>&1 | tail -20"
    log_error "  查看缓存大小  ：docker exec ${rc} du -sh /root/.cache/huggingface"
    log_error "  手动预热      ：docker exec ${rc} python -c \"import os; os.environ.pop('HF_HUB_OFFLINE',None); from sentence_transformers import CrossEncoder; CrossEncoder('BAAI/bge-reranker-v2-m3')\""
  fi

  log "中间件已就绪"
}

cmd_infra_stop() {
  ensure_tools
  [[ -f "$ENV_FILE" ]] && env_load "$ENV_FILE" "$COMPOSE_FILE"
  log_step "停止中间件容器"
  compose_cmd down "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE"
  log "中间件已停止"
}

# ---------- 进程管理 ----------
#
# PID 文件里记录两行：第 1 行 PID，第 2 行 PGID（进程组 id）。
# 记录 PGID 是为了能整组终止（Maven 会派生子进程、Vite 也会），
# 只杀 PID 会留下孤儿进程占用端口。
read_pid() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 1
  local pid
  pid="$(head -1 "$pid_file" 2>/dev/null || true)"
  [[ -n "$pid" ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  echo "$pid"
}

read_pgid() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 1
  local pgid
  pgid="$(sed -n '2p' "$pid_file" 2>/dev/null || true)"
  [[ -n "$pgid" ]] || return 1
  echo "$pgid"
}

# spawn_detached <pid文件> <日志文件> <命令...>
# 在独立进程组中后台启动命令，输出重定向到日志文件。
# 把真实 PID 与 PGID 写入 pid 文件（每行一个）。
#
# 说明：macOS 自带的 setsid 会立即 fork 并退出，$! 拿到的 PID 立刻失效；
# 且 BSD ps 的 pgid 字段在受限环境下不可靠。因此这里用 Python 的 os.setsid +
# os.getpgrp()，由 Python 自己把 PID/PGID 落盘，不依赖 ps。
spawn_detached() {
  local pid_file="$1"; shift
  local log_file="$1"; shift

  if has_cmd python3; then
    python3 - "$pid_file" "$log_file" "$@" <<'PY' &
import os, sys
pid_file, log_file = sys.argv[1], sys.argv[2]
cmd = sys.argv[3:]

fd = os.open(log_file, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o644)
os.dup2(fd, 1)
os.dup2(fd, 2)
devnull = os.open(os.devnull, os.O_RDONLY)
os.dup2(devnull, 0)

os.setsid()
with open(pid_file, 'w') as f:
    f.write(f"{os.getpid()}\n{os.getpgrp()}\n")

os.execvp(cmd[0], cmd)
PY
  else
    # 兜底：无 python3 时用 nohup（不独立进程组，stop 时按 PID 清理）
    nohup "$@" > "$log_file" 2>&1 &
    local pid=$!
    printf '%s\n%s\n' "$pid" "$pid" > "$pid_file"
  fi
}

# stop_process <名称> <pid文件>
stop_process() {
  local name="$1" pid_file="$2"
  local pid
  if ! pid="$(read_pid "$pid_file")"; then
    log "$name 未在运行"
    rm -f "$pid_file"
    return 0
  fi

  log "停止 $name (PID: $pid) ..."

  # 优先整组终止，确保 Maven/Vite 派生的子进程一并退出。
  # PGID 由 spawn_detached 里的 os.setsid() 保证独立于本脚本所在进程组，
  # 因此不会把脚本自身一起杀掉。
  local pgid
  pgid="$(read_pgid "$pid_file" || true)"
  if [[ -n "$pgid" ]]; then
    kill -TERM "-$pgid" 2>/dev/null || true
  fi
  kill -TERM "$pid" 2>/dev/null || true

  # 等待退出（最多 10s），超时则强杀
  local i
  for i in $(seq 1 10); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    log_warn "$name 未在 10s 内退出，强制终止"
    if [[ -n "$pgid" ]]; then
      kill -KILL "-$pgid" 2>/dev/null || true
    fi
    kill -KILL "$pid" 2>/dev/null || true
  fi

  rm -f "$pid_file"
  log "$name 已停止"
}

# ---------- 后端 ----------

# ensure_infra_ready —— 确保中间件容器在跑
# 单独执行 `dev.sh start backend` 时，中间件可能还没起，
# 此时后端会在 Flyway 连库时抛一长串 BeanCreationException，
# 用户根本看不出"其实是数据库没启动"。这里主动检查并自动补起。
ensure_infra_ready() {
  local svc container missing=()

  for svc in postgres redis minio ollama reranker; do
    container="$(compose_service_container "$PROJECT_NAME" "$svc")"
    local state
    state="$(container_status "$container" 2>/dev/null || echo "missing")"
    if [[ "$state" != "running" && "$state" != "healthy" ]]; then
      missing+=("$svc")
    fi
  done

  if (( ${#missing[@]} == 0 )); then
    return 0
  fi

  log "中间件未就绪（${missing[*]}），自动启动 ..."
  cmd_infra
}

start_backend() {
  mkdir -p "$LOG_DIR" "$PID_DIR"

  if read_pid "$BACKEND_PID_FILE" >/dev/null; then
    log "后端已在运行 (PID: $(read_pid "$BACKEND_PID_FILE"))"
    return 0
  fi

  # 中间件没起就先补起，否则后端会在连库时抛一堆难以定位的 Bean 异常
  ensure_infra_ready

  # 清理超过保留期的历史日志（默认 30 天，与容器 entrypoint 同一套规则）
  purge_expired_logs "$LOG_DIR" "${LOG_RETENTION_DAYS:-30}" "*.log"

  # 端口预检：本地开发默认用 8080 / 5173，与【一键部署实例】的宿主机端口完全相同。
  # 若部署实例正在跑，Spring Boot 不会报错，而是静默改用随机端口，
  # 结果是"启动成功但访问 8080 拿不到响应"，非常难排查 —— 这里提前拦下来。
  if port_in_use "$BACKEND_PORT"; then
    log_error "后端端口 ${BACKEND_PORT} 已被占用，无法启动本地后端"
    local owner
    owner="$(port_owner "$BACKEND_PORT")"
    [[ -n "$owner" ]] && log_error "  占用进程：${owner}"
    log_error "  占用方多半是【一键部署实例】：可执行 ./scripts/deploy.sh status 查看"
    log_error "  方案一：停掉部署实例  → ./scripts/deploy.sh down"
    log_error "  方案二：换端口启动开发  → DEPLOY_BACKEND_PORT=18080 ./scripts/dev.sh start backend"
    die "后端端口 ${BACKEND_PORT} 被占用，已中止启动"
  fi

  log_step "启动后端（Spring Boot，profiles=dev,mcp）"
  [[ -f "$ENV_FILE" ]] || die "未找到 $ENV_FILE"

  # 加载环境变量到当前进程
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  cd "$BACKEND_DIR"
  # 端口：始终显式设置 server.port。
  #
  # 为什么必须显式：某些 IDE / 终端会向子进程注入形如 SERVER__PORT=54578 的环境变量
  # （双下划线是 IDE 自己的服务端口）。Spring Boot 的 relaxed binding 会把
  # SERVER__PORT 解析成 server.port，于是应用去绑 IDE 占用的端口 →
  # "Port 54578 was already in use" → 启动失败，且错误信息与本项目毫无关联，
  # 极难定位。这里无条件 export，覆盖任何来源的隐式设置。
  # DEPLOY_BACKEND_PORT 可用于指定其他端口。
  export SERVER_PORT="$BACKEND_PORT"
  # 日志：与一键部署同一套规范（活动文件带日期、不压缩、保留 30 天），只是路径落在 <repo>/logs。
  # LOG_DATE 决定活动文件名（backend-<日期>.log），进程重启即切换到新日期文件。
  export LOG_DATE="$(date +%Y-%m-%d)"
  export LOGGING_FILE_PATH="$LOG_DIR"
  # 独立进程组启动，便于 stop 时整组终止（Maven 会派生子进程）
  spawn_detached "$BACKEND_PID_FILE" "$LOG_DIR/backend.out" \
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,mcp
  cd "$PROJECT_ROOT"

  # 等 PID 文件落盘（Python 写入）
  local i
  for i in $(seq 1 20); do
    [[ -s "$BACKEND_PID_FILE" ]] && break
    sleep 0.5
  done
  [[ -s "$BACKEND_PID_FILE" ]] || {
    log_error "后端进程启动失败，请查看 $LOG_DIR/backend.out"
    return 1
  }

  local backend_pid
  backend_pid="$(read_pid "$BACKEND_PID_FILE" || true)"
  log "后端启动中 (PID: ${backend_pid:-unknown})，日志: logs/backend.out"

  # 等待就绪
  local i ready=false
  for i in $(seq 1 "$BACKEND_TIMEOUT_SEC"); do
    if curl -fsS --max-time 2 "http://127.0.0.1:${BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
      ready=true
      break
    fi
    if ! kill -0 "${backend_pid:-0}" 2>/dev/null; then
      log_error "后端进程已退出，最近日志："
      tail -30 "$LOG_DIR/backend.out" >&2 || true
      rm -f "$BACKEND_PID_FILE"
      die "后端启动失败"
    fi
    sleep 1
  done

  if [[ "$ready" == "true" ]]; then
    log "后端已就绪: http://127.0.0.1:${BACKEND_PORT}"
  else
    log_warn "后端在 ${BACKEND_TIMEOUT_SEC}s 内未就绪，可能仍在启动，请查看 logs/backend.out"
  fi
}

# ---------- 前端 ----------

start_frontend() {
  mkdir -p "$LOG_DIR" "$PID_DIR"

  if read_pid "$FRONTEND_PID_FILE" >/dev/null; then
    log "前端已在运行 (PID: $(read_pid "$FRONTEND_PID_FILE"))"
    return 0
  fi

  # 端口预检（与后端同理：5173 与部署实例的前端端口冲突）
  if port_in_use "$FRONTEND_PORT"; then
    log_error "前端端口 ${FRONTEND_PORT} 已被占用，无法启动本地前端"
    local owner
    owner="$(port_owner "$FRONTEND_PORT")"
    [[ -n "$owner" ]] && log_error "  占用进程：${owner}"
    log_error "  占用方多半是【一键部署实例】：可执行 ./scripts/deploy.sh status 查看"
    log_error "  方案一：停掉部署实例  → ./scripts/deploy.sh down"
    log_error "  方案二：换端口启动开发  → DEPLOY_FRONTEND_PORT=15173 ./scripts/dev.sh start frontend"
    die "前端端口 ${FRONTEND_PORT} 被占用，已中止启动"
  fi

  log_step "启动前端（Vite 开发服务器）"
  cd "$FRONTEND_DIR"

  if [[ ! -d node_modules ]]; then
    log "安装前端依赖 ..."
    pnpm install || die "前端依赖安装失败"
  fi

  # 前端端口与代理目标通过环境变量传给 Vite（vite.config.js 读取）
  export DEPLOY_FRONTEND_PORT="$FRONTEND_PORT"
  export DEPLOY_BACKEND_PORT="$BACKEND_PORT"
  spawn_detached "$FRONTEND_PID_FILE" "$LOG_DIR/frontend.log" pnpm dev
  cd "$PROJECT_ROOT"

  local i
  for i in $(seq 1 20); do
    [[ -s "$FRONTEND_PID_FILE" ]] && break
    sleep 0.5
  done
  [[ -s "$FRONTEND_PID_FILE" ]] || {
    log_error "前端进程启动失败，请查看 $LOG_DIR/frontend.log"
    return 1
  }

  local frontend_pid
  frontend_pid="$(read_pid "$FRONTEND_PID_FILE" || true)"
  log "前端启动中 (PID: ${frontend_pid:-unknown})，日志: logs/frontend.log"

  local i ready=false
  for i in $(seq 1 60); do
    if curl -fsS --max-time 2 "http://127.0.0.1:${FRONTEND_PORT}/" >/dev/null 2>&1; then
      ready=true
      break
    fi
    if ! kill -0 "${frontend_pid:-0}" 2>/dev/null; then
      log_error "前端进程已退出，最近日志："
      tail -30 "$LOG_DIR/frontend.log" >&2 || true
      rm -f "$FRONTEND_PID_FILE"
      die "前端启动失败"
    fi
    sleep 1
  done

  if [[ "$ready" == "true" ]]; then
    log "前端已就绪: http://127.0.0.1:${FRONTEND_PORT}"
  else
    log_warn "前端在 60s 内未就绪，请查看 logs/frontend.log"
  fi
}

# ---------- 组合命令 ----------

cmd_start() {
  local target="${1:-all}"
  ensure_tools

  case "$target" in
    all)
      cmd_infra
      start_backend
      start_frontend
      print_summary
      ;;
    backend)
      start_backend
      ;;
    frontend)
      start_frontend
      ;;
    *)
      die "未知目标: $target（可选 all / backend / frontend）"
      ;;
  esac
}

cmd_stop() {
  local target="${1:-all}"
  case "$target" in
    all)
      stop_process "后端" "$BACKEND_PID_FILE"
      stop_process "前端" "$FRONTEND_PID_FILE"
      cmd_infra_stop
      ;;
    backend) stop_process "后端" "$BACKEND_PID_FILE" ;;
    frontend) stop_process "前端" "$FRONTEND_PID_FILE" ;;
    *) die "未知目标: $target（可选 all / backend / frontend）" ;;
  esac
}

cmd_status() {
  ensure_tools
  echo "中间件容器（项目: ${PROJECT_NAME}）："
  [[ -f "$ENV_FILE" ]] && env_load "$ENV_FILE" "$COMPOSE_FILE"
  compose_cmd ps "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" 2>/dev/null || true
  echo ""
  log "后端: $(read_pid "$BACKEND_PID_FILE" >/dev/null && echo "运行中 (PID $(read_pid "$BACKEND_PID_FILE"))" || echo '未运行')"
  log "前端: $(read_pid "$FRONTEND_PID_FILE" >/dev/null && echo "运行中 (PID $(read_pid "$FRONTEND_PID_FILE"))" || echo '未运行')"
}

cmd_logs() {
  local target="${1:-backend}"
  case "$target" in
    backend) tail -f "$LOG_DIR/backend.out" ;;
    frontend) tail -f "$LOG_DIR/frontend.log" ;;
    *) die "未知目标: $target（可选 backend / frontend）" ;;
  esac
}

print_summary() {
  log ""
  log "============================================"
  log "  本地开发环境已启动"
  log "============================================"
  log "前端:    http://127.0.0.1:${FRONTEND_PORT}"
  log "后端:    http://127.0.0.1:${BACKEND_PORT}"
  log "健康:    curl http://127.0.0.1:${BACKEND_PORT}/actuator/health"
  log ""
  log "后端日志: tail -f logs/backend.out（原始输出） / logs/backend-$(date +%F).log（按天文件）"
  log "前端日志: tail -f logs/frontend.log"
  log ""
  log "单独重启后端: ./scripts/dev.sh stop backend && ./scripts/dev.sh start backend"
  log "单独重启前端: ./scripts/dev.sh stop frontend && ./scripts/dev.sh start frontend"
  log "停止全部:     ./scripts/dev.sh stop"
  log "============================================"
}

cmd_help() {
  cat <<'EOF'
用法: ./scripts/dev.sh [子命令] [目标]

【方式二】本地开发启动（中间件用 Docker，前后端跑宿主机热重载）

子命令:
  start [target]   启动。target: all(默认) / backend / frontend
  stop [target]    停止。target: all(默认) / backend / frontend
  infra            仅启动中间件容器
  infra-stop       仅停止中间件容器
  status           查看容器与进程状态
  logs [target]    查看日志（backend / frontend）
  help             显示此帮助

示例:
  ./scripts/dev.sh                 # 启动中间件 + 前后端
  ./scripts/dev.sh start backend    # 只启动后端（中间件需已在运行）
  ./scripts/dev.sh stop frontend   # 只停前端，后端继续跑
  ./scripts/dev.sh start frontend   # 只启动前端
  ./scripts/dev.sh infra            # 只起中间件（自己用 IDE 跑后端时）
  ./scripts/dev.sh logs backend

配置文件: backend/.env.local
日志位置: logs/backend.out（原始输出）、logs/backend-$(date +%F).log（按天文件）、logs/frontend.log

与一键部署（./scripts/deploy.sh）的隔离:
  - Compose 项目名 policy-agent-dev（容器前缀 policy-agent-dev-）
  - 配置文件 backend/.env.local（部署方式用 deploy/.env）
  - 两者可同时运行，互不覆盖
EOF
}

# ---------- 路由 ----------
#
# 注意：这里刻意不使用 `shift`。
# bash 的 `shift` 在没有位置参数时返回 1，配合 `set -e` 会让脚本**静默退出**
# （stdout/stderr 全空、退出码 1）——例如不带任何参数运行时就会命中。
# 改用 "$2" / "${@:2}" 直接取参，天然兼容"只有子命令"和"子命令+参数"两种情况。

case "$ACTION" in
  start)       cmd_start "${2:-all}" ;;
  stop)        cmd_stop "${2:-all}" ;;
  infra)       cmd_infra ;;
  infra-stop)  cmd_infra_stop ;;
  status)      cmd_status ;;
  logs)        cmd_logs "${2:-backend}" ;;
  help|--help|-h) cmd_help ;;
  *)
    log_error "未知子命令: $ACTION"
    cmd_help
    exit 1
    ;;
esac
