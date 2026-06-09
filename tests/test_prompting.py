import unittest

from legal_rag.compat import make_document
from legal_rag.prompting import build_legal_prompt, extract_source_metadata, format_context


class PromptingTest(unittest.TestCase):
    def test_prompt_contains_strict_rules_and_sources(self):
        doc = make_document(
            "第三条 同一员工与公司只能约定一次试用期。",
            {"source": "sample.md", "article": "第三条"},
        )

        prompt = build_legal_prompt("试用期可以约定几次？", [doc])

        self.assertIn("只允许依据", prompt)
        self.assertIn("不得编造法律条文", prompt)
        self.assertIn("[来源1]", prompt)
        self.assertIn("第三条", prompt)
        self.assertIn("试用期可以约定几次？", prompt)

    def test_source_metadata_uses_one_based_page(self):
        doc = make_document(
            "第四条 试用期工资不得低于约定工资的百分之八十。",
            {"source": "sample.pdf", "page": 0, "article": "第四条", "score": 0.91},
        )

        metadata = extract_source_metadata([doc])
        context = format_context([doc])

        self.assertEqual(metadata[0]["id"], "来源1")
        self.assertIn("第1页", metadata[0]["label"])
        self.assertIn("score=0.9100", context)


if __name__ == "__main__":
    unittest.main()

