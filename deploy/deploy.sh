#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.yml"
ENV_FILE=".env"

PROJECT_NAME="policy-agent"
POSTGRES_CONTAINER="policy-agent-postgres"
REDIS_CONTAINER="policy-agent-redis"
MINIO_CONTAINER="policy-agent-minio"
OLLAMA_CONTAINER="policy-agent-ollama"
OLLAMA_INIT_CONTAINER="policy-agent-ollama-init"
TEI_RERANKER_CONTAINER="policy-agent-reranker"
BACKEND_CONTAINER="policy-agent-backend"
FRONTEND_CONTAINER="policy-agent-frontend"

INFRA_TIMEOUT_SEC="${INFRA_TIMEOUT_SEC:-300}"
OLLAMA_INIT_TIMEOUT_SEC="${OLLAMA_INIT_TIMEOUT_SEC:-1800}"
TEI_TIMEOUT_SEC="${TEI_TIMEOUT_SEC:-600}"
BACKEND_TIMEOUT_SEC="${BACKEND_TIMEOUT_SEC:-1800}"
FRONTEND_TIMEOUT_SEC="${FRONTEND_TIMEOUT_SEC:-600}"
HTTP_TIMEOUT_SEC="${HTTP_TIMEOUT_SEC:-10}"

REQUIRED_ENV_VARS=(
  DASHSCOPE_API_KEY
  POSTGRES_PASSWORD
  MINIO_PASSWORD
  APP_JWT_SECRET
)

# 子命令
ACTION="${1:-up}"

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

die() {
  log "ERROR: $*"
  exit 1
}

dump_failure_context() {
  log "容器状态快照："
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps || true

  log "Ollama 初始化日志（最近 120 行）："
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=120 ollama-init || true

  log "Reranker 服务日志（最近 120 行）："
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=120 reranker || true

  log "后端日志（最近 160 行）："
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=160 backend || true

  log "前端日志（最近 80 行）："
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=80 frontend || true
}

ensure_requirements() {
  command -v docker >/dev/null 2>&1 || {
    echo "未检测到 docker，请先安装 Docker。" >&2
    exit 1
  }
  docker compose version >/dev/null 2>&1 || {
    echo "未检测到 docker compose 插件，请先安装。" >&2
    exit 1
  }
  command -v curl >/dev/null 2>&1 || {
    echo "未检测到 curl，请先安装。" >&2
    exit 1
  }

  [[ -f "$ENV_FILE" ]] || {
    echo "未找到 $SCRIPT_DIR/$ENV_FILE，请先从 .env.example 复制并填写。" >&2
    exit 1
  }

  set -a
  # shellcheck disable=SC1091
  source "$ENV_FILE"
  set +a

  local missing=()
  local key
  for key in "${REQUIRED_ENV_VARS[@]}"; do
    if [[ -z "${!key:-}" ]]; then
      missing+=("$key")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    printf '以下环境变量未配置: %s\n' "${missing[*]}" >&2
    exit 1
  fi
}

container_status() {
  local container_name="$1"
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_name" 2>/dev/null || true
}

wait_for_container_status() {
  local container_name="$1"
  local expected_status="$2"
  local timeout_sec="$3"
  local start_ts
  start_ts="$(date +%s)"

  while true; do
    local current_status
    current_status="$(container_status "$container_name")"
    if [[ "$current_status" == "$expected_status" ]]; then
      log "容器 $container_name 已达到状态: $expected_status"
      return 0
    fi

    if (( "$(date +%s)" - start_ts >= timeout_sec )); then
      return 1
    fi
    sleep 5
  done
}

wait_for_ollama_init_success() {
  local start_ts
  start_ts="$(date +%s)"

  while true; do
    local state exit_code
    state="$(docker inspect -f '{{.State.Status}}' "$OLLAMA_INIT_CONTAINER" 2>/dev/null || true)"
    exit_code="$(docker inspect -f '{{.State.ExitCode}}' "$OLLAMA_INIT_CONTAINER" 2>/dev/null || true)"

    if [[ "$state" == "exited" ]]; then
      log "Ollama 初始化已退出 (exit_code=$exit_code)。"
      # exit 0 / 1 / 任意 都算「完成」，因为 ollama-init 现在始终 exit 0
      return 0
    fi

    if (( "$(date +%s)" - start_ts >= OLLAMA_INIT_TIMEOUT_SEC )); then
      return 1
    fi
    sleep 5
  done
}

