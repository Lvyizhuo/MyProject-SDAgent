#!/usr/bin/env bash
# ===========================================
# Windows 本地一键部署脚本（Git Bash 运行）
# 用法: ./deploy-windows.sh
# ===========================================

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

# ===========================================
# 日志与错误处理
# ===========================================

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

die() {
  log "ERROR: $*"
  dump_failure_context
  exit 1
}

dump_failure_context() {
  log "========== 容器状态快照 =========="
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps 2>/dev/null || true

  log "========== Ollama 初始化日志（最近 120 行）=========="
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=120 ollama-init 2>/dev/null || true

  log "========== 后端日志（最近 160 行）=========="
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=160 backend 2>/dev/null || true

  log "========== 前端日志（最近 80 行）=========="
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=80 frontend 2>/dev/null || true
}

# ===========================================
# 环境检测
# ===========================================

ensure_requirements() {
  # --- Docker ---
  if ! command -v docker >/dev/null 2>&1; then
    cat >&2 <<'EOF'

未检测到 Docker，请先安装 Docker Desktop for Windows：
  下载地址: https://www.docker.com/products/docker-desktop/
  安装后重启电脑，确保 Docker Desktop 正在运行，然后重新执行本脚本。

EOF
    exit 1
  fi

  # --- Docker Desktop 是否运行 ---
  if ! docker info >/dev/null 2>&1; then
    cat >&2 <<'EOF'

Docker Desktop 未运行或未就绪。请：
  1. 启动 Docker Desktop（开始菜单搜索 Docker Desktop）
  2. 等待左下角状态变为 "Engine running"
  3. 重新执行本脚本

  如果 Docker Desktop 一直无法启动，请确认：
  - 已启用 WSL2 或 Hyper-V（Docker Desktop 安装时会提示）
  - BIOS 中已启用虚拟化（Intel VT-x / AMD-V）

EOF
    exit 1
  fi

  # --- Docker Compose ---
  if ! docker compose version >/dev/null 2>&1; then
    echo "未检测到 docker compose 插件，Docker Desktop 应自带，请更新 Docker Desktop。" >&2
    exit 1
  fi

  # --- curl ---
  if ! command -v curl >/dev/null 2>&1; then
    echo "未检测到 curl，Git Bash 自带 curl，请确认你正在使用 Git Bash 运行此脚本。" >&2
    exit 1
  fi

  # --- openssl（用于生成随机密钥）---
  if ! command -v openssl >/dev/null 2>&1; then
    log "警告: 未检测到 openssl，将使用备用随机密钥生成方式。"
  fi
}

# ===========================================
# 端口冲突检测
# ===========================================

check_port_available() {
  local port="$1"
  local name="$2"
  # Windows/Git Bash: netstat 检测端口占用
  if netstat -ano 2>/dev/null | grep -q ":${port} .*LISTENING"; then
    return 1
  fi
  # 兜底: 尝试连接判断
  if command -v curl >/dev/null 2>&1; then
    if curl -fsS --max-time 1 "http://127.0.0.1:${port}" >/dev/null 2>&1; then
      return 1
    fi
  fi
  return 0
}

