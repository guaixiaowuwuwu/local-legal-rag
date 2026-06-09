"""Local document loading for legal knowledge bases."""

from __future__ import annotations

from pathlib import Path
from typing import Iterable, List

from legal_rag.compat import Document, make_document

TEXT_SUFFIXES = {".md", ".markdown", ".txt"}
PDF_SUFFIXES = {".pdf"}
WORD_SUFFIXES = {".docx"}
SUPPORTED_SUFFIXES = TEXT_SUFFIXES | PDF_SUFFIXES | WORD_SUFFIXES


def load_local_documents(docs_dir: Path | str) -> List[Document]:
    root = Path(docs_dir)
    if not root.exists():
        raise FileNotFoundError(f"Documents directory does not exist: {root}")

    documents: List[Document] = []
    for path in _iter_supported_files(root):
        suffix = path.suffix.lower()
        if suffix in TEXT_SUFFIXES:
            documents.append(_load_text_file(path, root))
        elif suffix in PDF_SUFFIXES:
            documents.extend(_load_pdf(path, root))
        elif suffix in WORD_SUFFIXES:
            documents.extend(_load_word(path, root))
    return documents


def _iter_supported_files(root: Path) -> Iterable[Path]:
    return (
        path
        for path in sorted(root.rglob("*"))
        if path.is_file() and path.suffix.lower() in SUPPORTED_SUFFIXES
    )


def _load_text_file(path: Path, root: Path) -> Document:
    content = path.read_text(encoding="utf-8")
    return make_document(content, _base_metadata(path, root))


def _load_pdf(path: Path, root: Path) -> List[Document]:
    try:
        from langchain_community.document_loaders import PyPDFLoader
    except ImportError as exc:  # pragma: no cover - depends on runtime install.
        raise RuntimeError("Install langchain-community and pypdf to load PDF files.") from exc

    loaded = PyPDFLoader(str(path)).load()
    return _attach_base_metadata(loaded, path, root)


def _load_word(path: Path, root: Path) -> List[Document]:
    try:
        from langchain_community.document_loaders import Docx2txtLoader
    except ImportError as exc:  # pragma: no cover - depends on runtime install.
        raise RuntimeError("Install langchain-community and docx2txt to load Word files.") from exc

    loaded = Docx2txtLoader(str(path)).load()
    return _attach_base_metadata(loaded, path, root)


def _attach_base_metadata(documents: List[Document], path: Path, root: Path) -> List[Document]:
    base = _base_metadata(path, root)
    for doc in documents:
        metadata = dict(base)
        metadata.update(doc.metadata or {})
        doc.metadata = metadata
    return documents


def _base_metadata(path: Path, root: Path) -> dict:
    relative = path.relative_to(root)
    return {
        "source": str(relative),
        "source_path": str(path),
        "file_name": path.name,
        "file_type": path.suffix.lower().lstrip("."),
    }

