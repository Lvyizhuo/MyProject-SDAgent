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
# APT 源走阿里云镜像：deb.debian.org 在国内实测仅 ~66KB/s，
# 拉 9.6MB 的 arm64 Packages 索引要几分钟，且常在构建中途超时断开；
# mirrors.aliyun.com 实测 ~780KB/s，快一个数量级。
ARG DEBIAN_MIRROR=https://mirrors.aliyun.com
RUN sed -i "s|http://deb.debian.org/debian-security|${DEBIAN_MIRROR}/debian-security|g; s|http://deb.debian.org/debian|${DEBIAN_MIRROR}/debian|g" \
        /etc/apt/sources.list.d/debian.sources \
    && apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 关键：不再硬编码 HF_ENDPOINT。
# 历史教训：国内 hf-mirror.com 镜像源返回 308 重定向，huggingface_hub
# 在 HEAD 请求时不跟随重定向就放弃，导致模型永远下载失败。
# 容器内 huggingface.co 直连可接受，下载时间 5-15 分钟。
# 注意：HF_HUB_OFFLINE=1 不能用 —— transformers 4.x 在 offline 模式下加载
# model.safetensors 会触发 NoneType endswith bug（modeling_utils.py:4924）。
# server.py 已改为用本地 snapshot 绝对路径加载，无需 offline 模式。
ENV HF_HOME=/root/.cache/huggingface
ENV TRANSFORMERS_CACHE=/root/.cache/huggingface
ENV HF_HUB_DOWNLOAD_TIMEOUT=300
# 关闭并发下载（huggingface_hub 默认 8 并发在 7.7GB 内存限制下会 OOM）
ENV HF_HUB_ENABLE_HF_TRANSFER=0

# 安装 Python 依赖
COPY requirements.txt .
# PyPI 镜像源：清华源（tuna）2026-08 起对 wheel 下载返回 403（镜像侧拒绝，
# 索引页 403 全站不可用），已改用阿里云镜像。仍可临时覆盖：
#   docker build --build-arg PIP_INDEX_URL=https://pypi.org/simple ...
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

# torch 单独走 PyTorch 官方 CPU 源，且必须先于 requirements 安装。
# 原因：PyPI 上的 torch==2.5.1 是 CUDA 构建版，arm64 下会连带拉取
# nvidia-cuda-* 系列共约 2.5GB 依赖，rerank 纯 CPU 推理根本用不到，
# 构建耗时从分钟级拉长到半小时级且容易中途断流。
# download.pytorch.org/whl/cpu 提供 manylinux_2_17_aarch64 的纯 CPU wheel
# （约 180MB，无 nvidia 依赖）。先装好 torch 后，后续 -r 安装会认为
# 约束已满足而跳过重复下载。
ARG TORCH_CPU_INDEX_URL=https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir torch==2.5.1 --index-url ${TORCH_CPU_INDEX_URL}

RUN pip install --no-cache-dir -r requirements.txt -i ${PIP_INDEX_URL}

# 复制服务代码
COPY server.py .

# 模型下载策略：
# - 首次启动：server.py 启动时按需下载（huggingface.co 官方源，约 5-15 分钟）
# - 后续启动：模型已在 huggingface_cache volume 中，秒级启动
# - 模型升级：删除 volume `docker volume rm deploy_huggingface_cache` 强制重下

ENV PORT=80
EXPOSE 80

# 日志目录：与后端同一套规范（容器内 /app/logs，按天归档）
RUN mkdir -p /app/logs && chmod 755 /app/logs

# 健康检查：必须判断 body 里的 status 字段，不能只看 HTTP 200。
# /health 在模型加载期间也返回 200（{"status":"loading"}），
# 只看 HTTP 状态码会让"模型永远下载不下来"的容器也显示为 healthy。
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=15 \
  CMD python -c "import urllib.request, json; r=urllib.request.urlopen('http://localhost:80/health'); d=json.loads(r.read()); exit(0 if d.get('status')=='ok' else 1)" || exit 1

CMD ["python", "server.py"]
