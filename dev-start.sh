#!/bin/bash
# ===========================================
# 一键本地启动脚本
# 启动后端（Spring Boot）+ 前端（Vite），热重载调试
# 前提：Docker 基础设施（postgres/redis/minio/ollama/reranker）已在运行
# ===========================================

set -e

PROJECT_ROOT="/Users/lvyizhuo/project/i/MyProject-SDAgent"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

# Java 21（Homebrew 安装路径）
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

echo "============================================"
echo "  本地开发环境一键启动"
echo "============================================"
echo ""

# 1. 检查 Docker 基础设施
echo "[1/4] 检查 Docker 基础设施..."
for svc in policy-agent-postgres policy-agent-redis policy-agent-minio policy-agent-ollama policy-agent-reranker; do
    if docker ps --format '{{.Names}}' | grep -q "$svc"; then
        echo "  ✓ $svc 运行中"
    else
        echo "  ✗ $svc 未运行，请先启动: docker compose up -d postgres redis minio ollama ollama-init reranker"
        exit 1
    fi
done

# 2. 停掉 Docker 中的 backend 和 frontend（避免端口冲突）
echo ""
echo "[2/4] 停掉 Docker 中的 backend 和 frontend（避免端口冲突）..."
docker stop policy-agent-backend policy-agent-frontend 2>/dev/null || true
echo "  ✓ 已停掉"

# 3. 启动后端
echo ""
echo "[3/4] 启动后端（Spring Boot + mcp profile）..."
cd "$BACKEND_DIR"
set -a
source .env.local
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp > /tmp/backend.log 2>&1 &
BACKEND_PID=$!
echo "  ✓ 后端已启动 (PID: $BACKEND_PID，日志: /tmp/backend.log)"

# 4. 启动前端
echo ""
echo "[4/4] 启动前端（Vite + pnpm）..."
cd "$FRONTEND_DIR"
pnpm dev > /tmp/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "  ✓ 前端已启动 (PID: $FRONTEND_PID，日志: /tmp/frontend.log)"

echo ""
echo "============================================"
echo "  启动完成！"
echo "============================================"
echo ""
echo "  前端:  http://localhost:5173"
echo "  后端:  http://localhost:8080"
echo "  API健康检查: curl http://localhost:8080/actuator/health"
echo ""
echo "  后端日志:   tail -f /tmp/backend.log"
echo "  前端日志:   tail -f /tmp/frontend.log"
echo ""
echo "  停止服务: kill $BACKEND_PID $FRONTEND_PID"
echo "  恢复 Docker: docker start policy-agent-backend policy-agent-frontend"
echo ""

# 等待用户按 Ctrl+C 停止
trap "echo ''; echo '正在停止服务...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; echo '已停止'; exit 0" INT TERM
wait
