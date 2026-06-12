#!/usr/bin/env python3
"""Evaluate labor-law RAG examples against a running backend HTTP API."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_EXAMPLES = Path(__file__).with_name("labor_qa_examples.json")
DEFAULT_OUTPUT = Path(__file__).with_name("reports") / "labor_rag_evaluation.md"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate labor-law RAG retrieval and answer coverage via the backend HTTP API."
    )
    parser.add_argument(
        "--base-url",
        default="http://localhost:8080",
        help="Backend base URL. Default: http://localhost:8080",
    )
    parser.add_argument(
        "--knowledge-base-id",
        required=True,
        help="Knowledge base UUID that has ingested the labor-law formal documents.",
    )
    parser.add_argument(
        "--examples",
        type=Path,
        default=DEFAULT_EXAMPLES,
        help=f"Evaluation examples JSON path. Default: {DEFAULT_EXAMPLES}",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=None,
        help="Override request topK. Omit to use backend LEGAL_RAG_TOP_K.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Markdown report path. Use '-' to print to stdout. Default: {DEFAULT_OUTPUT}",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=60.0,
        help="HTTP request timeout in seconds. Default: 60.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    examples_file = load_examples(args.examples)
    results = [
        evaluate_example(args, example)
        for example in examples_file.get("examples", [])
    ]
    report = build_report(args, examples_file, results)
    write_report(args.output, report)

    passed = sum(1 for result in results if result["passed"])
    total = len(results)
    pass_rate = percentage(passed, total)
    print(f"评测完成：{passed}/{total} 通过，通过率 {pass_rate}")
    print(f"报告：{args.output}")
    return 0 if passed == total else 1


def load_examples(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as input_file:
        return json.load(input_file)


def evaluate_example(args: argparse.Namespace, example: dict[str, Any]) -> dict[str, Any]:
    try:
        response = ask_backend(
            base_url=args.base_url,
            knowledge_base_id=args.knowledge_base_id,
            question=example["question"],
            top_k=args.top_k,
            timeout=args.timeout,
        )
        error = None
    except EvaluationError as exc:
        response = {"answer": "", "sources": [], "retrievedChunks": []}
        error = str(exc)

    expected_sources = example.get("expected_sources", [])
    expected_terms = example.get("expected_terms", [])
    sources = response.get("sources") or []
    retrieved_chunks = response.get("retrievedChunks") or []
    answer = response.get("answer") or ""

    source_hits = [
        {
            "expected": expected,
            "hit": expected_source_hit(expected, sources),
        }
        for expected in expected_sources
    ]
    search_text = "\n".join(
        [answer] + [str(chunk.get("content") or "") for chunk in retrieved_chunks]
    )
    term_hits = [
        {
            "expected": term,
            "hit": term in search_text,
        }
        for term in expected_terms
    ]
    passed = error is None and all(item["hit"] for item in source_hits + term_hits)

    return {
        "id": example["id"],
        "question": example["question"],
        "answer": answer,
        "sources": sources,
        "retrieved_chunks": retrieved_chunks,
        "source_hits": source_hits,
        "term_hits": term_hits,
        "passed": passed,
        "error": error,
    }


def ask_backend(
    base_url: str,
    knowledge_base_id: str,
    question: str,
    top_k: int | None,
    timeout: float,
) -> dict[str, Any]:
    safe_base_url = base_url.rstrip("/")
    safe_knowledge_base_id = urllib.parse.quote(knowledge_base_id)
    url = f"{safe_base_url}/api/knowledge-bases/{safe_knowledge_base_id}/questions"
    payload: dict[str, Any] = {"question": question}
    if top_k is not None:
        payload["topK"] = top_k

    request = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise EvaluationError(f"HTTP {exc.code}: {body}") from exc
    except urllib.error.URLError as exc:
        raise EvaluationError(f"请求失败：{exc.reason}") from exc
    except json.JSONDecodeError as exc:
        raise EvaluationError(f"响应不是合法 JSON：{exc}") from exc


def expected_source_hit(expected: str, sources: list[dict[str, Any]]) -> bool:
    return any(
        source_matches(expected, candidate)
        for source in sources
        for candidate in source_candidates(source)
    )


def source_candidates(source: dict[str, Any]) -> list[str]:
    values = [
        source.get("sourcePath"),
        source.get("source"),
        source.get("label"),
    ]
    return [str(value) for value in values if value]


def source_matches(expected: str, actual: str) -> bool:
    expected_norm = normalize_path(expected)
    actual_norm = normalize_path(actual)
    expected_name = expected_norm.rsplit("/", 1)[-1]
    actual_name = actual_norm.rsplit("/", 1)[-1]
    return (
        actual_norm.endswith(expected_norm)
        or expected_norm.endswith(actual_norm)
        or expected_name == actual_name
        or actual_name.endswith("-" + expected_name)
    )


def normalize_path(value: str) -> str:
    return value.replace("\\", "/").strip()


def build_report(args: argparse.Namespace, examples_file: dict[str, Any], results: list[dict[str, Any]]) -> str:
    passed = sum(1 for result in results if result["passed"])
    total = len(results)
    lines = [
        "# Labor RAG Evaluation Report",
        "",
        f"- Generated At: {dt.datetime.now(dt.timezone.utc).isoformat()}",
        f"- Backend: `{args.base_url.rstrip('/')}`",
        f"- Knowledge Base ID: `{args.knowledge_base_id}`",
        f"- Examples: `{args.examples}`",
        f"- Corpus Root: `{examples_file.get('corpus_root', '')}`",
        f"- Top-K: `{args.top_k if args.top_k is not None else 'backend default'}`",
        f"- Overall Pass Rate: **{percentage(passed, total)}** ({passed}/{total})",
        "",
        "## Summary",
        "",
        "| Question ID | Source Hits | Keyword Hits | Result |",
        "| --- | ---: | ---: | --- |",
    ]

    for result in results:
        source_passed = sum(1 for item in result["source_hits"] if item["hit"])
        term_passed = sum(1 for item in result["term_hits"] if item["hit"])
        lines.append(
            "| {id} | {source_passed}/{source_total} | {term_passed}/{term_total} | {status} |".format(
                id=md(result["id"]),
                source_passed=source_passed,
                source_total=len(result["source_hits"]),
                term_passed=term_passed,
                term_total=len(result["term_hits"]),
                status="PASS" if result["passed"] else "FAIL",
            )
        )

    for result in results:
        lines.extend(detail_lines(result))

    return "\n".join(lines) + "\n"


def detail_lines(result: dict[str, Any]) -> list[str]:
    lines = [
        "",
        f"## {result['id']}",
        "",
        f"**Question:** {md(result['question'])}",
        "",
        f"**Result:** {'PASS' if result['passed'] else 'FAIL'}",
    ]
    if result["error"]:
        lines.extend(["", f"**Error:** `{md(result['error'])}`"])

    lines.extend([
        "",
        "### Top-K Recalled Sources",
        "",
        "| Rank | Source ID | Source | Article | Score |",
        "| ---: | --- | --- | --- | ---: |",
    ])
    if result["sources"]:
        for index, source in enumerate(result["sources"], start=1):
            lines.append(
                "| {rank} | {source_id} | {source} | {article} | {score} |".format(
                    rank=index,
                    source_id=md(source.get("id", "")),
                    source=md(source.get("sourcePath") or source.get("source") or ""),
                    article=md(source.get("article") or ""),
                    score=format_score(source.get("score")),
                )
            )
    else:
        lines.append("| - | - | - | - | - |")

    lines.extend([
        "",
        "### Expected Source Hits",
        "",
    ])
    lines.extend(hit_lines(result["source_hits"]))
    lines.extend([
        "",
        "### Expected Keyword Hits",
        "",
    ])
    lines.extend(hit_lines(result["term_hits"]))
    return lines


def hit_lines(items: list[dict[str, Any]]) -> list[str]:
    if not items:
        return ["- No expectations configured."]
    return [
        f"- [{'x' if item['hit'] else ' '}] {md(item['expected'])}"
        for item in items
    ]


def write_report(output: Path | str, content: str) -> None:
    if str(output) == "-":
        print(content)
        return
    path = Path(output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def percentage(value: int, total: int) -> str:
    if total == 0:
        return "0.00%"
    return f"{value / total * 100:.2f}%"


def format_score(value: Any) -> str:
    try:
        return f"{float(value):.4f}"
    except (TypeError, ValueError):
        return ""


def md(value: Any) -> str:
    return str(value).replace("\n", " ").replace("|", "\\|")


class EvaluationError(Exception):
    pass


if __name__ == "__main__":
    sys.exit(main())
