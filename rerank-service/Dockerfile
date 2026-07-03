# ===========================================
# BGE Reranker v2-m3 推理服务 Dockerfile
# 跨平台支持：amd64 (x86) + arm64 (Apple Silicon / Graviton)
#
# 关键优化：
# 1. 镜像构建阶段不再预下载模型（避免 docker build 卡 50+ 分钟）
# 2. 使用 hf-mirror.com 镜像源加速模型下载（国内网络友好）
# 3. 启动阶段首次运行按需下载到 volume，下载一次永久缓存
# 4. 安装 CPU 版 PyTorch，体积小、下载快
# ===========================================

FROM python:3.11-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 设置 HuggingFace 镜像源（关键：国内构建/运行速度提升 10x+）
ENV HF_ENDPOINT=https://hf-mirror.com
ENV HF_HOME=/root/.cache/huggingface
ENV TRANSFORMERS_CACHE=/root/.cache/huggingface
ENV HF_HUB_DOWNLOAD_TIMEOUT=300

# 安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制服务代码
COPY server.py .

# ⚠️ 关键变更：移除原来的"构建时预下载模型"逻辑
# 旧逻辑会在 docker build 阶段从 HuggingFace 下载 568MB 模型，
# 国内网络下需要 30-55 分钟。改为启动时按需下载 + volume 缓存：
# - 首次启动：server.py 启动时按需下载（HF 镜像源，约 3-5 分钟）
# - 后续启动：模型已在 huggingface_cache volume 中，秒级启动
# - 模型升级：删除 volume `docker volume rm deploy_huggingface_cache` 强制重下

ENV PORT=80
EXPOSE 80

# 健康检查：模型未加载完成时返回 starting，加载完成后返回 ok
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=15 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:80/health')" || exit 1

CMD ["python", "server.py"]
