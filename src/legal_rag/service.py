"""End-to-end RAG service."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

from legal_rag.compat import Document
from legal_rag.config import RAGConfig
from legal_rag.embeddings import build_embeddings
from legal_rag.llm import AnswerGenerator, build_llm
from legal_rag.loaders import load_local_documents
from legal_rag.prompting import NO_CONTEXT_MESSAGE, build_legal_prompt, extract_source_metadata
from legal_rag.splitter import split_documents
from legal_rag.vectorstores import create_vector_store, load_vector_store, reset_vector_store


@dataclass
class RAGAnswer:
    question: str
    answer: str
    sources: List[Dict[str, Any]]
    retrieved_documents: List[Document]


class LegalRAGService:
    def __init__(
        self,
        config: RAGConfig,
        vector_store: Optional[Any] = None,
        llm: Optional[AnswerGenerator] = None,
    ) -> None:
        self.config = config
        self.vector_store = vector_store
        self.llm = llm
        self._embeddings = None

    def ingest(self, reset: bool = False) -> Dict[str, int]:
        if reset:
            reset_vector_store(self.config)

        raw_documents = load_local_documents(self.config.docs_dir)
        chunks = split_documents(
            raw_documents,
            chunk_size=self.config.chunk_size,
            chunk_overlap=self.config.chunk_overlap,
        )
        if not chunks:
            raise RuntimeError(f"No documents were loaded from {self.config.docs_dir}")

        self.vector_store = create_vector_store(chunks, self._get_embeddings(), self.config)
        return {"documents": len(raw_documents), "chunks": len(chunks)}

    def ask(self, question: str) -> RAGAnswer:
        retrieved = self.retrieve(question)
        if not retrieved:
            return RAGAnswer(
                question=question,
                answer=f"结论：{NO_CONTEXT_MESSAGE}\n依据：无。\n来源：无。",
                sources=[],
                retrieved_documents=[],
            )

        prompt = build_legal_prompt(question, retrieved)
        llm = self.llm or build_llm(self.config)
        answer = llm.generate(prompt)
        return RAGAnswer(
            question=question,
            answer=answer,
            sources=extract_source_metadata(retrieved),
            retrieved_documents=retrieved,
        )

    def retrieve(self, question: str) -> List[Document]:
        store = self._get_vector_store()
        if hasattr(store, "similarity_search_with_relevance_scores"):
            try:
                results = store.similarity_search_with_relevance_scores(question, k=self.config.top_k)
                return [_attach_score(doc, score) for doc, score in results]
            except Exception:
                pass

        if hasattr(store, "similarity_search_with_score"):
            results = store.similarity_search_with_score(question, k=self.config.top_k)
            return [_attach_score(doc, score) for doc, score in results]

        return store.similarity_search(question, k=self.config.top_k)

    def _get_embeddings(self) -> Any:
        if self._embeddings is None:
            self._embeddings = build_embeddings(self.config)
        return self._embeddings

    def _get_vector_store(self) -> Any:
        if self.vector_store is None:
            self.vector_store = load_vector_store(self._get_embeddings(), self.config)
        return self.vector_store


def _attach_score(document: Document, score: Any) -> Document:
    try:
        numeric_score = float(score)
    except (TypeError, ValueError):
        numeric_score = score
    document.metadata = dict(document.metadata or {})
    document.metadata["score"] = numeric_score
    return document


def service_from_config(config: RAGConfig, base_dir: Optional[Path] = None) -> LegalRAGService:
    return LegalRAGService(config.resolve_paths(base_dir))

