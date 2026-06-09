"""Local private legal RAG package."""

from legal_rag.config import RAGConfig, load_config
from legal_rag.service import LegalRAGService

__all__ = ["RAGConfig", "LegalRAGService", "load_config"]

