import io
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

from legal_rag.cli import main


class CliTest(unittest.TestCase):
    def test_smoke_command_ingests_and_answers_with_sources(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            docs_dir = root / "documents"
            persist_dir = root / "vectorstore"
            docs_dir.mkdir()
            (docs_dir / "sample.md").write_text(
                "第一章 试用期\n\n"
                "第一条 同一员工与公司只能约定一次试用期。\n\n"
                "第二条 试用期工资不得低于劳动合同约定工资的百分之八十。",
                encoding="utf-8",
            )

            output = io.StringIO()
            with redirect_stdout(output):
                exit_code = main(
                    [
                        "smoke",
                        "--docs-dir",
                        str(docs_dir),
                        "--persist-dir",
                        str(persist_dir),
                    ]
                )

            text = output.getvalue()
            self.assertEqual(exit_code, 0)
            self.assertIn("入库完成", text)
            self.assertIn("来源：", text)
            self.assertTrue((persist_dir / "legal_rag.json").exists())

    def test_ingest_bad_docs_dir_returns_clear_error(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            missing_dir = Path(tmpdir) / "missing"
            persist_dir = Path(tmpdir) / "vectorstore"

            error = io.StringIO()
            with redirect_stderr(error):
                exit_code = main(
                    [
                        "ingest",
                        "--docs-dir",
                        str(missing_dir),
                        "--persist-dir",
                        str(persist_dir),
                        "--embedding-model",
                        "hash",
                        "--llm-backend",
                        "extractive",
                    ]
                )

            self.assertEqual(exit_code, 1)
            self.assertIn("错误：文档目录不存在", error.getvalue())


if __name__ == "__main__":
    unittest.main()
