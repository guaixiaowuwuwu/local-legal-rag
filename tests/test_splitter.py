import unittest

from legal_rag.splitter import split_legal_text


class SplitterTest(unittest.TestCase):
    def test_splits_on_chinese_article_boundaries(self):
        text = """第一章 总则

第一条 为规范公司劳动合同管理，制定本示例制度。

第二条 公司订立、变更、解除或者终止劳动合同时，应当保留完整书面材料。
"""
        chunks = split_legal_text(text, {"source": "sample.md"}, chunk_size=200, chunk_overlap=20)

        self.assertEqual(len(chunks), 2)
        self.assertEqual(chunks[0].metadata["article"], "第一条")
        self.assertEqual(chunks[1].metadata["article"], "第二条")
        self.assertEqual(chunks[0].metadata["chapter"], "第一章 总则")
        self.assertNotIn("第二条", chunks[0].page_content)

    def test_oversized_article_uses_overlap(self):
        text = "第一条 " + "甲乙双方应当依法履行义务。" * 80
        chunks = split_legal_text(text, {"source": "long.md"}, chunk_size=120, chunk_overlap=20)

        self.assertGreater(len(chunks), 1)
        self.assertTrue(all(chunk.metadata["article"] == "第一条" for chunk in chunks))
        self.assertTrue(all(len(chunk.page_content) <= 120 for chunk in chunks))

    def test_fallback_without_articles(self):
        text = "这是一个没有法条编号的内部说明。" * 60
        chunks = split_legal_text(text, {"source": "note.txt"}, chunk_size=100, chunk_overlap=10)

        self.assertGreater(len(chunks), 1)
        self.assertEqual(chunks[0].metadata["source"], "note.txt")


if __name__ == "__main__":
    unittest.main()

