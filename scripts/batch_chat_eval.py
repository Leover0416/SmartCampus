#!/usr/bin/env python3
"""
SmartCampus 批量对话评测脚本（无需前端）

用法:
  cd campus
  python3 scripts/batch_chat_eval.py
  python3 scripts/batch_chat_eval.py --questions scripts/questions-batch.json --output reports/run1.json
  python3 scripts/batch_chat_eval.py --base-url http://localhost:8080 --delay 2

依赖: Python 3.9+ 标准库即可

测完后生成看板指标:
  mvn test -Dtest=EvalMetricsReportTest
"""

from __future__ import annotations

import argparse
import csv
import json
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


@dataclass
class QuestionCase:
    id: str
    query: str
    type: str = "RAG"
    expect_contains: str | None = None
    expect_doc_keyword: str | None = None

    @staticmethod
    def from_json(obj: dict[str, Any]) -> QuestionCase:
        return QuestionCase(
            id=str(obj.get("id", obj.get("query", "")[:20])),
            query=str(obj["query"]),
            type=str(obj.get("type", "RAG")).upper(),
            expect_contains=obj.get("expectContains"),
            expect_doc_keyword=obj.get("expectDocKeyword"),
        )


@dataclass
class RunResult:
    id: str
    query: str
    type: str
    ok: bool
    latency_ms: int
    intent: str | None = None
    answer: str = ""
    source_count: int = 0
    source_titles: list[str] = field(default_factory=list)
    error: str | None = None
    pass_check: bool = False
    pass_reason: str = ""


def http_json(
    method: str,
    url: str,
    body: dict | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 30,
) -> dict[str, Any]:
    data = None
    req_headers = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        req_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=req_headers)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 200:
        raise RuntimeError(f"API error {payload.get('code')}: {payload.get('message')}")
    return payload.get("data") or {}


def login(base_url: str, username: str, password: str) -> str:
    data = http_json(
        "POST",
        f"{base_url}/api/v1/auth/login",
        {"username": username, "password": password},
        timeout=30,
    )
    token = data.get("token")
    if not token:
        raise RuntimeError("登录成功但未返回 token")
    return token


def parse_sse_stream(raw: str) -> tuple[str, dict[str, Any]]:
    """Parse SSE text into full answer and done payload."""
    event_name = None
    answer_parts: list[str] = []
    done_payload: dict[str, Any] = {}

    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("event:"):
            event_name = line[6:].strip()
        elif line.startswith("data:"):
            data_str = line[5:].strip()
            if not data_str:
                continue
            try:
                data = json.loads(data_str)
            except json.JSONDecodeError:
                continue
            if event_name == "token":
                token_piece = data.get("token", "")
                if token_piece:
                    answer_parts.append(token_piece)
            elif event_name == "done":
                done_payload = data
            elif event_name == "error":
                raise RuntimeError(data.get("message", "SSE error event"))

    return "".join(answer_parts), done_payload


def chat_stream(
    base_url: str,
    token: str,
    query: str,
    session_id: str | None,
    timeout: int,
) -> tuple[str, dict[str, Any], int]:
    params: dict[str, str] = {"query": query, "token": token}
    if session_id:
        params["sessionId"] = session_id
    url = f"{base_url}/api/v1/chat/stream?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Accept": "text/event-stream"})
    started = time.perf_counter()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
    latency_ms = int((time.perf_counter() - started) * 1000)
    answer, done = parse_sse_stream(raw)
    return answer, done, latency_ms


def evaluate_case(case: QuestionCase, answer: str, done: dict[str, Any], latency_ms: int) -> tuple[bool, str]:
    refs = done.get("sourceRefs") or []
    titles = [str(r.get("docTitle", "")) for r in refs if isinstance(r, dict)]

    if case.type == "FAQ":
        needle = case.expect_contains or ""
        if needle and needle in answer:
            return True, f"答案包含「{needle}」"
        if latency_ms <= 3000 and not refs:
            return True, f"疑似 FAQ 短路（{latency_ms}ms，无来源引用）"
        return False, f"答案未包含「{needle}」且延迟 {latency_ms}ms"

    keyword = case.expect_doc_keyword or ""
    if keyword:
        haystack = answer + " " + " ".join(titles)
        if keyword in haystack:
            return True, f"答案或来源含「{keyword}」"
        if refs:
            return False, f"有 {len(refs)} 条来源但未命中关键词「{keyword}」"
        return False, "无知识来源引用"
    return bool(answer.strip()), "仅检查有回答" if answer.strip() else "空回答"


def load_questions(path: Path) -> list[QuestionCase]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("问题文件必须是 JSON 数组")
    return [QuestionCase.from_json(item) for item in data]


