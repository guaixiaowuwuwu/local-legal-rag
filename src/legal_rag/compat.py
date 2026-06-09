"""Small compatibility layer for tests before LangChain is installed."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Optional

try:  # pragma: no cover - exercised in real LangChain installs.
    from langchain_core.documents import Document as Document
except Exception:  # pragma: no cover - the fallback is covered in local tests.

    @dataclass
    class Document:  # type: ignore[no-redef]
        page_content: str
        metadata: Dict[str, Any] = field(default_factory=dict)


def make_document(page_content: str, metadata: Optional[Dict[str, Any]] = None) -> Document:
    return Document(page_content=page_content, metadata=metadata or {})

