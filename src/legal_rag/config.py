"""Configuration helpers for the local legal RAG system."""

from __future__ import annotations

import os
from dataclasses import dataclass, fields, replace
from pathlib import Path
from typing import Any, Dict, Mapping, Optional


def _bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def _env(name: str) -> Optional[str]:
    value = os.getenv(name)
    return value if value not in {"", None} else None


@dataclass(frozen=True)
class RAGConfig:
    docs_dir: Path = Path("data/documents/formal")
    persist_dir: Path = Path("data/vectorstore")
    vector_store: str = "chroma"
    collection_name: str = "legal_rag"

    embedding_model: str = "hash"
    embedding_device: str = "cpu"
    normalize_embeddings: bool = True

    chunk_size: int = 900
    chunk_overlap: int = 120
    top_k: int = 5

    llm_backend: str = "extractive"
    llm_model: str = "THUDM/chatglm3-6b"
    llm_device: str = "auto"
    max_new_tokens: int = 768
    temperature: float = 0.1

    def with_overrides(self, **overrides: Any) -> "RAGConfig":
        allowed = {field.name for field in fields(self)}
        clean = {key: _coerce_value(key, value) for key, value in overrides.items() if key in allowed}
        return replace(self, **clean)

    def resolve_paths(self, base_dir: Optional[Path] = None) -> "RAGConfig":
        base = base_dir or Path.cwd()
        docs_dir = self.docs_dir if self.docs_dir.is_absolute() else base / self.docs_dir
        persist_dir = self.persist_dir if self.persist_dir.is_absolute() else base / self.persist_dir
        return replace(self, docs_dir=docs_dir, persist_dir=persist_dir)


def load_config(path: Optional[Path] = None) -> RAGConfig:
    """Load config from YAML and environment variables.

    Environment variables override YAML values so deployment-specific local model paths can stay
    outside committed config files.
    """

    values: Dict[str, Any] = {}
    if path:
        values.update(_load_yaml(Path(path)))

    config = RAGConfig().with_overrides(**values)
    return config.with_overrides(**_env_overrides())


def _load_yaml(path: Path) -> Mapping[str, Any]:
    try:
        import yaml
    except ImportError as exc:  # pragma: no cover - depends on optional runtime install.
        raise RuntimeError("PyYAML is required to read YAML config files.") from exc

    with path.open("r", encoding="utf-8") as handle:
        data = yaml.safe_load(handle) or {}
    if not isinstance(data, Mapping):
        raise ValueError(f"Config file must contain a mapping: {path}")
    return data


def _env_overrides() -> Dict[str, Any]:
    mapping = {
        "docs_dir": "LEGAL_RAG_DOCS_DIR",
        "persist_dir": "LEGAL_RAG_PERSIST_DIR",
        "vector_store": "LEGAL_RAG_VECTOR_STORE",
        "collection_name": "LEGAL_RAG_COLLECTION_NAME",
        "embedding_model": "LEGAL_RAG_EMBEDDING_MODEL",
        "embedding_device": "LEGAL_RAG_EMBEDDING_DEVICE",
        "normalize_embeddings": "LEGAL_RAG_NORMALIZE_EMBEDDINGS",
        "chunk_size": "LEGAL_RAG_CHUNK_SIZE",
        "chunk_overlap": "LEGAL_RAG_CHUNK_OVERLAP",
        "top_k": "LEGAL_RAG_TOP_K",
        "llm_backend": "LEGAL_RAG_LLM_BACKEND",
        "llm_model": "LEGAL_RAG_LLM_MODEL",
        "llm_device": "LEGAL_RAG_LLM_DEVICE",
        "max_new_tokens": "LEGAL_RAG_MAX_NEW_TOKENS",
        "temperature": "LEGAL_RAG_TEMPERATURE",
    }
    aliases = {"llm_model": "CHATGLM_MODEL"}
    overrides: Dict[str, Any] = {}
    for field_name, env_name in mapping.items():
        value = _env(env_name)
        if value is None and field_name in aliases:
            value = _env(aliases[field_name])
        if value is not None:
            overrides[field_name] = value
    return overrides


def _coerce_value(field_name: str, value: Any) -> Any:
    if value is None:
        return value
    if field_name in {"docs_dir", "persist_dir"}:
        return Path(value)
    if field_name in {"chunk_size", "chunk_overlap", "top_k", "max_new_tokens"}:
        return int(value)
    if field_name in {"temperature"}:
        return float(value)
    if field_name in {"normalize_embeddings"}:
        return _bool(value)
    if field_name in {"vector_store", "llm_backend"}:
        return str(value).strip().lower()
    return value
