# ===========================================
# BGE Reranker v2-m3 推理服务 Dockerfile
# 跨平台支持：amd64 (x86) + arm64 (Apple Silicon / Graviton)
# ===========================================

FROM python:3.11-slim

WORKDIR /app

# 安装系统依赖（编译 PyTorch 扩展可能需要）
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制服务代码
COPY server.py .

# 构建时预下载模型，避免首次启动过慢
# 如果网络受限，可注释掉此行，改为运行时按需下载
RUN python -c "from sentence_transformers import CrossEncoder; CrossEncoder('BAAI/bge-reranker-v2-m3', max_length=512)" || \
    echo "模型预下载失败，将在运行时下载"

ENV PORT=80
EXPOSE 80

# 健康检查：模型未加载完成时返回 starting，加载完成后返回 ok
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=10 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:80/health')" || exit 1

CMD ["python", "server.py"]
