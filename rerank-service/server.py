"""
BGE Reranker v2-m3 推理服务

提供与 HuggingFace TEI /rerank 端点兼容的 API：
  POST /rerank   { "query": "...", "texts": ["..."], "return_text": false }
  -> [ { "index": 0, "score": 0.98 }, ... ]

模型：BAAI/bge-reranker-v2-m3（Cross-Encoder 架构）
"""

import os
import logging
from typing import List

import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import CrossEncoder

MODEL_ID = os.environ.get("RERANK_MODEL_ID", "BAAI/bge-reranker-v2-m3")
PORT = int(os.environ.get("PORT", "80"))

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("rerank-service")

app = FastAPI(title="BGE Reranker Service", version="1.0.0")

# 延迟加载：首次请求时才加载模型，避免健康检查在模型加载完成前就启动
_reranker: CrossEncoder | None = None


def get_reranker() -> CrossEncoder:
    global _reranker
    if _reranker is None:
        logger.info("正在加载 rerank 模型: %s ...", MODEL_ID)
        _reranker = CrossEncoder(MODEL_ID, max_length=512)
        logger.info("模型加载完成: %s", MODEL_ID)
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
    """健康检查端点。模型未加载时返回 starting，加载完成返回 ok。"""
    if _reranker is None:
        return {"status": "starting", "model": MODEL_ID}
    return {"status": "ok", "model": MODEL_ID}


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
