#!/usr/bin/env bash
# ===========================================
# 【方式一】服务器 / 新机器 一键部署
#
# 全平台兼容：Linux / macOS / Windows(Git Bash)
# 在全新机器上自动完成：
#   1. 依赖检测与自动安装（Docker / Docker Compose）
#   2. 生成或校验环境配置（deploy/.env）
#   3. 构建前后端镜像
#   4. 启动全部中间件与服务容器
#   5. 等待就绪并做健康校验
#
# 用法：
#   ./scripts/deploy.sh up          一键部署（默认）
#   ./scripts/deploy.sh init        仅生成配置文件
#   ./scripts/deploy.sh status      查看状态
#   ./scripts/deploy.sh logs [svc]  跟踪日志
#   ./scripts/deploy.sh down        停止（保留数据卷）
#   ./scripts/deploy.sh help        帮助
#
# 配置来源：deploy/.env（由 deploy/.env.example 渲染）
#   也可用环境变量覆盖：DEPLOY_DASHSCOPE_API_KEY=xxx ./scripts/deploy.sh up
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

# ---------- 路径与常量 ----------

DEPLOY_DIR="$PROJECT_ROOT/deploy"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
ENV_FILE="$DEPLOY_DIR/.env"
ENV_TEMPLATE="$DEPLOY_DIR/.env.example"
PROJECT_NAME="policy-agent"

BACKEND_PORT="${DEPLOY_BACKEND_PORT:-8080}"
FRONTEND_PORT="${DEPLOY_FRONTEND_PORT:-5173}"

# 超时（可用环境变量覆盖）
INFRA_TIMEOUT_SEC="${DEPLOY_INFRA_TIMEOUT_SEC:-300}"
OLLAMA_INIT_TIMEOUT_SEC="${DEPLOY_OLLAMA_INIT_TIMEOUT_SEC:-1800}"
RERANKER_TIMEOUT_SEC="${DEPLOY_RERANKER_TIMEOUT_SEC:-900}"
BACKEND_TIMEOUT_SEC="${DEPLOY_BACKEND_TIMEOUT_SEC:-1800}"
FRONTEND_TIMEOUT_SEC="${DEPLOY_FRONTEND_TIMEOUT_SEC:-600}"
HTTP_TIMEOUT_SEC="${DEPLOY_HTTP_TIMEOUT_SEC:-10}"

# 必填变量
REQUIRED_ENV_VARS=(
  DASHSCOPE_API_KEY
  POSTGRES_PASSWORD
  MINIO_USER
  MINIO_PASSWORD
  APP_JWT_SECRET
)

ACTION="${1:-up}"

# ---------- 依赖安装 ----------

# install_docker_linux —— 在 Linux 上用官方脚本安装 Docker
install_docker_linux() {
  log "正在通过 get.docker.com 安装 Docker ..."
  if has_cmd curl; then
    curl -fsSL https://get.docker.com -o /tmp/get-docker.sh || {
      log_error "下载安装脚本失败"
      return 1
    }
    if [[ "$(id -u)" -eq 0 ]]; then
      sh /tmp/get-docker.sh || return 1
    else
      log_warn "需要 root 权限，请手动执行: sudo sh /tmp/get-docker.sh"
      return 1
    fi
  else
    log_error "需要 curl 才能自动安装 Docker"
    return 1
  fi
  log "Docker 安装完成，正在启动服务..."
  systemctl enable docker 2>/dev/null || true
  systemctl start docker 2>/dev/null || true
  return 0
}

# install_docker_macos —— macOS 用 Homebrew
install_docker_macos() {
  if ! has_cmd brew; then
    log_error "macOS 上需要 Homebrew 才能自动安装 Docker"
    log_error "请先安装 Homebrew: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
    return 1
  fi
  log "正在通过 Homebrew 安装 Docker Desktop ..."
  brew install --cask docker || return 1
  log_warn "Docker Desktop 已安装，请手动启动它（应用程序 → Docker），然后重新运行本脚本"
  return 0
}

