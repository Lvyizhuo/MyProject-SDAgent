#!/bin/bash
# ===========================================
# 一键本地开发启动脚本
# 启动后端（Spring Boot 本地热重载）+ 前端（Vite 本地热重载）
# 前提：Docker 基础设施（postgres/redis/minio/ollama/reranker）必须已在运行
# ===========================================
# 用法:
#   ./dev-start.sh          # 启动开发环境
#   ./dev-start.sh stop     # 停止本地服务
# ===========================================

set -e

PROJECT_ROOT="/Users/lvyizhuo/project/i/MyProject-SDAgent"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
ENV_FILE="$BACKEND_DIR/.env.local"
LOG_DIR="$PROJECT_ROOT/logs"

# Java 21（Homebrew 安装路径）
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 确保脚本在项目根目录运行
cd "$PROJECT_ROOT"

# 创建日志目录（已存在则跳过）
mkdir -p "$LOG_DIR"

# 停止功能
stop_services() {
    echo ""
    echo "正在停止本地服务..."
    # 杀掉整个进程组（确保 Java 子进程也被清理）
    if [ -n "${BACKEND_PID:-}" ]; then
        kill -- -$(ps -o pgid= -p "$BACKEND_PID" 2>/dev/null | tr -d ' ') 2>/dev/null || true
    fi
    if [ -n "${FRONTEND_PID:-}" ]; then
        kill -- -$(ps -o pgid= -p "$FRONTEND_PID" 2>/dev/null | tr -d ' ') 2>/dev/null || true
    fi
    # 额外清理：杀掉可能残留的 Java 进程（仅限本项目）
    lsof -ti:8080 2>/dev/null | xargs kill 2>/dev/null || true
    lsof -ti:5173 2>/dev/null | xargs kill 2>/dev/null || true
    echo "已停止所有本地服务"
    exit 0
}

# 处理 ./dev-start.sh stop
if [ "${1:-}" = "stop" ]; then
    stop_services
fi

echo "============================================"
echo "  本地开发环境一键启动"
echo "============================================"
echo ""

# ==========================================
# 1. 检查 Docker 基础设施
# ==========================================
echo "[1/6] 检查 Docker 基础设施..."
INFRA_CONTAINERS=(
    "policy-agent-postgres"
    "policy-agent-redis"
    "policy-agent-minio"
    "policy-agent-ollama"
    "policy-agent-reranker"
)
ALL_RUNNING=true
for svc in "${INFRA_CONTAINERS[@]}"; do
    if docker ps --format '{{.Names}}' | grep -q "$svc"; then
        echo "  ✓ $svc 运行中"
    else
        echo "  ✗ $svc 未运行"
        ALL_RUNNING=false
    fi
done

if [ "$ALL_RUNNING" = false ]; then
    echo ""
    echo "  ⚠️  部分基础设施未运行，请先启动："
    echo "     cd $PROJECT_ROOT/backend"
    echo "     docker compose up -d postgres redis minio ollama ollama-init reranker"
    exit 1
fi

# ==========================================
# 2. 检查环境文件
# ==========================================
echo ""
echo "[2/6] 检查环境配置..."
if [ ! -f "$ENV_FILE" ]; then
    echo "  ✗ 未找到 $ENV_FILE"
    echo "  ⚠️  请从 deploy/.env 复制并调整后创建"
    exit 1
fi
echo "  ✓ $ENV_FILE 存在"

# ==========================================
# 3. 停掉 Docker 中的 backend/frontend
# ==========================================
echo ""
echo "[3/6] 停掉 Docker 中的 backend/frontend（避免端口冲突）..."
docker stop policy-agent-backend policy-agent-frontend 2>/dev/null || true

# 确保本地端口没有被占用
lsof -ti:8080 2>/dev/null | xargs kill 2>/dev/null || true
lsof -ti:5173 2>/dev/null | xargs kill 2>/dev/null || true

echo "  ✓ 端口已清理"

# ==========================================
# 4. 安装前端依赖（仅安装，不启动）
# ==========================================
echo ""
echo "[4/6] 安装前端依赖..."
cd "$FRONTEND_DIR"

if [ ! -d "node_modules" ] || [ ! -f "node_modules/.package-lock.json" ]; then
    echo "  → 安装前端依赖..."
    pnpm install
fi
echo "  ✓ 前端依赖就绪"

# ==========================================
# 5. 启动后端 + 等待就绪
# ==========================================
echo ""
echo "[5/6] 启动后端（Spring Boot + MCP profile）..."
cd "$BACKEND_DIR"

# 加载环境变量（set -a 使所有变量自动 export）
set -a
source "$ENV_FILE"
set +a

./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp < /dev/null > $LOG_DIR/backend.log 2>&1 &
BACKEND_PID=$!
disown "$BACKEND_PID" 2>/dev/null || true
echo "  ✓ 后端启动中 (PID: $BACKEND_PID，日志: tail -f $LOG_DIR/backend.log)"

# ==========================================
# 等待后端就绪（超时 120 秒）
# ==========================================
echo ""
echo "  ⏳ 等待后端启动就绪..."
BACKEND_READY=false
for i in $(seq 1 120); do
    if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health 2>/dev/null | grep -q "200"; then
        BACKEND_READY=true
        break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        echo "  ✗ 后端进程已退出，请检查日志: tail -20 $LOG_DIR/backend.log"
        exit 1
    fi
    sleep 1
done

if [ "$BACKEND_READY" = true ]; then
    echo "  ✓ 后端启动就绪"
else
    echo "  ⚠️  后端启动超时（120s），仍在启动中..."
    echo "     请稍后通过 curl http://localhost:8080/actuator/health 检查状态"
fi

# ==========================================
# 6. 后端就绪后，再启动前端
# ==========================================
echo ""
echo "[6/6] 启动前端（Vite 热重载）..."
cd "$FRONTEND_DIR"
pnpm dev < /dev/null > $LOG_DIR/frontend.log 2>&1 &
FRONTEND_PID=$!
disown "$FRONTEND_PID" 2>/dev/null || true
echo "  ✓ 前端已启动 (PID: $FRONTEND_PID，日志: tail -f $LOG_DIR/frontend.log)"

# ==========================================
# 完成
# ==========================================
echo ""
echo "============================================"
echo "  本地开发环境启动完成！"
echo "============================================"
echo ""
echo "  前端:  http://localhost:5173"
echo "  后端:  http://localhost:8080"
echo "  健康检查:  curl http://localhost:8080/actuator/health"
echo ""
echo "  后端日志:  tail -f $LOG_DIR/backend.log"
echo "  前端日志:  tail -f $LOG_DIR/frontend.log"
echo ""
echo "  停止服务:  ./dev-start.sh stop"
echo "  或按 Ctrl+C"
echo "  恢复 Docker: docker start policy-agent-backend policy-agent-frontend"
echo ""

# 设置 Ctrl+C 陷阱
trap "stop_services" INT TERM

# 等待任意后台进程退出
wait
