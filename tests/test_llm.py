import unittest

from legal_rag.compat import make_document
from legal_rag.llm import ExtractiveAnswerer
from legal_rag.prompting import build_legal_prompt


class ExtractiveAnswererTest(unittest.TestCase):
    def test_uses_only_context_tag_body(self):
        doc = make_document(
            "第四条 试用期工资不得低于劳动合同约定工资的百分之八十。",
            {"source": "sample.md", "article": "第四条"},
        )
        prompt = build_legal_prompt("试用期工资有什么要求？", [doc])

        answer = ExtractiveAnswerer().generate(prompt)

        self.assertIn("第四条", answer)
        self.assertNotIn("不得编造法律条文", answer)


if __name__ == "__main__":
    unittest.main()