# ensure_docker —— 检测并按需安装 Docker
ensure_docker() {
  if has_cmd docker && docker compose version >/dev/null 2>&1; then
    log "Docker 与 Docker Compose 已就绪"
    return 0
  fi

  log_warn "未检测到可用的 Docker 环境"

  # 已装 docker 但缺 compose 插件
  if has_cmd docker && ! docker compose version >/dev/null 2>&1; then
    log_warn "检测到 Docker 但缺少 compose 插件"
    if has_cmd apt-get; then
      log "尝试安装 docker-compose-plugin ..."
      apt-get update -qq && apt-get install -y docker-compose-plugin
    elif has_cmd brew; then
      log "尝试安装 docker-compose ..."
      brew install docker-compose
    fi
    docker compose version >/dev/null 2>&1 && {
      log "Docker Compose 插件安装完成"
      return 0
    }
  fi

  case "$(detect_platform)" in
    linux)
      install_docker_linux && return 0
      ;;
    macos)
      install_docker_macos && return 0
      ;;
    windows)
      log_error "Windows 上无法自动安装 Docker，请手动安装 Docker Desktop："
      log_error "  https://www.docker.com/products/docker-desktop/"
      log_error "安装后启动 Docker Desktop，确认状态为 Engine running，再重新运行本脚本"
      return 1
      ;;
    *)
      log_error "未识别的操作系统，请手动安装 Docker + Docker Compose"
      return 1
      ;;
  esac

  docker compose version >/dev/null 2>&1
}

# ---------- 配置生成 ----------

# cmd_init —— 交互式生成 deploy/.env
cmd_init() {
  log_step "生成部署配置文件"

  if [[ -f "$ENV_FILE" ]]; then
    log "已存在 $ENV_FILE，保持不变"
    log "如需重新生成，请先删除该文件"
    return 0
  fi

  if [[ ! -f "$ENV_TEMPLATE" ]]; then
    die "未找到模板文件: $ENV_TEMPLATE"
  fi

  local dashscope_key tavily_key pg_password minio_user minio_password
  local jwt_secret encrypt_secret cors_patterns

  printf '\n'
  log "配置向导（每项都有默认值，直接回车即可）"
  printf '\n'

  printf '  1) 阿里云 DashScope API Key（必填）\n'
  printf '     获取: https://dashscope.console.aliyun.com/apiKey\n'
  read -r -p "     DASHSCOPE_API_KEY: " dashscope_key
  [[ -n "$dashscope_key" ]] || die "DASHSCOPE_API_KEY 不能为空"

  printf '\n  2) Tavily 联网搜索 Key（可留空）\n'
  printf '     获取: https://tavily.com/\n'
  read -r -p "     TAVILY_API_KEY: " tavily_key

  printf '\n  3) PostgreSQL 密码（留空自动生成）\n'
  read -r -p "     POSTGRES_PASSWORD: " pg_password
  [[ -n "$pg_password" ]] || pg_password="$(random_secret 24)"

  printf '\n  4) MinIO 账号密码（留空自动生成）\n'
  read -r -p "     MINIO_USER [minioadmin]: " minio_user
  [[ -n "$minio_user" ]] || minio_user="minioadmin"
  read -r -p "     MINIO_PASSWORD: " minio_password
  [[ -n "$minio_password" ]] || minio_password="$(random_secret 24)"

  printf '\n  5) JWT 签名密钥（留空自动生成 Base64）\n'
  read -r -p "     APP_JWT_SECRET: " jwt_secret
  [[ -n "$jwt_secret" ]] || jwt_secret="$(random_secret 48)"

  printf '\n  6) 模型管理 API Key 加密密钥（留空自动生成）\n'
  read -r -p "     APP_MODEL_PROVIDER_ENCRYPTION_SECRET: " encrypt_secret
  [[ -n "$encrypt_secret" ]] || encrypt_secret="$(random_secret 48)"

  printf '\n  7) 正式域名（用于 CORS 放行与前端访问）\n'
  read -r -p "     APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS: " cors_patterns
  [[ -n "$cors_patterns" ]] || cors_patterns="http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT}"

  printf '\n'
  render_env_template "$ENV_TEMPLATE" "$ENV_FILE" \
    "DASHSCOPE_API_KEY=$dashscope_key" \
    "TAVILY_API_KEY=$tavily_key" \
    "POSTGRES_PASSWORD=$pg_password" \
    "MINIO_USER=$minio_user" \
    "MINIO_PASSWORD=$minio_password" \
    "APP_JWT_SECRET=$jwt_secret" \
    "APP_MODEL_PROVIDER_ENCRYPTION_SECRET=$encrypt_secret" \
    "APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS=$cors_patterns"

  log "配置已写入 $ENV_FILE"
  log_warn "该文件含明文密钥，请勿提交到 Git（.gitignore 已覆盖）"
}