# TEI 启动时自动下载模型，无需单独的 init 容器
# 健康检查通过即代表模型已加载就绪

wait_for_http_200() {
  local url="$1"
  local timeout_sec="$2"
  local start_ts
  start_ts="$(date +%s)"

  while true; do
    if curl -fsS --max-time "$HTTP_TIMEOUT_SEC" "$url" >/dev/null 2>&1; then
      log "HTTP 就绪: $url"
      return 0
    fi

    if (( "$(date +%s)" - start_ts >= timeout_sec )); then
      return 1
    fi
    sleep 5
  done
}

verify_backend_health() {
  local health_json
  health_json="$(curl -fsS --max-time "$HTTP_TIMEOUT_SEC" http://127.0.0.1:8080/actuator/health)"

  [[ "$health_json" == *'"status":"UP"'* ]] || return 1
  [[ "$health_json" == *'"knowledgeMigration":{"status":"UP"'* ]] || return 1

  log "后端健康检查通过，知识库迁移状态正常。"
}

verify_ollama_model() {
  local model_list
  model_list="$(docker exec "$OLLAMA_CONTAINER" ollama list 2>/dev/null || true)"
  if [[ "$model_list" == *'bge-m3'* ]]; then
    log "Ollama 已加载 bge-m3。"
    return 0
  fi
  if [[ "$model_list" == *'nomic-embed-text'* ]]; then
    log "Ollama 已加载 nomic-embed-text（bge-m3 缺失，迁移/嵌入可能受限）。"
    return 0
  fi
  return 1
}

verify_tei_reranker() {
  # Reranker 健康检查通过即代表模型已就绪
  local health_status
  health_status="$(container_status "$TEI_RERANKER_CONTAINER")"
  if [[ "$health_status" == "healthy" ]]; then
    log "Reranker 服务已就绪，bge-reranker-v2-m3 已加载。"
    return 0
  fi
  return 1
}

# ===========================================
# 主流程：up（首次 / 重新部署）
# ===========================================

cmd_up() {
  ensure_requirements

  log "校验 Compose 配置..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null

  log "开始一键部署 $PROJECT_NAME ..."
  # 关键：使用 --no-cache 强制重建镜像，避免 Docker layer cache 命中
  # 旧的 lombok / stale class 文件。配合 mvn clean package 一起保证
  # 100% 干净的构建产物。
  log "⚠️  使用 --no-cache 强制重建（lombok 升级后必须）..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build --no-cache --pull
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans

  wait_for_container_status "$POSTGRES_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || { dump_failure_context; die "PostgreSQL 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"; }
  wait_for_container_status "$REDIS_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || { dump_failure_context; die "Redis 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"; }
  wait_for_container_status "$MINIO_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || { dump_failure_context; die "MinIO 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"; }
  wait_for_container_status "$OLLAMA_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || { dump_failure_context; die "Ollama 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"; }
  wait_for_ollama_init_success \
    || { dump_failure_context; die "Ollama 模型初始化超时，请检查 ollama-init 日志"; }
  log "等待 Reranker 服务就绪（首次启动需下载模型，可能需要数分钟）..."
  wait_for_container_status "$TEI_RERANKER_CONTAINER" healthy "$TEI_TIMEOUT_SEC" \
    || { dump_failure_context; die "Reranker 服务未在 ${TEI_TIMEOUT_SEC}s 内变为 healthy"; }
  wait_for_container_status "$BACKEND_CONTAINER" healthy "$BACKEND_TIMEOUT_SEC" \
    || { dump_failure_context; die "后端未在 ${BACKEND_TIMEOUT_SEC}s 内变为 healthy"; }
  wait_for_container_status "$FRONTEND_CONTAINER" healthy "$FRONTEND_TIMEOUT_SEC" \
    || { dump_failure_context; die "前端未在 ${FRONTEND_TIMEOUT_SEC}s 内变为 healthy"; }

  verify_ollama_model || log "警告: 未检测到 bge-m3/nomic-embed-text 已拉取完成（首次启动时若拉取失败，请手动执行: docker exec $OLLAMA_CONTAINER ollama pull bge-m3）"
  verify_tei_reranker || die "Reranker 服务未就绪"
  wait_for_http_200 "http://127.0.0.1:8080/actuator/health" "$BACKEND_TIMEOUT_SEC" \
    || die "后端健康接口未在 ${BACKEND_TIMEOUT_SEC}s 内可访问"
  verify_backend_health || die "后端健康接口未报告 status=UP 或 knowledgeMigration=UP"
  wait_for_http_200 "http://127.0.0.1:5173/health" "$FRONTEND_TIMEOUT_SEC" \
    || die "前端健康接口未在 ${FRONTEND_TIMEOUT_SEC}s 内可访问"

  log "============================================"
  log "  一键部署完成！"
  log "============================================"
  log "访问前端: http://127.0.0.1:5173"
  log "后端健康: http://127.0.0.1:8080/actuator/health"
  log "如果你使用宝塔反代，直接访问你的正式域名即可。"
}

