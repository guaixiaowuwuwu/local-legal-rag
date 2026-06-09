"""Prompt construction and source formatting."""

from __future__ import annotations

from typing import Any, Dict, Iterable, List

from legal_rag.compat import Document

NO_CONTEXT_MESSAGE = (
    "未在本地知识库检索到足够相关的法律原文。请补充权威文件后重新建库，"
    "或换一种更具体的问法。"
)

PROMPT_TEMPLATE = """你是律所/法务部门内部的本地法律知识库问答助手。

必须遵守：
1. 只允许依据 <检索原文> 中的内容回答。
2. 不得编造法律条文、案号、发布日期、裁判观点或来源。
3. 如果检索原文不足以回答，直接说明“本地知识库未检索到足够依据”。
4. 回答中必须引用来源编号，例如 [来源1]。
5. 语言应准确、克制，不能替代律师正式法律意见。

<检索原文>
{context}
</检索原文>

<用户问题>
{question}
</用户问题>

请按以下格式输出：
结论：
依据：
来源：
"""


def build_legal_prompt(question: str, documents: Iterable[Document]) -> str:
    return PROMPT_TEMPLATE.format(question=question.strip(), context=format_context(documents))


def format_context(documents: Iterable[Document]) -> str:
    parts = []
    for index, document in enumerate(documents, start=1):
        label = source_label(document, index)
        parts.append(f"[来源{index}] {label}\n{document.page_content.strip()}")
    return "\n\n".join(parts) if parts else NO_CONTEXT_MESSAGE


def source_label(document: Document, index: int) -> str:
    metadata = document.metadata or {}
    items = [str(metadata.get("source") or metadata.get("file_name") or f"document-{index}")]

    page = metadata.get("page")
    if isinstance(page, int):
        items.append(f"第{page + 1}页")
    elif page:
        items.append(f"页码: {page}")

    for key in ("part", "chapter", "section", "article"):
        value = metadata.get(key)
        if value:
            items.append(str(value))

    score = metadata.get("score")
    if isinstance(score, (float, int)):
        items.append(f"score={score:.4f}")

    return " | ".join(items)


def extract_source_metadata(documents: Iterable[Document]) -> List[Dict[str, Any]]:
    sources: List[Dict[str, Any]] = []
    for index, document in enumerate(documents, start=1):
        metadata = dict(document.metadata or {})
        sources.append(
            {
                "id": f"来源{index}",
                "label": source_label(document, index),
                "source": metadata.get("source"),
                "source_path": metadata.get("source_path"),
                "page": metadata.get("page"),
                "article": metadata.get("article"),
                "score": metadata.get("score"),
                "preview": document.page_content.strip()[:180],
            }
        )
    return sources