# ensure_env —— 确保配置文件存在且必填项有效
ensure_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    log_warn "未找到 $ENV_FILE"
    cmd_init
  fi

  env_load "$ENV_FILE" "$COMPOSE_FILE" || die "环境变量文件加载失败"

  # 环境变量覆盖（DEPLOY_ 前缀的直接透传）
  [[ -n "${DEPLOY_DASHSCOPE_API_KEY:-}" ]] && export DASHSCOPE_API_KEY="$DEPLOY_DASHSCOPE_API_KEY"
  [[ -n "${DEPLOY_TAVILY_API_KEY:-}" ]] && export TAVILY_API_KEY="$DEPLOY_TAVILY_API_KEY"
  [[ -n "${DEPLOY_POSTGRES_PASSWORD:-}" ]] && export POSTGRES_PASSWORD="$DEPLOY_POSTGRES_PASSWORD"
  [[ -n "${DEPLOY_MINIO_PASSWORD:-}" ]] && export MINIO_PASSWORD="$DEPLOY_MINIO_PASSWORD"
  [[ -n "${DEPLOY_APP_JWT_SECRET:-}" ]] && export APP_JWT_SECRET="$DEPLOY_APP_JWT_SECRET"
  [[ -n "${DEPLOY_CORS_ORIGINS:-}" ]] && export APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS="$DEPLOY_CORS_ORIGINS"

  env_require_real "${REQUIRED_ENV_VARS[@]}" || die "请修正 $ENV_FILE 后重新运行（或执行 ./scripts/deploy.sh init 重新生成）"
}

# ---------- 主流程 ----------

cmd_up() {
  log_step "准备环境"
  ensure_docker
  require_cmd curl "curl 用于健康检查"
  ensure_env

  local cp
  cp="$(compose_prefix "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE")"

  log_step "校验 Compose 配置"
  compose_cmd config "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" >/dev/null \
    || die "docker compose 配置校验失败，请检查 $ENV_FILE"

  log_step "构建镜像"
  if [[ "${DEPLOY_SKIP_BUILD:-0}" == "1" ]]; then
    # 跳过构建：直接用现有镜像启动（适用于基础镜像拉不下来、或只改了环境变量时）
    log "跳过构建（DEPLOY_SKIP_BUILD=1），使用已有镜像"
  elif [[ "${DEPLOY_NO_CACHE:-1}" == "1" ]]; then
    log "使用 --no-cache 强制重建（规避 lombok 升级后的 stale class 问题）"
    compose_cmd build "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" --no-cache --pull
  else
    compose_cmd build "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" --pull
  fi

  log_step "启动服务"
  compose_cmd up "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" -d --remove-orphans

  log_step "等待服务就绪"
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" postgres)" "$INFRA_TIMEOUT_SEC" "PostgreSQL" \
    || { dump_failure_context "$cp" postgres backend; die "PostgreSQL 未在 ${INFRA_TIMEOUT_SEC}s 内就绪"; }
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" redis)" "$INFRA_TIMEOUT_SEC" "Redis" \
    || { dump_failure_context "$cp" redis backend; die "Redis 未就绪"; }
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" minio)" "$INFRA_TIMEOUT_SEC" "MinIO" \
    || { dump_failure_context "$cp" minio backend; die "MinIO 未就绪"; }
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" ollama)" "$INFRA_TIMEOUT_SEC" "Ollama" \
    || { dump_failure_context "$cp" ollama backend; die "Ollama 未就绪"; }

  wait_container_exited "$(compose_service_container "$PROJECT_NAME" ollama-init)" "$OLLAMA_INIT_TIMEOUT_SEC" "Ollama 模型初始化" \
    || log_warn "Ollama 模型初始化超时，可手动执行: docker compose -f $COMPOSE_FILE exec ollama ollama pull bge-m3"

  if ! wait_container_healthy "$(compose_service_container "$PROJECT_NAME" reranker)" "$RERANKER_TIMEOUT_SEC" "Reranker"; then
    local rc
    rc="$(compose_service_container "$PROJECT_NAME" reranker)"
    log_error "Reranker 未在 ${RERANKER_TIMEOUT_SEC}s 内就绪"
    log_error "  最常见原因：bge-reranker-v2-m3 模型（568MB）尚未下载完成，国内网络直连 huggingface.co 较慢"
    log_error "  查看下载进度：docker logs ${rc} 2>&1 | tail -20"
    log_error "  查看缓存大小  ：docker exec ${rc} du -sh /root/.cache/huggingface"
    dump_failure_context "$cp" reranker backend
    die "Reranker 未就绪（首次启动需下载模型）"
  fi

  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" backend)" "$BACKEND_TIMEOUT_SEC" "后端" \
    || { dump_failure_context "$cp" backend; die "后端未就绪"; }
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" frontend)" "$FRONTEND_TIMEOUT_SEC" "前端" \
    || { dump_failure_context "$cp" frontend; die "前端未就绪"; }

  log_step "功能校验"
  # ollama-init 的 entrypoint 用了 `|| true`，拉取失败也返回 exit 0，
  # 因此"容器退出"不等于"模型到位"，这里显式校验并在缺失时给出可执行的修复命令。
  local ollama_container model_list
  ollama_container="$(compose_service_container "$PROJECT_NAME" ollama)"
  model_list="$(docker exec "$ollama_container" ollama list 2>/dev/null || true)"
  if [[ "$model_list" == *'bge-m3'* ]]; then
    log "嵌入模型 bge-m3 已就绪"
  elif [[ "$model_list" == *'nomic-embed-text'* ]]; then
    log_warn "bge-m3 缺失（仅拉取到 nomic-embed-text），RAG 嵌入将降级或失败"
    log_warn "补拉命令: docker compose --project-name $PROJECT_NAME --env-file $ENV_FILE -f $COMPOSE_FILE exec ollama ollama pull bge-m3"
  else
    log_warn "未检测到任何嵌入模型，RAG 检索不可用"
    log_warn "补拉命令: docker compose --project-name $PROJECT_NAME --env-file $ENV_FILE -f $COMPOSE_FILE exec ollama ollama pull bge-m3"
  fi

  wait_http_200 "http://127.0.0.1:${BACKEND_PORT}/actuator/health" "$BACKEND_TIMEOUT_SEC" "后端健康接口" \
    || die "后端健康接口不可访问"
  verify_backend_health "http://127.0.0.1:${BACKEND_PORT}/actuator/health" \
    || die "后端健康检查未通过（status 非 UP）"
  verify_knowledge_migration "http://127.0.0.1:${BACKEND_PORT}/api/chat/health" \
    || log_warn "知识库健康端点未通过，请检查后端日志中的 KnowledgeMigration 相关记录"
  wait_http_200 "http://127.0.0.1:${FRONTEND_PORT}/health" "$FRONTEND_TIMEOUT_SEC" "前端健康接口" \
    || die "前端健康接口不可访问"

  log ""
  log "============================================"
  log "  部署完成"
  log "============================================"
  log "前端访问:   http://127.0.0.1:${FRONTEND_PORT}"
  log "后端健康:   http://127.0.0.1:${BACKEND_PORT}/actuator/health"
  log "管理台:     http://127.0.0.1:${FRONTEND_PORT}/admin"
  log ""
  log "查看日志:   ./scripts/deploy.sh logs backend"
  log "重启服务:   ./scripts/deploy.sh restart"
  log "停止服务:   ./scripts/deploy.sh down"
  log "============================================"
}

