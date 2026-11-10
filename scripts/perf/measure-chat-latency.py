#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对话链路耗时测量脚本

用法:
    python3 scripts/perf/measure-chat-latency.py --base http://127.0.0.1:8080 \
        --user admin --password admin --queries queries.json

流程:
    1. 登录取 JWT
    2. 逐条发起 /api/chat 请求，记录端到端墙钟耗时
    3. 从后端容器日志里提取 [chat] / [agentChain] / [ragRetrieve] 阶段耗时与 token 用量
    4. 输出汇总表（阶段占比 + LLM 调用次数）

依赖: requests（若缺失则回退到 urllib）
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import uuid

try:
    import requests
except ImportError:  # pragma: no cover
    requests = None


def http_post_json(url, payload, headers=None, timeout=300):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    if requests is not None:
        resp = requests.post(url, data=body, headers=headers or {}, timeout=timeout)
        resp.raise_for_status()
        return resp.json()
    import urllib.request

    req = urllib.request.Request(
        url, data=body, headers=headers or {}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


STAGE_LINE = re.compile(r"\[(chat|agentChain|ragRetrieve)\] 阶段耗时 \| conversationId=(\S+) \| total=(\d+)ms(.*)")
TOKEN_LINE = re.compile(r"Token使用 \| conversationId=(\S+) \| promptTokens=(\d+) \| completionTokens=(\d+) \| totalTokens=(\d+)")
PLANNER_LLM = re.compile(r"ReAct 规划完成 \| conversationId=(\S+)")
PLANNER_SHORTCUT = re.compile(r"ReAct 规划命中\S*快捷路径 \| conversationId=(\S+)")
TOOL_CALL = re.compile(r"Executing tool call|ToolCall|tool call|调用工具")
CHAT_DONE = re.compile(r"对话完成 \| conversationId=(\S+) \| 耗时=(\d+)ms")
RAG_DOCS = re.compile(r"\[RAG\] 检索到 (\d+) 个相关文档")


def docker_logs(since, container="policy-agent-backend"):
    cmd = ["docker", "logs", "--since", since, container]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        return out.stdout + out.stderr
    except Exception as exc:  # pragma: no cover
        return f"<日志获取失败: {exc}>"


def parse_stages(log_text):
    """返回 {conversationId: {'stages': {...}, 'total': ms, 'tokens': {...}, ...}}"""
    result = {}
    for line in log_text.splitlines():
        m = STAGE_LINE.search(line)
        if m:
            scope, cid, total, rest = m.group(1), m.group(2), int(m.group(3)), m.group(4)
            entry = result.setdefault(cid, {"stages": {}, "scope_total": {}})
            entry["scope_total"][scope] = total
            stages = entry["stages"].setdefault(scope, {})
            for part in rest.split("|"):
                part = part.strip()
                if "=" not in part:
                    continue
                key, val = part.split("=", 1)
                mm = re.match(r"([\d.]+)ms\(([\d.]+)%\)", val)
                if mm:
                    stages[key] = {"ms": float(mm.group(1)), "pct": float(mm.group(2))}
                elif val.endswith("ms"):
                    stages[key] = {"ms": float(val[:-2]), "pct": None}
            continue

        m = TOKEN_LINE.search(line)
        if m:
            cid = m.group(1)
            entry = result.setdefault(cid, {"stages": {}, "scope_total": {}})
            entry["tokens"] = {
                "prompt": int(m.group(2)),
                "completion": int(m.group(3)),
                "total": int(m.group(4)),
            }
            continue

        m = PLANNER_LLM.search(line)
        if m:
            cid = m.group(1)
            entry = result.setdefault(cid, {"stages": {}, "scope_total": {}})
            entry["planner_llm"] = entry.get("planner_llm", 0) + 1
            continue

        m = PLANNER_SHORTCUT.search(line)
        if m:
            cid = m.group(1)
            entry = result.setdefault(cid, {"stages": {}, "scope_total": {}})
            entry["planner_shortcut"] = entry.get("planner_shortcut", 0) + 1
            continue

        m = RAG_DOCS.search(line)
        if m:
            # 该行没有 conversationId，用最近一条带 cid 的上下文不可靠，交给上层按顺序匹配
            pass

    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--container", default="policy-agent-backend")
    parser.add_argument("--queries", default=None, help="JSON 文件：[{name, message}] 或直接传字符串")
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--out", default=None, help="结果 JSON 输出路径")
    args = parser.parse_args()

    if args.queries and os.path.exists(args.queries):
        with open(args.queries, encoding="utf-8") as fh:
            queries = json.load(fh)
    else:
        raw = args.queries or json.dumps([
            {"name": "政策问答", "message": "山东省汽车以旧换新补贴政策是什么？"},
            {"name": "补贴计算", "message": "手机 5999元 能补贴多少钱？"},
            {"name": "问候", "message": "你好"},
        ], ensure_ascii=False)
        queries = json.loads(raw)

    # 登录
    login = http_post_json(
        f"{args.base}/api/admin/auth/login",
        {"username": args.user, "password": args.password},
        {"Content-Type": "application/json"},
    )
    token = login.get("token")
    if not token:
        print("登录失败：未取得 token", file=sys.stderr)
        print(json.dumps(login, ensure_ascii=False)[:400], file=sys.stderr)
        sys.exit(1)

    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}

    runs = []
    for q in queries:
        for r in range(args.repeat):
            cid = f"perf-{uuid.uuid4().hex[:10]}"
            started = time.time()
            err = None
            try:
                resp = http_post_json(
                    f"{args.base}/api/chat",
                    {"message": q["message"], "conversationId": cid},
                    headers,
                )
                content_len = len(resp.get("content") or "")
            except Exception as exc:
                resp, content_len, err = None, 0, str(exc)
            wall = (time.time() - started) * 1000
            runs.append({
                "name": q.get("name", q["message"][:12]),
                "conversationId": cid,
                "wall_ms": round(wall),
                "content_chars": content_len,
                "error": err,
            })
            print(f"[{runs[-1]['name']}] 端到端 {runs[-1]['wall_ms']}ms, 回复 {content_len} 字符"
                  + (f", 错误: {err}" if err else ""), flush=True)

    # 拉日志并归因
    since = "10m"
    log_text = docker_logs(since, args.container)
    per_cid = parse_stages(log_text)

    print("\n" + "=" * 108)
    print(f"{'场景':<12}{'端到端':>9}{'chat':>9}{'agentChain':>12}{'RAG':>9}"
          f"{'模型调用':>10}{'规划':>8}{'prompt':>9}{'completion':>11}")
    print("-" * 108)

    merged = []
    for run in runs:
        cid = run["conversationId"]
        info = per_cid.get(cid, {"stages": {}, "scope_total": {}})
        chat = info["scope_total"].get("chat")
        chain = info["scope_total"].get("agentChain")
        rag = info["scope_total"].get("ragRetrieve")
        tokens = info.get("tokens", {})
        planner = "LLM" if info.get("planner_llm") else ("快捷" if info.get("planner_shortcut") else "-")
        # 模型调用次数 = agentChain 中的 llmCall 阶段 + planner LLM 调用
        llm_ms = info.get("stages", {}).get("agentChain", {}).get("llmCall", {}).get("ms")
        merged.append({
            **run,
            "chat_ms": chat,
            "agentChain_ms": chain,
            "rag_ms": rag,
            "llmCall_ms": llm_ms,
            "planner": planner,
            "tokens": tokens,
            "stages": info.get("stages", {}),
        })
        print(f"{run['name']:<12}{run['wall_ms']:>9}{str(chat or '-'):>9}{str(chain or '-'):>12}"
              f"{str(rag or '-'):>9}{str(llm_ms or '-'):>10}{planner:>8}"
              f"{str(tokens.get('prompt', '-')):>9}{str(tokens.get('completion', '-')):>11}")

    print("=" * 108)

    # 阶段明细（按 chat 作用域占比排序）
    for m in merged:
        if not m["stages"]:
            continue
        print(f"\n--- {m['name']} ({m['conversationId']}) 阶段明细 ---")
        for scope in ("chat", "agentChain", "ragRetrieve"):
            stages = m["stages"].get(scope)
            if not stages:
                continue
            print(f"  [{scope}]")
            for key, val in sorted(stages.items(), key=lambda kv: -(kv[1]["ms"] or 0)):
                pct = f"{val['pct']:.1f}%" if val["pct"] is not None else "-"
                print(f"    {key:<20} {val['ms']:>9.0f}ms  {pct:>7}")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump(merged, fh, ensure_ascii=False, indent=2)
        print(f"\n结果已写入 {args.out}")


if __name__ == "__main__":
    main()
