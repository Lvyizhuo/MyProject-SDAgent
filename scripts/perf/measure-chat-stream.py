#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SSE 流式对话测量脚本：统计「首字可见延迟(TTFT)」与端到端耗时。

用法:
    python3 scripts/perf/measure-chat-stream.py --base http://127.0.0.1:8080 \
        --user admin --password admin --queries queries.json --repeat 3

输出:
    场景          端到端ms   TTFTms   增量块数   阶段事件序列   输出字符
"""

import argparse
import json
import os
import time
import urllib.request
import uuid


def post_json(url, payload, headers=None, timeout=300):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    merged = {"Content-Type": "application/json"}
    merged.update(headers or {})
    req = urllib.request.Request(url, data=body, headers=merged, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def stream_once(base, token, message, conversation_id):
    """发起一次 SSE 请求，返回 (ttft_ms, total_ms, deltas, stages, chars, error)。"""
    body = json.dumps({"message": message, "conversationId": conversation_id},
                      ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        f"{base}/api/chat/stream", data=body, method="POST",
        headers={"Content-Type": "application/json", "Accept": "text/event-stream",
                 "Authorization": f"Bearer {token}"})
    t0 = time.time()
    ttft = None
    deltas = 0
    chars = 0
    stages = []
    buffer = ""
    error = None
    with urllib.request.urlopen(req, timeout=300) as resp:
        while True:
            raw = resp.readline()
            if not raw:
                break
            line = raw.decode("utf-8", "ignore").rstrip("\n")
            if line.strip():
                buffer += line + "\n"
            if buffer.endswith("\n\n") or (line.strip() == "" and buffer.strip()):
                frame = buffer.strip()
                buffer = ""
                if not frame:
                    continue
                event = "message"
                data_lines = []
                for l in frame.split("\n"):
                    if l.startswith("event:"):
                        event = l[6:].strip()
                    elif l.startswith("data:"):
                        data_lines.append(l[5:].strip())
                if not data_lines:
                    continue
                try:
                    payload = json.loads("\n".join(data_lines))
                except Exception:
                    payload = {"content": "\n".join(data_lines)}
                if event == "delta":
                    c = payload.get("content") or ""
                    if c:
                        if ttft is None:
                            ttft = (time.time() - t0) * 1000
                        deltas += 1
                        chars += len(c)
                elif event == "stage":
                    stages.append(payload.get("stage"))
                elif event == "error":
                    error = payload.get("message")
    total = (time.time() - t0) * 1000
    return ttft, total, deltas, stages, chars, error


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--queries", default=None)
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--out", default=None)
    args = parser.parse_args()

    if args.queries and os.path.exists(args.queries):
        with open(args.queries, encoding="utf-8") as fh:
            queries = json.load(fh)
    else:
        queries = json.loads(args.queries or json.dumps([
            {"name": "政策问答", "message": "山东省汽车以旧换新补贴政策是什么？"},
            {"name": "补贴计算", "message": "手机 5999元 能补贴多少钱？"},
            {"name": "申领条件", "message": "家电以旧换新需要满足什么条件才能申领？"},
        ], ensure_ascii=False))

    token = post_json(f"{args.base}/api/admin/auth/login",
                      {"username": args.user, "password": args.password})["token"]

    rows = []
    for q in queries:
        for r in range(args.repeat):
            cid = f"stream-{uuid.uuid4().hex[:10]}"
            ttft, total, deltas, stages, chars, error = stream_once(
                args.base, token, q["message"], cid)
            rows.append({
                "name": q.get("name", q["message"][:12]),
                "conversationId": cid,
                "ttft_ms": round(ttft) if ttft else None,
                "total_ms": round(total),
                "deltas": deltas,
                "stages": stages,
                "chars": chars,
                "error": error,
            })
            print(f"[{rows[-1]['name']}] TTFT={rows[-1]['ttft_ms']}ms  端到端={rows[-1]['total_ms']}ms  "
                  f"增量块={deltas}  字符={chars}  阶段={'→'.join(stages)}"
                  + (f"  错误={error}" if error else ""), flush=True)

    print("\n" + "=" * 96)
    print(f"{'场景':<12}{'TTFT(ms)':>11}{'端到端(ms)':>12}{'增量块':>8}{'字符':>8}  阶段序列")
    print("-" * 96)
    for row in rows:
        print(f"{row['name']:<12}{str(row['ttft_ms']):>11}{row['total_ms']:>12}{row['deltas']:>8}"
              f"{row['chars']:>8}  {'→'.join(row['stages'])}")
    print("=" * 96)

    ttfts = [r["ttft_ms"] for r in rows if r["ttft_ms"] is not None]
    totals = [r["total_ms"] for r in rows]
    if ttfts:
        print(f"TTFT   P50={sorted(ttfts)[len(ttfts)//2]}ms  max={max(ttfts)}ms")
    print(f"端到端 P50={sorted(totals)[len(totals)//2]}ms  max={max(totals)}ms")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump(rows, fh, ensure_ascii=False, indent=2)
        print(f"结果已写入 {args.out}")


if __name__ == "__main__":
    main()
