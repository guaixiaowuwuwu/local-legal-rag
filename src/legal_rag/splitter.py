"""Legal-aware text splitting.

The splitter prefers Chinese legal article boundaries such as "第十九条" and only falls
back to character windows when an individual article is longer than the configured chunk.
"""

from __future__ import annotations

import re
from typing import Any, Dict, Iterable, List, Mapping, Sequence

from legal_rag.compat import Document, make_document

LEGAL_NUMBER = r"[零〇一二三四五六七八九十百千万亿两0-9]+(?:之[零〇一二三四五六七八九十百千万亿两0-9]+)?"
ARTICLE_RE = re.compile(rf"(?m)^\s*(第{LEGAL_NUMBER}条)\s*([^\n]*)")
HIERARCHY_RE = re.compile(rf"(?m)^\s*(第{LEGAL_NUMBER}(?:编|章|节))\s*([^\n]*)")
SENTENCE_SPLIT_RE = re.compile(r"(?<=[。；;！？!?])\s*|\n{2,}")


def split_documents(
    documents: Iterable[Document],
    chunk_size: int = 900,
    chunk_overlap: int = 120,
) -> List[Document]:
    chunks: List[Document] = []
    for doc_index, document in enumerate(documents):
        base_metadata = dict(document.metadata or {})
        base_metadata.setdefault("source", f"document-{doc_index + 1}")
        doc_chunks = split_legal_text(
            document.page_content,
            source_metadata=base_metadata,
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
        )
        for local_index, chunk in enumerate(doc_chunks):
            chunk.metadata.setdefault("parent_document_index", doc_index)
            chunk.metadata.setdefault("chunk_index", local_index)
            chunks.append(chunk)
    return chunks


def split_legal_text(
    text: str,
    source_metadata: Mapping[str, Any] | None = None,
    chunk_size: int = 900,
    chunk_overlap: int = 120,
) -> List[Document]:
    normalized = _normalize_text(text)
    if not normalized:
        return []

    source = dict(source_metadata or {})
    article_blocks = _article_blocks(normalized)
    if article_blocks:
        chunks: List[Document] = []
        for block, metadata in article_blocks:
            chunks.extend(
                _documents_from_parts(
                    _split_oversized(block, chunk_size, chunk_overlap),
                    {**source, **metadata},
                )
            )
        return chunks

    return _documents_from_parts(_split_oversized(normalized, chunk_size, chunk_overlap), source)


def _article_blocks(text: str) -> List[tuple[str, Dict[str, Any]]]:
    matches = list(ARTICLE_RE.finditer(text))
    blocks: List[tuple[str, Dict[str, Any]]] = []
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        block = text[start:end].strip()
        if not block:
            continue

        metadata = _nearest_hierarchy(text[:start])
        metadata["article"] = match.group(1)
        title = match.group(2).strip()
        if title:
            metadata["article_title"] = title
        blocks.append((block, metadata))
    return blocks


def _nearest_hierarchy(prefix: str) -> Dict[str, str]:
    hierarchy: Dict[str, str] = {}
    for match in HIERARCHY_RE.finditer(prefix):
        label = match.group(1)
        title = f"{label} {match.group(2).strip()}".strip()
        if label.endswith("编"):
            hierarchy["part"] = title
        elif label.endswith("章"):
            hierarchy["chapter"] = title
        elif label.endswith("节"):
            hierarchy["section"] = title
    return hierarchy


def _documents_from_parts(parts: Sequence[str], metadata: Mapping[str, Any]) -> List[Document]:
    documents: List[Document] = []
    total = len(parts)
    for index, part in enumerate(parts):
        clean = part.strip()
        if not clean:
            continue
        part_metadata = dict(metadata)
        part_metadata["chunk_part"] = index + 1
        part_metadata["chunk_parts"] = total
        documents.append(make_document(clean, part_metadata))
    return documents


def _split_oversized(text: str, chunk_size: int, chunk_overlap: int) -> List[str]:
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive.")
    overlap = max(0, min(chunk_overlap, chunk_size // 2))
    if len(text) <= chunk_size:
        return [text]

    units = _sentence_units(text)
    chunks: List[str] = []
    current = ""

    for unit in units:
        if len(unit) > chunk_size:
            if current:
                chunks.append(current.strip())
                current = ""
            chunks.extend(_window_split(unit, chunk_size, overlap))
            continue

        candidate = f"{current}\n{unit}".strip() if current else unit
        if len(candidate) <= chunk_size:
            current = candidate
            continue

        if current:
            chunks.append(current.strip())
            current = _join_overlap(current, unit, overlap)
        else:
            current = unit

    if current:
        chunks.append(current.strip())
    return chunks


def _sentence_units(text: str) -> List[str]:
    parts = [part.strip() for part in SENTENCE_SPLIT_RE.split(text) if part and part.strip()]
    return parts or [text]


def _window_split(text: str, chunk_size: int, overlap: int) -> List[str]:
    chunks: List[str] = []
    step = max(1, chunk_size - overlap)
    for start in range(0, len(text), step):
        chunk = text[start : start + chunk_size].strip()
        if chunk:
            chunks.append(chunk)
        if start + chunk_size >= len(text):
            break
    return chunks


def _join_overlap(previous: str, next_unit: str, overlap: int) -> str:
    if overlap <= 0:
        return next_unit
    suffix = previous[-overlap:].strip()
    return f"{suffix}\n{next_unit}".strip()


def _normalize_text(text: str) -> str:
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")]
    return "\n".join(lines).strip()