def percentile(values: list[int], p: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    idx = max(0, min(len(ordered) - 1, int(round(p * len(ordered))) - 1))
    return ordered[idx]


def summarize(results: list[RunResult]) -> dict[str, Any]:
    latencies = [r.latency_ms for r in results if r.ok]
    faq = [r for r in results if r.type == "FAQ"]
    rag = [r for r in results if r.type == "RAG"]
    return {
        "total": len(results),
        "success": sum(1 for r in results if r.ok),
        "failed": sum(1 for r in results if not r.ok),
        "pass": sum(1 for r in results if r.pass_check),
        "pass_rate_pct": round(100 * sum(1 for r in results if r.pass_check) / len(results), 1) if results else 0,
        "latency_ms": {
            "avg": int(statistics.mean(latencies)) if latencies else 0,
            "p50": percentile(latencies, 0.5),
            "p95": percentile(latencies, 0.95),
            "max": max(latencies) if latencies else 0,
        },
        "faq": {
            "count": len(faq),
            "pass": sum(1 for r in faq if r.pass_check),
            "avg_ms": int(statistics.mean([r.latency_ms for r in faq if r.ok])) if faq else 0,
        },
        "rag": {
            "count": len(rag),
            "pass": sum(1 for r in rag if r.pass_check),
            "avg_ms": int(statistics.mean([r.latency_ms for r in rag if r.ok])) if rag else 0,
        },
    }


def write_csv(path: Path, results: list[RunResult]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "id", "type", "query", "ok", "pass", "pass_reason",
                "latency_ms", "intent", "source_count", "source_titles", "answer_preview", "error",
            ],
        )
        writer.writeheader()
        for r in results:
            writer.writerow({
                "id": r.id,
                "type": r.type,
                "query": r.query,
                "ok": r.ok,
                "pass": r.pass_check,
                "pass_reason": r.pass_reason,
                "latency_ms": r.latency_ms,
                "intent": r.intent or "",
                "source_count": r.source_count,
                "source_titles": "|".join(r.source_titles),
                "answer_preview": r.answer[:200].replace("\n", " "),
                "error": r.error or "",
            })


def main() -> int:
    parser = argparse.ArgumentParser(description="SmartCampus 批量 SSE 对话评测")
    parser.add_argument("--base-url", default="http://localhost:8080", help="后端地址")
    parser.add_argument("--username", default="student001")
    parser.add_argument("--password", default="student123")
    parser.add_argument(
        "--questions",
        default=str(Path(__file__).parent / "questions-batch.json"),
        help="问题 JSON 文件路径",
    )
    parser.add_argument("--output", default="", help="结果 JSON 输出路径（默认 reports/batch-时间戳.json）")
    parser.add_argument("--csv", default="", help="可选 CSV 输出路径")
    parser.add_argument("--session-id", default="", help="固定会话 ID（测多轮时传入）")
    parser.add_argument("--new-session-each", action="store_true", help="每条问题使用新会话（默认共用一会话）")
    parser.add_argument("--delay", type=float, default=1.0, help="问题间隔秒数")
    parser.add_argument("--timeout", type=int, default=180, help="单条 SSE 超时秒数")
    parser.add_argument("--limit", type=int, default=0, help="只跑前 N 条（0=全部）")
    args = parser.parse_args()

    questions_path = Path(args.questions)
    if not questions_path.exists():
        print(f"问题文件不存在: {questions_path}", file=sys.stderr)
        return 1

    cases = load_questions(questions_path)
    if args.limit > 0:
        cases = cases[: args.limit]

    print(f"登录 {args.base_url} 用户={args.username} ...")
    try:
        token = login(args.base_url.rstrip("/"), args.username, args.password)
    except (urllib.error.URLError, RuntimeError) as e:
        print(f"登录失败: {e}", file=sys.stderr)
        return 1

    session_id = args.session_id or None
    results: list[RunResult] = []

    print(f"共 {len(cases)} 条问题，delay={args.delay}s\n")
    for i, case in enumerate(cases, 1):
        if args.new_session_each:
            session_id = None
        print(f"[{i}/{len(cases)}] {case.id} ({case.type}) {case.query[:40]}...")
        result = RunResult(id=case.id, query=case.query, type=case.type, ok=False, latency_ms=0)
        try:
            answer, done, latency_ms = chat_stream(
                args.base_url.rstrip("/"), token, case.query, session_id, args.timeout
            )
            result.ok = True
            result.latency_ms = latency_ms
            result.answer = answer
            result.intent = done.get("intent")
            refs = done.get("sourceRefs") or []
            result.source_count = len(refs)
            result.source_titles = [
                str(r.get("docTitle", "")) for r in refs if isinstance(r, dict) and r.get("docTitle")
            ]
            if not session_id and done.get("sessionId"):
                session_id = done["sessionId"]
            passed, reason = evaluate_case(case, answer, done, latency_ms)
            result.pass_check = passed
            result.pass_reason = reason
            status = "PASS" if passed else "FAIL"
            print(f"    -> {status} {latency_ms}ms intent={result.intent} sources={result.source_count}")
        except Exception as e:
            result.error = str(e)
            print(f"    -> ERROR {e}")
        results.append(result)
        if i < len(cases) and args.delay > 0:
            time.sleep(args.delay)

    summary = summarize(results)
    report = {
        "run_at": datetime.now(timezone.utc).isoformat(),
        "base_url": args.base_url,
        "username": args.username,
        "questions_file": str(questions_path),
        "session_id": session_id,
        "summary": summary,
        "results": [asdict(r) for r in results],
    }

    out_path = Path(args.output) if args.output else Path("reports") / f"batch-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    csv_path = Path(args.csv) if args.csv else out_path.with_suffix(".csv")
    write_csv(csv_path, results)

    print("\n========== 汇总 ==========")
    print(f"成功/总数: {summary['success']}/{summary['total']}")
    print(f"通过/总数: {summary['pass']}/{summary['total']} ({summary['pass_rate_pct']}%)")
    lat = summary["latency_ms"]
    print(f"延迟 ms: avg={lat['avg']} p50={lat['p50']} p95={lat['p95']} max={lat['max']}")
    print(f"FAQ: {summary['faq']['pass']}/{summary['faq']['count']} 平均 {summary['faq']['avg_ms']}ms")
    print(f"RAG: {summary['rag']['pass']}/{summary['rag']['count']} 平均 {summary['rag']['avg_ms']}ms")
    print(f"\n结果已写入:\n  JSON: {out_path}\n  CSV:  {csv_path}")
    print("\n下一步（看板指标）:\n  cd campus && mvn test -Dtest=EvalMetricsReportTest")
    return 0 if summary["failed"] == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