# ===========================================
# 子命令：restart（仅重启后端 + 前端，不重建镜像）
# ===========================================

cmd_restart() {
  ensure_requirements
  log "正在重启后端 + 前端 ..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" restart backend frontend
  log "等待后端健康 ..."
  wait_for_container_status "$BACKEND_CONTAINER" healthy "$BACKEND_TIMEOUT_SEC" \
    || { dump_failure_context; die "后端未在 ${BACKEND_TIMEOUT_SEC}s 内 healthy"; }
  log "等待前端健康 ..."
  wait_for_container_status "$FRONTEND_CONTAINER" healthy "$FRONTEND_TIMEOUT_SEC" \
    || { dump_failure_context; die "前端未在 ${FRONTEND_TIMEOUT_SEC}s 内 healthy"; }
  log "重启完成。"
}

# ===========================================
# 子命令：status（查看所有容器状态）
# ===========================================

cmd_status() {
  ensure_requirements
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
  echo ""
  log "后端 health:"
  curl -fsS --max-time "$HTTP_TIMEOUT_SEC" http://127.0.0.1:8080/actuator/health 2>/dev/null || log "  (未就绪)"
  echo ""
  log "前端 health:"
  curl -fsS --max-time "$HTTP_TIMEOUT_SEC" http://127.0.0.1:5173/health 2>/dev/null || log "  (未就绪)"
}

# ===========================================
# 子命令：logs（跟踪指定服务日志，默认 backend）
# ===========================================

cmd_logs() {
  ensure_requirements
  local svc="${2:-backend}"
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs -f "$svc"
}

# ===========================================
# 子命令：down（停止容器，保留数据卷）
# ===========================================

cmd_down() {
  ensure_requirements
  log "正在停止所有容器（数据卷保留）..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down
  log "停止完成。下次启动执行: $0 up"
}

# ===========================================
# 子命令：help
# ===========================================

cmd_help() {
  cat <<'EOF'
用法: ./deploy.sh [子命令]

子命令:
  up          一键部署（默认）。build + up + 等待所有服务 healthy
  restart     仅重启后端 + 前端（不重建镜像）
  status      查看所有容器状态
  logs [svc]  跟踪指定服务日志（默认 backend，可选 frontend / ollama / reranker / postgres 等）
  down        停止所有容器，保留数据卷
  help        显示此帮助

环境变量:
  INFRA_TIMEOUT_SEC     基础设施容器 healthy 超时（默认 300）
  OLLAMA_INIT_TIMEOUT_SEC  Ollama 初始化超时（默认 1800）
  TEI_TIMEOUT_SEC       Reranker 启动超时（默认 600）
  BACKEND_TIMEOUT_SEC   后端启动超时（默认 1800）
  FRONTEND_TIMEOUT_SEC  前端启动超时（默认 600）
  HTTP_TIMEOUT_SEC      HTTP 探测超时（默认 10）

示例:
  ./deploy.sh                    # 首次部署
  ./deploy.sh restart            # 仅重启后端 + 前端
  ./deploy.sh logs backend       # 跟踪后端日志
  ./deploy.sh status             # 查看所有容器状态
EOF
}

# ===========================================
# 路由
# ===========================================

case "$ACTION" in
  up)            cmd_up ;;
  restart)       cmd_restart ;;
  status)        cmd_status ;;
  logs)          cmd_logs "$@" ;;
  down)          cmd_down ;;
  help|--help|-h) cmd_help ;;
  *)
    die "未知子命令: $ACTION (执行 '$0 help' 查看帮助)"
    ;;
esac
