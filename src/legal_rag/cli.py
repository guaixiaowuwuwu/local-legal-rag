"""Command-line interface for the legal RAG system."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Dict, Optional

from legal_rag.config import RAGConfig, load_config
from legal_rag.service import LegalRAGService


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(prog="legal-rag")
    parser.add_argument("--config", type=Path, help="Path to YAML config.")

    subparsers = parser.add_subparsers(dest="command", required=True)

    ingest_parser = subparsers.add_parser("ingest", help="Build or rebuild the local vector DB.")
    _add_common_overrides(ingest_parser)
    ingest_parser.add_argument("--reset", action="store_true", help="Delete the existing vector DB first.")

    ask_parser = subparsers.add_parser("ask", help="Ask a question against the local knowledge base.")
    _add_common_overrides(ask_parser)
    ask_parser.add_argument("question", help="Legal question to answer.")

    smoke_parser = subparsers.add_parser("smoke", help="Run a dependency-light ingest and ask flow.")
    _add_common_overrides(smoke_parser)
    smoke_parser.add_argument("--question", default="试用期工资有什么要求？")
    smoke_parser.add_argument("--no-reset", action="store_true", help="Reuse the existing local index.")

    args = parser.parse_args(argv)
    config = _config_from_args(args)
    if args.command == "smoke":
        config = _with_smoke_defaults(config, args)
    service = LegalRAGService(config)

    if args.command == "ingest":
        stats = service.ingest(reset=args.reset)
        print(f"入库完成：原始文档 {stats['documents']} 份，切分片段 {stats['chunks']} 条。")
        print(f"向量库：{config.vector_store} -> {config.persist_dir}")
        return 0

    if args.command == "ask":
        result = service.ask(args.question)
        _print_answer(result)
        return 0

    if args.command == "smoke":
        stats = service.ingest(reset=not args.no_reset)
        print(f"入库完成：原始文档 {stats['documents']} 份，切分片段 {stats['chunks']} 条。")
        print(f"向量库：{config.vector_store} -> {config.persist_dir}")
        result = service.ask(args.question)
        _print_answer(result)
        return 0

    parser.error(f"Unknown command: {args.command}")
    return 2


def _add_common_overrides(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--docs-dir", type=Path)
    parser.add_argument("--persist-dir", type=Path)
    parser.add_argument("--vector-store", choices=["chroma", "faiss", "local"])
    parser.add_argument("--collection-name")
    parser.add_argument("--embedding-model")
    parser.add_argument("--embedding-device")
    parser.add_argument("--chunk-size", type=int)
    parser.add_argument("--chunk-overlap", type=int)
    parser.add_argument("--top-k", type=int)
    parser.add_argument("--llm-backend", choices=["chatglm", "extractive"])
    parser.add_argument("--llm-model")
    parser.add_argument("--llm-device")
    parser.add_argument("--max-new-tokens", type=int)
    parser.add_argument("--temperature", type=float)


def _config_from_args(args: argparse.Namespace) -> RAGConfig:
    config = load_config(args.config)
    overrides: Dict[str, Any] = {}
    for field_name in (
        "docs_dir",
        "persist_dir",
        "vector_store",
        "collection_name",
        "embedding_model",
        "embedding_device",
        "chunk_size",
        "chunk_overlap",
        "top_k",
        "llm_backend",
        "llm_model",
        "llm_device",
        "max_new_tokens",
        "temperature",
    ):
        value = getattr(args, field_name, None)
        if value is not None:
            overrides[field_name] = value
    return config.with_overrides(**overrides).resolve_paths(Path.cwd())


def _with_smoke_defaults(config: RAGConfig, args: argparse.Namespace) -> RAGConfig:
    overrides: Dict[str, Any] = {}
    if args.vector_store is None:
        overrides["vector_store"] = "local"
    if args.embedding_model is None:
        overrides["embedding_model"] = "hash"
    if args.llm_backend is None:
        overrides["llm_backend"] = "extractive"
    return config.with_overrides(**overrides)


def _print_answer(result: Any) -> None:
    print(result.answer)
    if result.sources:
        print("\n来源：")
        for source in result.sources:
            print(f"- {source['id']}: {source['label']}")


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