cmd_restart() {
  ensure_docker
  ensure_env
  log_step "重启服务"
  local services=("$@")
  if (( ${#services[@]} == 0 )); then
    services=(backend frontend)
  fi
  compose_cmd restart "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" "${services[@]}"
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" backend)" "$BACKEND_TIMEOUT_SEC" "后端" \
    || die "后端未就绪"
  wait_container_healthy "$(compose_service_container "$PROJECT_NAME" frontend)" "$FRONTEND_TIMEOUT_SEC" "前端" \
    || die "前端未就绪"
  log "重启完成"
}

cmd_status() {
  ensure_docker
  [[ -f "$ENV_FILE" ]] && env_load "$ENV_FILE" "$COMPOSE_FILE"
  compose_cmd ps "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE"
  echo ""
  log "后端健康:"
  curl -fsS --max-time "$HTTP_TIMEOUT_SEC" "http://127.0.0.1:${BACKEND_PORT}/actuator/health" 2>/dev/null || log "  (未就绪)"
  echo ""
  log "前端健康:"
  curl -fsS --max-time "$HTTP_TIMEOUT_SEC" "http://127.0.0.1:${FRONTEND_PORT}/health" 2>/dev/null || log "  (未就绪)"
}

cmd_logs() {
  ensure_docker
  [[ -f "$ENV_FILE" ]] && env_load "$ENV_FILE" "$COMPOSE_FILE"
  local svc="${1:-backend}"
  if [[ "$svc" == "all" ]]; then
    # 全量：所有容器的 stdout（Docker json-file）
    compose_cmd logs "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" -f
    return 0
  fi
  compose_cmd logs "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE" -f "$svc"
}

# 日志文件位于各容器内部（按天归档），这里统一给出查看方式
cmd_logfiles() {
  ensure_docker
  log_step "容器内日志文件（<组件>-<日期>.log，按天一个文件，不压缩，保留 30 天）"
  local today
  today="$(date +%Y-%m-%d)"
  printf '  后端    : docker exec %s tail -f /app/logs/backend-%s.log\n' "$(compose_service_container "$PROJECT_NAME" backend)" "$today"
  printf '  重排服务: docker exec %s tail -f /app/logs/reranker-%s.log\n' "$(compose_service_container "$PROJECT_NAME" reranker)" "$today"
  printf '  前端    : docker exec %s tail -f /app/logs/frontend-%s.log\n' "$(compose_service_container "$PROJECT_NAME" frontend)" "$today"
  printf '  全部文件: docker exec %s ls -la /app/logs/\n' "$(compose_service_container "$PROJECT_NAME" backend)"
}

cmd_down() {
  ensure_docker
  [[ -f "$ENV_FILE" ]] && env_load "$ENV_FILE" "$COMPOSE_FILE"
  log_step "停止服务（数据卷保留）"
  compose_cmd down "$PROJECT_NAME" "$COMPOSE_FILE" "$ENV_FILE"
  log "已停止。重新启动: ./scripts/deploy.sh up"
}

cmd_help() {
  cat <<'EOF'
用法: ./scripts/deploy.sh [子命令]

【方式一】服务器 / 新机器一键部署（全平台兼容：Linux / macOS / Windows Git Bash）

子命令:
  up          一键部署（默认）：装依赖 → 校验配置 → 构建 → 启动 → 健康检查
  init        仅生成 deploy/.env 配置（交互式向导）
  restart     重启服务（默认 backend + frontend，可指定服务名）
  status      查看容器状态与健康检查
  logs [svc]  跟踪日志（默认 backend；svc=all 看全部容器）
  logfiles    打印容器内按天归档的日志文件位置
  down        停止全部服务（数据卷保留）
  help        显示此帮助

环境变量:
  DEPLOY_NO_CACHE=0            跳过 --no-cache 重建（默认 1，强制干净构建）
  DEPLOY_BACKEND_PORT=8080     后端宿主机端口
  DEPLOY_FRONTEND_PORT=5173    前端宿主机端口
  DEPLOY_INFRA_TIMEOUT_SEC=300     中间件就绪超时
  DEPLOY_OLLAMA_INIT_TIMEOUT_SEC=1800  Ollama 模型拉取超时
  DEPLOY_RERANKER_TIMEOUT_SEC=900    Reranker 就绪超时（首次需下载模型）
  DEPLOY_BACKEND_TIMEOUT_SEC=1800    后端就绪超时
  DEPLOY_FRONTEND_TIMEOUT_SEC=600    前端就绪超时
  DEPLOY_HTTP_TIMEOUT_SEC=10         单次 HTTP 探测超时

配置覆盖（优先于 deploy/.env）:
  DEPLOY_DASHSCOPE_API_KEY / DEPLOY_TAVILY_API_KEY / DEPLOY_POSTGRES_PASSWORD
  DEPLOY_MINIO_PASSWORD / DEPLOY_APP_JWT_SECRET / DEPLOY_CORS_ORIGINS

示例:
  ./scripts/deploy.sh                 # 全新机器一键部署
  ./scripts/deploy.sh init            # 先配置再部署
  ./scripts/deploy.sh logs backend    # 看后端日志（容器 stdout）
  ./scripts/deploy.sh logfiles        # 看容器内按天归档的日志文件位置
  ./scripts/deploy.sh restart frontend
EOF
}

# ---------- 路由 ----------
#
# 注意：这里刻意不使用 `shift`。
# bash 的 `shift` 在没有位置参数时返回 1，配合 `set -e` 会让脚本**静默退出**
# （stdout/stderr 全空、退出码 1）——例如 `deploy.sh restart` 不带服务名时就会命中。
# 改用 "$2" / "${@:2}" 直接取参，天然兼容"只有子命令"和"子命令+参数"两种情况。

case "$ACTION" in
  up)            cmd_up ;;
  init)          cmd_init ;;
  restart)       cmd_restart "${@:2}" ;;
  status)        cmd_status ;;
  logs)          cmd_logs "${2:-backend}" ;;
  logfiles)      cmd_logfiles ;;
  down)          cmd_down ;;
  help|--help|-h) cmd_help ;;
  *)
    log_error "未知子命令: $ACTION"
    cmd_help
    exit 1
    ;;
esac
