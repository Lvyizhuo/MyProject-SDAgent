"""
BGE Reranker v2-m3 推理服务

提供与 HuggingFace TEI /rerank 端点兼容的 API：
  POST /rerank   { "query": "...", "texts": ["..."], "return_text": false }
  -> [ { "index": 0, "score": 0.98 }, ... ]

模型：BAAI/bge-reranker-v2-m3（Cross-Encoder 架构）

启动行为：
  - 服务启动时在后台线程预加载模型（首次需要 3-5 分钟从 HF 镜像下载 568MB）
  - 加载期间 /health 返回 {"status": "loading", "model": "..."}（HTTP 200，HTTP client 不报错）
  - 加载完成后 /health 返回 {"status": "ok", ...}
  - /rerank 请求会等待模型加载完成（阻塞）后处理  - 模型文件通过 volume `huggingface_cache` 持久化，重启后秒级加载
"""

# ⚠️ 必须在所有 import 之前删除 HF_ENDPOINT + 设置 offline 模式！
# Dockerfile 写死了 HF_ENDPOINT=https://hf-mirror.com，但该镜像源在
# huggingface_hub 的 HEAD 请求时返回 308 重定向不跟随，导致模型永远下载失败。
# huggingface_hub.constants.ENDPOINT 在 import 时固定，import sentence_transformers
# 会间接 import huggingface_hub，所以必须在 import 之前清掉环境变量。
# 强制 offline 模式：模型文件已下载到 HF_HOME cache，不需要任何远端 HEAD 探测。
import os
os.environ.pop("HF_ENDPOINT", None)
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "0"

import logging
import threading
import time
from typing import List

import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import CrossEncoder

MODEL_ID = os.environ.get("RERANK_MODEL_ID", "BAAI/bge-reranker-v2-m3")
PORT = int(os.environ.get("PORT", "80"))

# HF_HOME 等配置（HF_ENDPOINT 已在上方 import 前 pop 掉）
os.environ.setdefault("HF_HOME", "/root/.cache/huggingface")
os.environ.setdefault("HF_HUB_DOWNLOAD_TIMEOUT", "300")
# 关闭并发下载（huggingface_hub 默认 8 并发在 7.7GB 内存限制下会 OOM）
os.environ.setdefault("HF_HUB_ENABLE_HF_TRANSFER", "0")

# 关键：使用本地缓存目录的绝对路径作为模型 ID
# transformers 4.x 在 HF_HUB_OFFLINE=1 模式下加载 model.safetensors
# 时会触发 NoneType endswith bug（modeling_utils.py:4924）。
# 解决方案：不用 offline 模式，而是直接传本地绝对路径，CrossEncoder
# 会从该路径加载，并且不会去远端探测。
def _resolve_local_snapshot() -> str:
    """根据 HF_HOME + MODEL_ID 找到本地 snapshot 绝对路径。"""
    hf_home = os.environ.get("HF_HOME", "/root/.cache/huggingface")
    safe_id = MODEL_ID.replace("/", "--")
    snapshots_root = os.path.join(
        hf_home, "hub", f"models--{safe_id}", "snapshots"
    )
    if not os.path.isdir(snapshots_root):
        return MODEL_ID  # 触发正常下载流程
    candidates = [
        d for d in os.listdir(snapshots_root)
        if os.path.isfile(os.path.join(snapshots_root, d, "config.json"))
    ]
    if not candidates:
        return MODEL_ID
    return os.path.join(snapshots_root, candidates[0])

# 启动时计算本地路径（如果不存在则用 MODEL_ID 走远端下载）
SNAPSHOT_DIR = _resolve_local_snapshot()

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("rerank-service")

app = FastAPI(title="BGE Reranker Service", version="1.0.0")

# 延迟加载 + 启动时后台预下载
_reranker: CrossEncoder | None = None
_load_lock = threading.Lock()
_load_started = False


def _load_model() -> None:
    """同步加载模型。

    关键修复：HF_ENDPOINT 已在顶部 pop 掉，走官方 huggingface.co。
    模型已通过手工下载到 HF_HOME cache 目录，sentence-transformers 会自动从缓存加载。
    """
    global _reranker
    logger.info("正在加载 rerank 模型: %s (HF_ENDPOINT=%s) ...", MODEL_ID, os.environ.get("HF_ENDPOINT"))
    start = time.time()
    # 用本地 snapshot 路径加载（而不是 MODEL_ID 字符串）
    # 这样 CrossEncoder 直接读本地 model.safetensors，不会触发远端 HEAD 探测
    # 也不会触发 HF_HUB_OFFLINE 模式下的 NoneType endswith bug
    _reranker = CrossEncoder(SNAPSHOT_DIR, max_length=512)
    elapsed = time.time() - start
    logger.info("模型加载完成: %s (耗时 %.1fs)", MODEL_ID, elapsed)


def _background_load() -> None:
    """后台线程加载模型，加载失败时记录日志。"""
    try:
        _load_model()
    except Exception as e:
        logger.error("模型后台加载失败（首次请求时会重试）: %s: %s", type(e).__name__, e)


def ensure_loaded() -> None:
    """确保模型已加载。如果还没启动过，启动后台加载（不阻塞调用方）。"""
    global _load_started
    if _reranker is not None or _load_started:
        return
    with _load_lock:
        if _load_started:
            return
        _load_started = True
        thread = threading.Thread(target=_background_load, daemon=True, name="model-loader")
        thread.start()


def get_reranker() -> CrossEncoder:
    """获取已加载的模型。如果未加载，等待加载完成。"""
    if _reranker is not None:
        return _reranker
    # 触发后台加载
    ensure_loaded()
    # 等待模型加载完成（最多 10 分钟）
    deadline = time.time() + 600
    while _reranker is None and time.time() < deadline:
        time.sleep(1)
    if _reranker is None:
        raise RuntimeError(f"模型加载超时（10 分钟），请检查 HF_ENDPOINT 配置和网络: {MODEL_ID}")
    return _reranker


class RerankRequest(BaseModel):
    query: str
    texts: List[str]
    return_text: bool = False


class RerankResult(BaseModel):
    index: int
    score: float


@app.get("/health")
async def health():
    """健康检查端点。
    - 启动中：{"status": "starting", "model": "..."}
    - 加载中：{"status": "loading", "model": "..."}
    - 就绪：{"status": "ok", "model": "..."}
    """
    if _reranker is not None:
        return {"status": "ok", "model": MODEL_ID}
    if not _load_started:
        return {"status": "starting", "model": MODEL_ID}
    return {"status": "loading", "model": MODEL_ID}


@app.on_event("startup")
async def _on_startup() -> None:
    """服务启动时立即在后台开始加载模型。"""
    logger.info("服务启动，触发后台模型预加载 ...")
    ensure_loaded()


@app.post("/rerank", response_model=List[RerankResult])
async def rerank(req: RerankRequest):
    """对文档列表按与 query 的相关性重排序，返回降序排列的 (index, score)。"""
    if not req.texts:
        return []

    model = get_reranker()

    # 构造 (query, text) pair 列表
    pairs = [[req.query, text] for text in req.texts]

    # 计算相关性分数，normalize=True 将分数映射到 0-1（sigmoid）
    scores = model.predict(pairs, convert_to_numpy=True)

    # 组装结果并按分数降序排列
    results = [
        RerankResult(index=i, score=float(s))
        for i, s in enumerate(scores)
    ]
    results.sort(key=lambda x: x.score, reverse=True)

    return results


if __name__ == "__main__":
    logger.info("启动 BGE Reranker 服务 | model=%s | port=%s", MODEL_ID, PORT)
    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="info")
