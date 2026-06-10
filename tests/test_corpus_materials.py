import json
import tempfile
import unittest
from pathlib import Path

from legal_rag.config import RAGConfig
from legal_rag.loaders import load_local_documents
from legal_rag.service import LegalRAGService


ROOT = Path(__file__).resolve().parents[1]
FORMAL_DOCS = ROOT / "data" / "documents" / "formal"
QA_EXAMPLES = ROOT / "data" / "evaluation" / "labor_qa_examples.json"


class CorpusMaterialsTest(unittest.TestCase):
    def test_default_docs_dir_points_to_formal_corpus(self):
        self.assertEqual(RAGConfig().docs_dir, Path("data/documents/formal"))

    def test_formal_labor_corpus_loads_supported_documents(self):
        documents = load_local_documents(FORMAL_DOCS)
        sources = {document.metadata["source"] for document in documents}
        expected_sources = {
            "labor/administrative_regulation/laodong_hetong_fa_shishi_tiaoli_excerpt.md",
            "labor/national_law/laodong_fa_excerpt.md",
            "labor/national_law/laodong_hetong_fa_excerpt.md",
            "labor/national_law/laodong_zhengyi_tiaojie_zhongcai_fa_excerpt.md",
            "labor/national_law/shehui_baoxian_fa_excerpt.md",
        }

        self.assertTrue(expected_sources.issubset(sources))

        combined = "\n".join(document.page_content for document in documents)
        for term in (
            "试用期不得超过六个月",
            "每月支付两倍的工资",
            "自用工之日起三十日内",
            "仲裁的时效期间为一年",
        ):
            self.assertIn(term, combined)

    def test_loader_reports_directory_with_no_supported_files(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            docs_dir = Path(tmpdir)
            (docs_dir / "image.png").write_text("not supported", encoding="utf-8")

            with self.assertRaisesRegex(RuntimeError, "未在文档目录中找到支持的文件"):
                load_local_documents(docs_dir)

    def test_regression_examples_reference_existing_sources(self):
        payload = json.loads(QA_EXAMPLES.read_text(encoding="utf-8"))
        examples = payload["examples"]
        known_sources = {
            str(path.relative_to(FORMAL_DOCS))
            for path in FORMAL_DOCS.rglob("*")
            if path.is_file() and path.suffix == ".md"
        }

        self.assertGreaterEqual(len(examples), 5)
        self.assertLessEqual(len(examples), 10)
        for example in examples:
            self.assertTrue(example["id"])
            self.assertTrue(example["question"])
            self.assertTrue(example["expected_sources"])
            self.assertTrue(example["expected_terms"])
            for source in example["expected_sources"]:
                self.assertIn(source, known_sources)

    def test_regression_examples_retrieve_expected_sources(self):
        payload = json.loads(QA_EXAMPLES.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmpdir:
            config = RAGConfig(
                docs_dir=FORMAL_DOCS,
                persist_dir=Path(tmpdir),
                vector_store="local",
                embedding_model="hash",
                llm_backend="extractive",
                top_k=5,
            )
            service = LegalRAGService(config)
            service.ingest(reset=True)

            for example in payload["examples"]:
                with self.subTest(example_id=example["id"]):
                    retrieved = service.retrieve(example["question"])
                    actual_sources = {document.metadata.get("source") for document in retrieved}
                    expected_sources = set(example["expected_sources"])
                    self.assertTrue(actual_sources & expected_sources)


if __name__ == "__main__":
    unittest.main()
