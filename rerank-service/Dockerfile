# ===========================================
# BGE Reranker v2-m3 推理服务 Dockerfile
# 跨平台支持：amd64 (x86) + arm64 (Apple Silicon / Graviton)
#
# 关键优化：
# 1. 镜像构建阶段不再预下载模型（避免 docker build 卡 50+ 分钟）
# 2. 启动阶段首次运行按需下载到 volume，下载一次永久缓存
# 3. 安装 CPU 版 PyTorch，体积小、下载快
# ===========================================

FROM python:3.11-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 关键：不再硬编码 HF_ENDPOINT。
# 历史教训：国内 hf-mirror.com 镜像源返回 308 重定向，huggingface_hub
# 在 HEAD 请求时不跟随重定向就放弃，导致模型永远下载失败。
# 容器内 huggingface.co 直连可接受，下载时间 5-15 分钟。
ENV HF_HOME=/root/.cache/huggingface
ENV TRANSFORMERS_CACHE=/root/.cache/huggingface
ENV HF_HUB_DOWNLOAD_TIMEOUT=300
# 关闭并发下载（huggingface_hub 默认 8 并发在 7.7GB 内存限制下会 OOM）
ENV HF_HUB_ENABLE_HF_TRANSFER=0

# 安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制服务代码
COPY server.py .

# 模型下载策略：
# - 首次启动：server.py 启动时按需下载（huggingface.co 官方源，约 5-15 分钟）
# - 后续启动：模型已在 huggingface_cache volume 中，秒级启动
# - 模型升级：删除 volume `docker volume rm deploy_huggingface_cache` 强制重下

ENV PORT=80
EXPOSE 80

# 健康检查：模型未加载完成时返回 starting/loading，加载完成后返回 ok
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=15 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:80/health')" || exit 1

CMD ["python", "server.py"]
