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
#
# 注意：这里**不能**设 HF_HUB_OFFLINE=1 / TRANSFORMERS_OFFLINE=1。
# 之前误设了 offline 模式，而 Dockerfile 的注释早已写明"offline 不能用"
# （transformers 4.x 在 offline 模式下加载 model.safetensors 会触发
#  NoneType endswith bug，modeling_utils.py:4924）。
# 后果是：只要本地 HF 缓存卷是空的（全新机器 / 本地开发实例），
# 模型就永远下载不下来，服务会永久卡在 status=loading，
# 而容器 healthcheck 又只看 HTTP 200，于是表现为"看起来启动了但永远不就绪"。
# 现在改为：清掉 endpoint 相关环境变量，允许按需从 huggingface.co 下载；
# 已有本地 snapshot 时仍走 _resolve_local_snapshot() 的绝对路径，不触网。
import os
os.environ.pop("HF_ENDPOINT", None)
os.environ.pop("HF_HUB_OFFLINE", None)
os.environ.pop("TRANSFORMERS_OFFLINE", None)
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "0"

import logging
import logging.handlers
import threading
import time
from datetime import datetime
from typing import List

import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import CrossEncoder

MODEL_ID = os.environ.get("RERANK_MODEL_ID", "BAAI/bge-reranker-v2-m3")
PORT = int(os.environ.get("PORT", "80"))
# 推理批大小：1 = 不做批次补齐（真实 chunk 长度参差大时最省算力）。
# 可用环境变量 RERANK_BATCH_SIZE 覆盖。
RERANK_BATCH_SIZE = max(1, int(os.environ.get("RERANK_BATCH_SIZE", "1")))

# ---- 日志配置（与后端 Spring Boot 同一套规范）----
#
# 规范：
#   1) 格式：ISO-8601 带时区时间戳 + 级别 + PID + 组件名 + 线程 + logger + 消息，
#      与后端 logback 的 FILE_LOG_PATTERN 同构（后端去掉 ANSI 颜色）。
#   2) 存储：容器内 /app/logs/reranker.log，按天归档到
#      /app/logs/archive/reranker.YYYY-MM-DD.log.gz，保留 30 天。
#   3) 同时保留 stdout 输出 —— 由 Docker 收集进 json-file，
#      保证 `docker compose logs reranker` 与其他组件的读取方式一致。
LOG_DIR = os.environ.get("RERANK_LOG_DIR", "/app/logs")
# 活动日志文件名带日期：reranker-<YYYY-MM-DD>.log
# 日期由启动脚本 / entrypoint 注入（容器内由 compose 传入，缺失时取当天日期）
LOG_DATE = os.environ.get("LOG_DATE") or datetime.now().strftime("%Y-%m-%d")
LOG_FILE = os.path.join(LOG_DIR, f"reranker-{LOG_DATE}.log")
# 单文件上限 100MB，超过后切分为 .1 / .2 ...（不压缩）
LOG_MAX_BYTES = 100 * 1024 * 1024
LOG_BACKUP_COUNT = 30
# 保留天数：启动时清理过期文件
LOG_RETENTION_DAYS = max(1, int(os.environ.get("RERANK_LOG_RETENTION_DAYS", "30")))


class Iso8601Formatter(logging.Formatter):
    """输出与 Java %d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} 一致的带时区时间戳。

    Python 标准库的 asctime 不带时区偏移，与后端日志无法逐行对齐，
    这里用 astimezone().isoformat(timespec="milliseconds") 补齐偏移量。
    """

    def formatTime(self, record, datefmt=None):  # noqa: N802 - 覆写标准库命名
        return datetime.fromtimestamp(record.created).astimezone().isoformat(timespec="milliseconds")


def _build_logging() -> None:
    """装配统一日志：stdout + 按天切分的文件。"""
    formatter = Iso8601Formatter(
        # %(levelname)5s 与 Java 的 %5p 一致（右对齐 5 字符），使两组件日志逐行同构
        fmt="%(asctime)s %(levelname)5s %(process)d --- [reranker][%(threadName)s] %(name)s : %(message)s"
    )

    root = logging.getLogger()
    root.setLevel(logging.INFO)
    # 清掉 uvicorn / 第三方库预置的 handler，避免同一行日志重复输出
    for handler in list(root.handlers):
        root.removeHandler(handler)

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    root.addHandler(stream_handler)

    try:
        os.makedirs(LOG_DIR, exist_ok=True)
        # RotatingFileHandler：纯按容量切分，不压缩，便于直接查看排查
        file_handler = logging.handlers.RotatingFileHandler(
            LOG_FILE,
            maxBytes=LOG_MAX_BYTES,
            backupCount=LOG_BACKUP_COUNT,
            encoding="utf-8",
        )
        file_handler.setFormatter(formatter)
        root.addHandler(file_handler)
        _purge_expired_logs()
    except OSError as exc:
        logging.getLogger("rerank-service").warning(
            "日志文件目录不可写，仅输出到 stdout | dir=%s | error=%s", LOG_DIR, exc)


def _purge_expired_logs() -> None:
    """清理超过保留期的日志文件（默认 30 天）。"""
    import time

    cutoff = time.time() - LOG_RETENTION_DAYS * 86400
    try:
        for name in os.listdir(LOG_DIR):
            if not name.startswith("reranker-") or not name.endswith(".log"):
                continue
            path = os.path.join(LOG_DIR, name)
            if os.path.isfile(path) and os.path.getmtime(path) < cutoff:
                os.remove(path)
    except OSError:
        pass

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

_build_logging()
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
    # batch_size=1：sentence-transformers 默认按 batch_size=32 把全部候选塞进一个批次，
    # 补齐到最长候选的长度。实测真实政策 chunk 长度差异极大（5~470 token），
    # 单批次下 10 条候选要补齐到 470 token，耗时 5163ms；
    # 改为 batch_size=1 后无补齐浪费，耗时 3259ms（-37%），且打分与排序完全一致。
    scores = model.predict(pairs, batch_size=RERANK_BATCH_SIZE, convert_to_numpy=True)

    # 组装结果并按分数降序排列
    results = [
        RerankResult(index=i, score=float(s))
        for i, s in enumerate(scores)
    ]
    results.sort(key=lambda x: x.score, reverse=True)

    return results


if __name__ == "__main__":
    logger.info("启动 BGE Reranker 服务 | model=%s | port=%s", MODEL_ID, PORT)
    # log_config=None：不让 uvicorn 用自己的 dictConfig 覆盖日志配置，
    # 这样 uvicorn.error / uvicorn.access 也会走上面装配的统一 formatter。
    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="info", log_config=None)
