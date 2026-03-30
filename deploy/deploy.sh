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
BACKEND_CONTAINER="policy-agent-backend"
FRONTEND_CONTAINER="policy-agent-frontend"

INFRA_TIMEOUT_SEC="${INFRA_TIMEOUT_SEC:-300}"
OLLAMA_INIT_TIMEOUT_SEC="${OLLAMA_INIT_TIMEOUT_SEC:-1800}"
BACKEND_TIMEOUT_SEC="${BACKEND_TIMEOUT_SEC:-1800}"
FRONTEND_TIMEOUT_SEC="${FRONTEND_TIMEOUT_SEC:-600}"
HTTP_TIMEOUT_SEC="${HTTP_TIMEOUT_SEC:-10}"

REQUIRED_ENV_VARS=(
  DASHSCOPE_API_KEY
  POSTGRES_PASSWORD
  MINIO_PASSWORD
  APP_JWT_SECRET
)

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

die() {
  log "ERROR: $*"
  dump_failure_context
  exit 1
}

dump_failure_context() {
  log "容器状态快照："
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps || true

  log "Ollama 初始化日志（最近 120 行）："
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=120 ollama-init || true

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

    if [[ "$state" == "exited" && "$exit_code" == "0" ]]; then
      log "Ollama 初始化已完成，模型拉取成功。"
      return 0
    fi

    if [[ "$state" == "exited" && "$exit_code" != "0" ]]; then
      return 1
    fi

    if (( "$(date +%s)" - start_ts >= OLLAMA_INIT_TIMEOUT_SEC )); then
      return 1
    fi
    sleep 5
  done
}

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
  [[ "$model_list" == *'nomic-embed-text:latest'* ]] || return 1
  log "Ollama 已加载 nomic-embed-text:latest。"
}

main() {
  ensure_requirements

  log "校验 Compose 配置..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null

  log "开始一键部署 $PROJECT_NAME ..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build --remove-orphans

  wait_for_container_status "$POSTGRES_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "PostgreSQL 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_container_status "$REDIS_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "Redis 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_container_status "$MINIO_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "MinIO 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_container_status "$OLLAMA_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "Ollama 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_ollama_init_success \
    || die "Ollama 模型初始化失败，请检查 ollama-init 日志"
  wait_for_container_status "$BACKEND_CONTAINER" healthy "$BACKEND_TIMEOUT_SEC" \
    || die "后端未在 ${BACKEND_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_container_status "$FRONTEND_CONTAINER" healthy "$FRONTEND_TIMEOUT_SEC" \
    || die "前端未在 ${FRONTEND_TIMEOUT_SEC}s 内变为 healthy"

  verify_ollama_model || die "未检测到 nomic-embed-text:latest 已拉取完成"
  wait_for_http_200 "http://127.0.0.1:8080/actuator/health" "$BACKEND_TIMEOUT_SEC" \
    || die "后端健康接口未在 ${BACKEND_TIMEOUT_SEC}s 内可访问"
  verify_backend_health || die "后端健康接口未报告 status=UP 或 knowledgeMigration=UP"
  wait_for_http_200 "http://127.0.0.1:5173/health" "$FRONTEND_TIMEOUT_SEC" \
    || die "前端健康接口未在 ${FRONTEND_TIMEOUT_SEC}s 内可访问"

  log "一键部署完成。"
  log "访问前端: http://127.0.0.1:5173"
  log "后端健康: http://127.0.0.1:8080/actuator/health"
  log "如果你使用宝塔反代，直接访问你的正式域名即可。"
}

main "$@"
