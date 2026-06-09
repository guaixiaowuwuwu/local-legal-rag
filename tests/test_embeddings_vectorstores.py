import tempfile
import unittest
from pathlib import Path

from legal_rag.compat import make_document
from legal_rag.config import RAGConfig
from legal_rag.embeddings import build_embeddings
from legal_rag.vectorstores import create_vector_store, load_vector_store


class LocalVectorStoreTest(unittest.TestCase):
    def test_hash_embeddings_and_local_store_retrieve_matching_document(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            config = RAGConfig(
                persist_dir=Path(tmpdir),
                vector_store="local",
                collection_name="test_legal_rag",
                embedding_model="hash",
            )
            embeddings = build_embeddings(config)
            documents = [
                make_document(
                    "第四条 试用期工资不得低于劳动合同约定工资的百分之八十。",
                    {"source": "sample.md", "article": "第四条"},
                ),
                make_document(
                    "第五条 劳动合同、续签协议、岗位调整通知等材料应当归档。",
                    {"source": "sample.md", "article": "第五条"},
                ),
            ]

            create_vector_store(documents, embeddings, config)
            loaded = load_vector_store(embeddings, config)
            results = loaded.similarity_search_with_relevance_scores("试用期工资有什么要求？", k=1)

            self.assertEqual(len(results), 1)
            self.assertIn("试用期工资", results[0][0].page_content)
            self.assertGreater(results[0][1], 0)


if __name__ == "__main__":
    unittest.main()
