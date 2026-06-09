"""Local LLM backends."""

from __future__ import annotations

import re
from typing import Protocol

from legal_rag.config import RAGConfig
from legal_rag.prompting import NO_CONTEXT_MESSAGE


class AnswerGenerator(Protocol):
    def generate(self, prompt: str) -> str:
        ...


class ExtractiveAnswerer:
    """Deterministic fallback for smoke tests before local ChatGLM weights are configured."""

    def generate(self, prompt: str) -> str:
        context = _extract_tag(prompt, "检索原文")
        if not context or NO_CONTEXT_MESSAGE in context:
            return f"结论：{NO_CONTEXT_MESSAGE}\n依据：无。\n来源：无。"

        cited_sources = sorted(set(re.findall(r"\[来源\d+\]", context)))
        excerpt = _compact(context, limit=900)
        source_text = "、".join(cited_sources) if cited_sources else "检索原文"
        return (
            "结论：已检索到以下本地知识库原文，可作为回答该问题的依据。\n"
            f"依据：{excerpt}\n"
            f"来源：{source_text}"
        )


class LocalChatGLM:
    """Lazy-loading ChatGLM wrapper using local HuggingFace/Transformers models."""

    def __init__(self, config: RAGConfig) -> None:
        self.config = config
        self._tokenizer = None
        self._model = None

    def generate(self, prompt: str) -> str:
        tokenizer, model = self._load()
        if hasattr(model, "chat"):
            response = model.chat(
                tokenizer,
                prompt,
                history=[],
                temperature=self.config.temperature,
                max_new_tokens=self.config.max_new_tokens,
            )
            if isinstance(response, tuple):
                return str(response[0]).strip()
            return str(response).strip()

        import torch

        inputs = tokenizer(prompt, return_tensors="pt")
        device = getattr(model, "device", None)
        if device is not None:
            inputs = {key: value.to(device) for key, value in inputs.items()}
        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=self.config.max_new_tokens,
                do_sample=self.config.temperature > 0,
                temperature=max(self.config.temperature, 1e-5),
            )
        generated = outputs[0][inputs["input_ids"].shape[-1] :]
        return tokenizer.decode(generated, skip_special_tokens=True).strip()

    def _load(self):
        if self._tokenizer is not None and self._model is not None:
            return self._tokenizer, self._model

        import torch
        from transformers import AutoModel, AutoModelForCausalLM, AutoTokenizer

        tokenizer = AutoTokenizer.from_pretrained(self.config.llm_model, trust_remote_code=True)
        load_kwargs = {"trust_remote_code": True}
        if self.config.llm_device == "auto":
            load_kwargs["device_map"] = "auto"
        elif self.config.llm_device == "cuda" and torch.cuda.is_available():
            load_kwargs["torch_dtype"] = torch.float16

        try:
            model = AutoModel.from_pretrained(self.config.llm_model, **load_kwargs)
        except Exception:
            model = AutoModelForCausalLM.from_pretrained(self.config.llm_model, **load_kwargs)

        if self.config.llm_device not in {"auto", "cpu"}:
            model = model.to(self.config.llm_device)
        elif self.config.llm_device == "cpu":
            model = model.to("cpu")
        model.eval()

        self._tokenizer = tokenizer
        self._model = model
        return tokenizer, model


def build_llm(config: RAGConfig) -> AnswerGenerator:
    if config.llm_backend == "extractive":
        return ExtractiveAnswerer()
    if config.llm_backend == "chatglm":
        return LocalChatGLM(config)
    raise ValueError(f"Unsupported llm_backend: {config.llm_backend}")


def _extract_tag(text: str, tag: str) -> str:
    match = re.search(rf"(?m)^<{tag}>\s*(.*?)^\s*</{tag}>", text, flags=re.S)
    return match.group(1).strip() if match else ""


def _compact(text: str, limit: int) -> str:
    clean = re.sub(r"\s+", " ", text).strip()
    if len(clean) <= limit:
        return clean
    return clean[: limit - 1].rstrip() + "..."
