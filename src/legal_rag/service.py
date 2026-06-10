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
        if not raw_documents:
            raise RuntimeError(
                f"文档目录未加载到任何内容：{self.config.docs_dir}。"
                "请确认文件不是空文件，并使用支持的格式。"
            )
        chunks = split_documents(
            raw_documents,
            chunk_size=self.config.chunk_size,
            chunk_overlap=self.config.chunk_overlap,
        )
        if not chunks:
            raise RuntimeError(
                f"文档已读取但没有可入库文本：{self.config.docs_dir}。"
                "请检查文件内容是否为空，或 PDF/Word 是否可正确解析。"
            )

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
        if not question.strip():
            raise ValueError("检索问题不能为空。")

        store = self._get_vector_store()
        if self.config.vector_store in {"chroma", "faiss"} and hasattr(
            store,
            "similarity_search_with_score",
        ):
            try:
                results = store.similarity_search_with_score(question, k=self.config.top_k)
            except Exception as exc:
                raise RuntimeError(
                    f"检索失败：向量库 {self.config.vector_store} 的打分检索出错。"
                    f"原因：{exc}"
                ) from exc
            return [_attach_score(doc, score) for doc, score in results]

        if hasattr(store, "similarity_search_with_relevance_scores"):
            try:
                results = store.similarity_search_with_relevance_scores(question, k=self.config.top_k)
                return [_attach_score(doc, score) for doc, score in results]
            except NotImplementedError:
                pass
            except Exception as exc:
                raise RuntimeError(
                    f"检索失败：向量库 {self.config.vector_store} 的相关度检索出错。"
                    f"原因：{exc}"
                ) from exc

        if hasattr(store, "similarity_search_with_score"):
            try:
                results = store.similarity_search_with_score(question, k=self.config.top_k)
            except Exception as exc:
                raise RuntimeError(
                    f"检索失败：向量库 {self.config.vector_store} 的打分检索出错。"
                    f"原因：{exc}"
                ) from exc
            return [_attach_score(doc, score) for doc, score in results]

        if hasattr(store, "similarity_search"):
            try:
                return store.similarity_search(question, k=self.config.top_k)
            except Exception as exc:
                raise RuntimeError(
                    f"检索失败：向量库 {self.config.vector_store} 的相似度检索出错。"
                    f"原因：{exc}"
                ) from exc

        raise RuntimeError("检索失败：当前向量库对象不支持 similarity_search 接口。")

    def _get_embeddings(self) -> Any:
        if self._embeddings is None:
            self._embeddings = build_embeddings(self.config)
        return self._embeddings

    def _get_vector_store(self) -> Any:
        if self.vector_store is None:
            try:
                self.vector_store = load_vector_store(self._get_embeddings(), self.config)
            except RuntimeError:
                raise
            except Exception as exc:
                raise RuntimeError(
                    f"加载向量库失败：{self.config.vector_store} -> {self.config.persist_dir}。"
                    "请确认已经成功建库，或先运行 legal-rag ingest --reset。"
                ) from exc
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
