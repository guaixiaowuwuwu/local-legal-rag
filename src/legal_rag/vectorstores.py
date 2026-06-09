"""Vector store helpers for Chroma and FAISS."""

from __future__ import annotations

import json
import math
import shutil
from pathlib import Path
from typing import Any, Sequence

from legal_rag.compat import Document, make_document
from legal_rag.config import RAGConfig


def reset_vector_store(config: RAGConfig) -> None:
    if config.persist_dir.exists():
        shutil.rmtree(config.persist_dir)


def create_vector_store(
    documents: Sequence[Document],
    embeddings: Any,
    config: RAGConfig,
) -> Any:
    config.persist_dir.mkdir(parents=True, exist_ok=True)

    if config.vector_store == "local":
        return LocalVectorStore.from_documents(documents, embeddings, config)

    if config.vector_store == "chroma":
        Chroma = _import_chroma()
        store = Chroma.from_documents(
            documents=documents,
            embedding=embeddings,
            persist_directory=str(config.persist_dir),
            collection_name=config.collection_name,
        )
        if hasattr(store, "persist"):
            store.persist()
        return store

    if config.vector_store == "faiss":
        FAISS = _import_faiss()
        store = FAISS.from_documents(documents, embeddings)
        store.save_local(str(config.persist_dir), index_name=config.collection_name)
        return store

    raise ValueError(f"Unsupported vector store: {config.vector_store}")


def load_vector_store(embeddings: Any, config: RAGConfig) -> Any:
    if config.vector_store == "local":
        return LocalVectorStore.load(embeddings, config)

    if config.vector_store == "chroma":
        Chroma = _import_chroma()
        return Chroma(
            persist_directory=str(config.persist_dir),
            collection_name=config.collection_name,
            embedding_function=embeddings,
        )

    if config.vector_store == "faiss":
        FAISS = _import_faiss()
        index_file = config.persist_dir / f"{config.collection_name}.faiss"
        if not index_file.exists():
            fallback = config.persist_dir / "index.faiss"
            index_name = "index" if fallback.exists() else config.collection_name
        else:
            index_name = config.collection_name
        return FAISS.load_local(
            str(config.persist_dir),
            embeddings,
            index_name=index_name,
            allow_dangerous_deserialization=True,
        )

    raise ValueError(f"Unsupported vector store: {config.vector_store}")


class LocalVectorStore:
    """Tiny persisted vector store for dependency-light smoke tests."""

    def __init__(
        self,
        documents: Sequence[Document],
        vectors: Sequence[Sequence[float]],
        embeddings: Any,
        path: Path,
    ) -> None:
        self.documents = list(documents)
        self.vectors = [list(vector) for vector in vectors]
        self.embeddings = embeddings
        self.path = path

    @classmethod
    def from_documents(
        cls,
        documents: Sequence[Document],
        embeddings: Any,
        config: RAGConfig,
    ) -> "LocalVectorStore":
        texts = [document.page_content for document in documents]
        vectors = embeddings.embed_documents(texts)
        store = cls(documents, vectors, embeddings, _local_store_path(config))
        store.persist()
        return store

    @classmethod
    def load(cls, embeddings: Any, config: RAGConfig) -> "LocalVectorStore":
        path = _local_store_path(config)
        if not path.exists():
            return cls([], [], embeddings, path)

        payload = json.loads(path.read_text(encoding="utf-8"))
        documents = [
            make_document(item["page_content"], dict(item.get("metadata") or {}))
            for item in payload.get("documents", [])
        ]
        vectors = payload.get("vectors", [])
        return cls(documents, vectors, embeddings, path)

    def persist(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "documents": [
                {
                    "page_content": document.page_content,
                    "metadata": document.metadata or {},
                }
                for document in self.documents
            ],
            "vectors": self.vectors,
        }
        self.path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2, default=str),
            encoding="utf-8",
        )

    def similarity_search_with_relevance_scores(
        self,
        query: str,
        k: int = 5,
    ) -> list[tuple[Document, float]]:
        query_vector = self.embeddings.embed_query(query)
        scored = [
            (document, max(0.0, _cosine(query_vector, vector)))
            for document, vector in zip(self.documents, self.vectors)
        ]
        scored.sort(key=lambda item: item[1], reverse=True)
        return scored[:k]

    def similarity_search_with_score(self, query: str, k: int = 5) -> list[tuple[Document, float]]:
        return self.similarity_search_with_relevance_scores(query, k)

    def similarity_search(self, query: str, k: int = 5) -> list[Document]:
        return [
            document
            for document, _score in self.similarity_search_with_relevance_scores(query, k)
        ]


def _local_store_path(config: RAGConfig) -> Path:
    return config.persist_dir / f"{config.collection_name}.json"


def _cosine(left: Sequence[float], right: Sequence[float]) -> float:
    if not left or not right:
        return 0.0
    dot = sum(left_value * right_value for left_value, right_value in zip(left, right))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    if not left_norm or not right_norm:
        return 0.0
    return dot / (left_norm * right_norm)


def _import_chroma() -> Any:
    try:
        from langchain_chroma import Chroma
    except ImportError:
        try:
            from langchain_community.vectorstores import Chroma
        except ImportError as exc:  # pragma: no cover - depends on runtime install.
            raise RuntimeError("Install langchain-chroma or langchain-community to use Chroma.") from exc
    return Chroma


def _import_faiss() -> Any:
    try:
        from langchain_community.vectorstores import FAISS
    except ImportError as exc:  # pragma: no cover - depends on runtime install.
        raise RuntimeError("Install langchain-community and faiss-cpu to use FAISS.") from exc
    return FAISS
