"""Embedding factory."""

from __future__ import annotations

import hashlib
import math
import re
from typing import Any

from legal_rag.config import RAGConfig

try:  # pragma: no cover - depends on optional LangChain install.
    from langchain_core.embeddings import Embeddings as BaseEmbeddings
except Exception:  # pragma: no cover - covered in dependency-light tests.
    BaseEmbeddings = object

_HASH_EMBEDDING_NAMES = {"hash", "local-hash", "debug-hash"}
_TOKEN_RE = re.compile(r"[a-z0-9]+|[\u4e00-\u9fff]", flags=re.I)


class HashingEmbeddings(BaseEmbeddings):
    """Small deterministic embedding backend for local smoke tests."""

    def __init__(self, dimension: int = 2048, normalize: bool = True) -> None:
        self.dimension = dimension
        self.normalize = normalize

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        return self._embed(text)

    def __call__(self, text: str) -> list[float]:
        return self.embed_query(text)

    def _embed(self, text: str) -> list[float]:
        vector = [0.0] * self.dimension
        for token in _tokens(text):
            digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
            value = int.from_bytes(digest, byteorder="big", signed=False)
            index = value % self.dimension
            vector[index] += 1.0

        if self.normalize:
            norm = math.sqrt(sum(value * value for value in vector))
            if norm:
                vector = [value / norm for value in vector]
        return vector


def build_embeddings(config: RAGConfig) -> Any:
    if config.embedding_model.strip().lower() in _HASH_EMBEDDING_NAMES:
        return HashingEmbeddings(normalize=config.normalize_embeddings)

    try:
        from langchain_huggingface import HuggingFaceEmbeddings
    except ImportError:
        try:
            from langchain_community.embeddings import HuggingFaceEmbeddings
        except ImportError as exc:  # pragma: no cover - depends on runtime install.
            raise RuntimeError(
                "Install langchain-huggingface and sentence-transformers to use local embeddings."
            ) from exc

    try:
        return HuggingFaceEmbeddings(
            model_name=config.embedding_model,
            model_kwargs={"device": config.embedding_device},
            encode_kwargs={"normalize_embeddings": config.normalize_embeddings},
        )
    except ImportError as exc:
        raise RuntimeError(
            "Embedding 模型依赖缺失：当前配置需要 sentence-transformers/"
            "langchain-huggingface。轻量演示请使用 --embedding-model hash；"
            "真实模型请安装完整依赖并确认模型可访问。"
        ) from exc
    except Exception as exc:
        raise RuntimeError(
            f"初始化 Embedding 模型失败：{config.embedding_model}。"
            "请检查模型名称、本地路径、网络和设备配置。"
        ) from exc


def _tokens(text: str) -> list[str]:
    tokens = [match.group(0).lower() for match in _TOKEN_RE.finditer(text)]
    if not tokens:
        return []

    features = list(tokens)
    for window in (2, 3, 4):
        features.extend(
            "".join(tokens[index : index + window])
            for index in range(len(tokens) - window + 1)
        )
    return features