check_ports() {
  local ports=(
    "5432:PostgreSQL"
    "6379:Redis"
    "9000:MinIO API"
    "9001:MinIO Console"
    "11434:Ollama"
    "8080:后端 API"
    "5173:前端"
  )

  local conflicts=()
  local entry port name
  for entry in "${ports[@]}"; do
    port="${entry%%:*}"
    name="${entry#*:}"
    if ! check_port_available "$port" "$name"; then
      conflicts+=("$port ($name)")
    fi
  done

  if (( ${#conflicts[@]} > 0 )); then
    printf '\n以下端口已被占用，请先释放后再部署：\n'
    local c
    for c in "${conflicts[@]}"; do
      printf '  - %s\n' "$c"
    done
    printf '\n提示: 在 PowerShell 中运行 "netstat -ano | findstr :端口号" 查找占用进程。\n\n'
    exit 1
  fi
}

# ===========================================
# .env 文件自动生成
# ===========================================

generate_random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '\n'
  else
    # 备用：利用 /dev/urandom（Git Bash 自带）
    head -c 48 /dev/urandom 2>/dev/null | base64 | tr -d '\n' || \
      echo "change-me-$(date +%s)-$RANDOM$RANDOM"
  fi
}

# 读取用户输入的通用函数
# 用法: prompt_value "提示信息" "默认值" "是否必填(y/n)"
# 结果存入 PROMPT_RESULT
PROMPT_RESULT=""
prompt_value() {
  local prompt_text="$1"
  local default_val="$2"
  local required="$3"

  while true; do
    if [[ -n "$default_val" ]]; then
      printf '  %s [%s]: ' "$prompt_text" "$default_val"
    else
      printf '  %s: ' "$prompt_text"
    fi
    read -r PROMPT_RESULT

    # 回车使用默认值
    if [[ -z "$PROMPT_RESULT" && -n "$default_val" ]]; then
      PROMPT_RESULT="$default_val"
    fi

    # 必填校验
    if [[ "$required" == "y" && -z "$PROMPT_RESULT" ]]; then
      printf '    此项为必填，请重新输入。\n'
      continue
    fi

    break
  done
}

ensure_env_file() {
  if [[ -f "$ENV_FILE" ]]; then
    log "检测到已有的 $ENV_FILE 文件，跳过配置向导。"
    log "如需重新配置，请删除 $ENV_FILE 后重新运行。"
    return 0
  fi

  printf '\n'
  log "未检测到 $ENV_FILE，即将进入配置向导。"
  log "每项均有默认值，直接回车即可使用；也可输入自定义值。"
  printf '\n'

  # =============================================
  # 1. AI 大模型配置
  # =============================================
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  一、AI 大模型配置\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  DashScope 是阿里云大模型服务，驱动对话和知识库嵌入。\n'
  printf '  获取地址: https://dashscope.console.aliyun.com/apiKey\n\n'

  prompt_value "DASHSCOPE_API_KEY（必填）" "" "y"
  local dashscope_key="$PROMPT_RESULT"

  # =============================================
  # 2. 联网搜索配置
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  二、联网搜索配置\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  Tavily 提供 AI 联网搜索能力，让助手能查实时价格/新闻/政策。\n'
  printf '  不配置则无法使用联网搜索功能，但不影响其他功能。\n'
  printf '  获取地址: https://tavily.com/\n\n'

  prompt_value "TAVILY_API_KEY（可留空跳过）" "" "n"
  local tavily_key="$PROMPT_RESULT"

  # =============================================
  # 3. 数据库密码
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  三、数据库配置\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  PostgreSQL 存储业务数据和向量索引，Redis 存储会话缓存。\n'
  printf '  密码已自动生成强随机值，如无特殊需求直接回车即可。\n\n'

  local pg_password_default
  pg_password_default="$(generate_random_secret)"
  prompt_value "POSTGRES_PASSWORD（PostgreSQL 密码）" "$pg_password_default" "y"
  local pg_password="$PROMPT_RESULT"

  # =============================================
  # 4. MinIO 对象存储配置
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  四、MinIO 对象存储配置\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  MinIO 存储知识库文档（PDF/Word/图片等）。\n'
  printf '  安装后可访问 http://localhost:9001 管理控制台。\n\n'

  prompt_value "MINIO_USER（MinIO 管理员用户名）" "minioadmin" "y"
  local minio_user="$PROMPT_RESULT"

  local minio_password_default
  minio_password_default="$(generate_random_secret)"
  prompt_value "MINIO_PASSWORD（MinIO 管理员密码）" "$minio_password_default" "y"
  local minio_password="$PROMPT_RESULT"

  # =============================================
  # 5. JWT 安全密钥
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  五、JWT 安全密钥\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  JWT 密钥用于签发用户登录令牌，请使用强随机值。\n\n'

  local jwt_secret_default
  jwt_secret_default="$(generate_random_secret)"
  prompt_value "APP_JWT_SECRET（JWT 签名密钥）" "$jwt_secret_default" "y"
  local jwt_secret="$PROMPT_RESULT"

  # =============================================
  # 6. 模型管理加密密钥
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  六、模型管理加密密钥\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  管理员在控制台添加第三方模型时，API Key 会被加密存储。\n'
  printf '  不配置时将回退使用 JWT 密钥，但建议独立设置。\n\n'

  local model_encrypt_default
  model_encrypt_default="$(generate_random_secret)"
  prompt_value "APP_MODEL_PROVIDER_ENCRYPTION_SECRET（推荐配置，回车自动生成）" "$model_encrypt_default" "n"
  local model_encrypt_secret="$PROMPT_RESULT"

  prompt_value "APP_MODEL_PROVIDER_LEGACY_ENCRYPTION_SECRETS（历史密钥兼容，可留空）" "" "n"
  local model_legacy_secrets="$PROMPT_RESULT"

  # =============================================
  # 7. CORS 跨域配置
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  七、CORS 跨域配置\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  允许前端跨域访问后端 API 的域名白名单。\n'
  printf '  本地部署已自动包含 localhost，如有自定义域名可追加（逗号分隔）。\n\n'

  local cors_default="http://localhost:5173,http://127.0.0.1:5173"
  prompt_value "APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS" "$cors_default" "y"
  local cors_patterns="$PROMPT_RESULT"

  # =============================================
  # 8. Ollama 嵌入服务配置
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  八、Ollama 嵌入服务配置\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  Ollama 运行 nomic-embed-text 嵌入模型，用于知识库语义检索。\n'
  printf '  Docker Compose 会自动启动 Ollama 容器，此地址为容器内网络。\n'
  printf '  除非你使用外部 Ollama 服务，否则无需修改。\n\n'

  prompt_value "APP_EMBEDDING_OLLAMA_BASE_URL" "http://ollama:11434" "y"
  local ollama_base_url="$PROMPT_RESULT"

  # =============================================
  # 9. 时区
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  九、时区配置\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n'

  prompt_value "TZ（容器时区）" "Asia/Shanghai" "y"
  local tz="$PROMPT_RESULT"

  # =============================================
  # 生成并确认
  # =============================================
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '  配置汇总 — 即将写入 %s\n' "$ENV_FILE"
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n'

  # 隐藏 API Key 中间部分
  mask_secret() {
    local s="$1"
    if (( ${#s} > 8 )); then
      printf '%s****%s' "${s:0:4}" "${s: -4}"
    else
      printf '****'
    fi
  }

  printf '  DASHSCOPE_API_KEY          = %s\n' "$(mask_secret "$dashscope_key")"
  printf '  TAVILY_API_KEY             = %s\n' "$(if [[ -n "$tavily_key" ]]; then mask_secret "$tavily_key"; else echo "(未配置)"; fi)"
  printf '  POSTGRES_PASSWORD          = %s\n' "$(mask_secret "$pg_password")"
  printf '  MINIO_USER                 = %s\n' "$minio_user"
  printf '  MINIO_PASSWORD             = %s\n' "$(mask_secret "$minio_password")"
  printf '  APP_JWT_SECRET             = %s\n' "$(mask_secret "$jwt_secret")"
  printf '  APP_MODEL_PROVIDER_ENCRYPTION_SECRET = %s\n' "$(if [[ -n "$model_encrypt_secret" ]]; then mask_secret "$model_encrypt_secret"; else echo "(未配置)"; fi)"
  printf '  APP_MODEL_PROVIDER_LEGACY_ENCRYPTION_SECRETS = %s\n' "$(if [[ -n "$model_legacy_secrets" ]]; then mask_secret "$model_legacy_secrets"; else echo "(未配置)"; fi)"
  printf '  CORS_PATTERNS              = %s\n' "$cors_patterns"
  printf '  OLLAMA_BASE_URL            = %s\n' "$ollama_base_url"
  printf '  TZ                         = %s\n' "$tz"
  printf '\n'

  local confirm
  while true; do
    printf '  确认写入？(Y/n): '
    read -r confirm
    case "$confirm" in
      ""|Y|y|YES|yes)
        break
        ;;
      N|n|NO|no)
        printf '\n  已取消。请重新运行脚本以重新配置。\n\n'
        exit 0
        ;;
      *)
        printf '  请输入 Y 或 N。\n'
        ;;
    esac
  done

  # --- 写入 .env ---
  cat > "$ENV_FILE" <<EOF
# ===========================================
# 本地部署环境变量（由 deploy-windows.sh 配置向导生成）
# ===========================================

# 必填：阿里云 DashScope Key（驱动对话和知识库嵌入）
DASHSCOPE_API_KEY=${dashscope_key}

# 可选：联网搜索（Tavily，不配置则无法使用联网搜索）
TAVILY_API_KEY=${tavily_key}

# PostgreSQL 数据库密码
POSTGRES_PASSWORD=${pg_password}

# MinIO 对象存储
MINIO_USER=${minio_user}
MINIO_PASSWORD=${minio_password}

# JWT 签名密钥
APP_JWT_SECRET=${jwt_secret}

# 模型管理 API Key 加密主密钥
APP_MODEL_PROVIDER_ENCRYPTION_SECRET=${model_encrypt_secret}

# 历史加密密钥兼容列表（逗号分隔，用于平滑轮换）
APP_MODEL_PROVIDER_LEGACY_ENCRYPTION_SECRETS=${model_legacy_secrets}

# CORS 跨域白名单（逗号分隔）
APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS=${cors_patterns}

# Ollama 嵌入服务地址（容器内网络，除非使用外部 Ollama 否则无需修改）
APP_EMBEDDING_OLLAMA_BASE_URL=${ollama_base_url}

# 容器时区
TZ=${tz}
EOF

  printf '\n'
  log "%s 已写入成功。" "$ENV_FILE"
}

# ===========================================
# 校验 .env 中必填变量
# ===========================================

validate_env() {
  [[ -f "$ENV_FILE" ]] || die "未找到 $ENV_FILE"

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
    printf '请编辑 %s 填写后再重新运行。\n' "$SCRIPT_DIR/$ENV_FILE" >&2
    exit 1
  fi

  # --- 检查 CORS 是否包含 localhost ---
  if [[ -n "${APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS:-}" ]]; then
    if [[ "$APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS" != *"localhost"* && \
          "$APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS" != *"127.0.0.1"* ]]; then
      log "警告: CORS 配置中未包含 localhost/127.0.0.1，浏览器可能无法访问后端 API。"
      log "建议在 APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS 中添加 http://localhost:5173"
    fi
  fi
}

# ===========================================
# 容器状态等待
# ===========================================

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

# ===========================================
# 健康验证
# ===========================================

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

# ===========================================
# Docker 资源清理提示
# ===========================================

check_docker_resources() {
  local disk_info
  disk_info="$(docker system df --format '{{.Free}}' 2>/dev/null || true)"

  log "Docker 资源使用情况："
  docker system df 2>/dev/null || true
  printf '\n'
}

# ===========================================
# 主流程
# ===========================================

main() {
  log "============================================"
  log "  山东省智能政策咨询助手 - Windows 本地一键部署"
  log "============================================"
  printf '\n'

  # 1. 环境检测
  log "[1/7] 检测运行环境..."
  ensure_requirements
  log "Docker 和基础工具检测通过。"
  printf '\n'

  # 2. 端口冲突检测
  log "[2/7] 检测端口占用..."
  check_ports
  log "所有端口可用。"
  printf '\n'

  # 3. .env 配置
  log "[3/7] 准备环境变量配置..."
  ensure_env_file
  validate_env
  log "环境变量校验通过。"
  printf '\n'

  # 4. Docker 资源检查
  log "[4/7] 检查 Docker 磁盘空间..."
  check_docker_resources
  printf '\n'

  # 5. 校验 Compose 配置并启动
  log "[5/7] 校验 Compose 配置..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null \
    || die "docker compose 配置校验失败，请检查 $COMPOSE_FILE 和 $ENV_FILE"

  log "开始构建并启动所有容器（首次构建较慢，请耐心等待）..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build --remove-orphans
  printf '\n'

  # 6. 等待各服务就绪
  log "[6/7] 等待各服务就绪..."

  wait_for_container_status "$POSTGRES_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "PostgreSQL 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_container_status "$REDIS_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "Redis 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_container_status "$MINIO_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "MinIO 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"
  wait_for_container_status "$OLLAMA_CONTAINER" healthy "$INFRA_TIMEOUT_SEC" \
    || die "Ollama 未在 ${INFRA_TIMEOUT_SEC}s 内变为 healthy"

  log "Ollama 正在拉取嵌入模型 nomic-embed-text（首次可能需要 3~10 分钟）..."
  wait_for_ollama_init_success \
    || die "Ollama 模型初始化失败，请检查 ollama-init 日志"

  log "等待后端服务启动（含 Maven 构建 + 知识库迁移，首次可能需要 5~15 分钟）..."
  wait_for_container_status "$BACKEND_CONTAINER" healthy "$BACKEND_TIMEOUT_SEC" \
    || die "后端未在 ${BACKEND_TIMEOUT_SEC}s 内变为 healthy"

  wait_for_container_status "$FRONTEND_CONTAINER" healthy "$FRONTEND_TIMEOUT_SEC" \
    || die "前端未在 ${FRONTEND_TIMEOUT_SEC}s 内变为 healthy"
  printf '\n'

  # 7. 功能验证
  log "[7/7] 功能验证..."

  verify_ollama_model || die "未检测到 nomic-embed-text:latest 已拉取完成"
  wait_for_http_200 "http://127.0.0.1:8080/actuator/health" "$BACKEND_TIMEOUT_SEC" \
    || die "后端健康接口未在 ${BACKEND_TIMEOUT_SEC}s 内可访问"
  verify_backend_health || die "后端健康接口未报告 status=UP 或 knowledgeMigration=UP"
  wait_for_http_200 "http://127.0.0.1:5173/health" "$FRONTEND_TIMEOUT_SEC" \
    || die "前端健康接口未在 ${FRONTEND_TIMEOUT_SEC}s 内可访问"
  printf '\n'

  # ===========================================
  # 部署成功
  # ===========================================
  log "============================================"
  log "  一键部署完成！"
  log "============================================"
  printf '\n'
  log "访问地址:"
  log "  前端页面:  http://localhost:5173"
  log "  后端健康:  http://localhost:8080/actuator/health"
  log "  MinIO 控制台: http://localhost:9001"
  printf '\n'
  log "常用运维命令（在 deploy/ 目录下执行）:"
  log "  查看日志:      docker compose logs -f backend"
  log "  查看所有容器:  docker compose ps"
  log "  停止（保留数据）: docker compose down"
  log "  停止（清除数据）: docker compose down -v"
  log "  重新部署:      ./deploy-windows.sh"
  printf '\n'
  log "提示: 如需打开浏览器，直接在地址栏输入 http://localhost:5173"
}

main "$@"
