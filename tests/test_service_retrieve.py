import tempfile
import unittest
from pathlib import Path

from legal_rag.compat import make_document
from legal_rag.config import RAGConfig
from legal_rag.service import LegalRAGService


class RelevanceStore:
    def similarity_search_with_relevance_scores(self, query, k=5):
        return [(make_document(f"{query} answer", {"source": "sample.md"}), 0.75)]


class ScoreFallbackStore:
    def similarity_search_with_relevance_scores(self, query, k=5):
        raise NotImplementedError("relevance scores are not available")

    def similarity_search_with_score(self, query, k=5):
        return [(make_document("fallback answer", {"source": "fallback.md"}), 3.5)]


class BrokenRelevanceStore:
    def similarity_search_with_relevance_scores(self, query, k=5):
        raise RuntimeError("boom")

    def similarity_search_with_score(self, query, k=5):
        return [(make_document("should not hide the failure", {}), 1.0)]


class RetrieveTest(unittest.TestCase):
    def test_retrieve_attaches_relevance_score(self):
        service = LegalRAGService(RAGConfig(top_k=1), vector_store=RelevanceStore())

        results = service.retrieve("试用期工资")

        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].metadata["score"], 0.75)

    def test_retrieve_falls_back_when_relevance_scores_are_not_implemented(self):
        service = LegalRAGService(
            RAGConfig(top_k=1, vector_store="local"),
            vector_store=ScoreFallbackStore(),
        )

        results = service.retrieve("试用期工资")

        self.assertEqual(results[0].metadata["source"], "fallback.md")
        self.assertEqual(results[0].metadata["score"], 3.5)

    def test_retrieve_does_not_swallow_real_failures(self):
        service = LegalRAGService(
            RAGConfig(top_k=1, vector_store="local"),
            vector_store=BrokenRelevanceStore(),
        )

        with self.assertRaisesRegex(RuntimeError, "检索失败.*boom"):
            service.retrieve("试用期工资")

    def test_ask_reports_missing_vector_store(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            config = RAGConfig(
                persist_dir=Path(tmpdir) / "missing-store",
                vector_store="local",
                embedding_model="hash",
                llm_backend="extractive",
            )
            service = LegalRAGService(config)

            with self.assertRaisesRegex(RuntimeError, "向量库不存在或未完成建库"):
                service.ask("试用期工资有什么要求？")


if __name__ == "__main__":
    unittest.main()
