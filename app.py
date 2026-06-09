from __future__ import annotations

from pathlib import Path

import streamlit as st

from legal_rag.config import load_config
from legal_rag.service import LegalRAGService


st.set_page_config(page_title="法律检索 RAG", page_icon="§", layout="wide")

config = load_config().resolve_paths(Path.cwd())

with st.sidebar:
    docs_dir = st.text_input("文档目录", value=str(config.docs_dir))
    persist_dir = st.text_input("向量库目录", value=str(config.persist_dir))
    vector_store = st.selectbox("向量库", ["chroma", "faiss"], index=0 if config.vector_store == "chroma" else 1)
    top_k = st.slider("Top-K", min_value=1, max_value=10, value=config.top_k)
    llm_backend = st.selectbox(
        "生成模式",
        ["chatglm", "extractive"],
        index=0 if config.llm_backend == "chatglm" else 1,
    )
    reset = st.checkbox("重建前清空向量库", value=False)
    ingest = st.button("建库", use_container_width=True)

config = config.with_overrides(
    docs_dir=docs_dir,
    persist_dir=persist_dir,
    vector_store=vector_store,
    top_k=top_k,
    llm_backend=llm_backend,
).resolve_paths(Path.cwd())
service = LegalRAGService(config)

st.title("法律检索 · 本地知识库 RAG 问答系统")

if ingest:
    with st.spinner("正在读取、切分、向量化并写入本地向量库..."):
        stats = service.ingest(reset=reset)
    st.success(f"入库完成：原始文档 {stats['documents']} 份，切分片段 {stats['chunks']} 条。")

question = st.text_area("问题", height=110, placeholder="例如：劳动合同试用期有哪些限制？")
ask = st.button("检索并回答", type="primary")

if ask and question.strip():
    with st.spinner("正在检索本地法条并生成回答..."):
        result = service.ask(question)
    st.markdown(result.answer)

    if result.sources:
        st.subheader("来源")
        for source in result.sources:
            with st.expander(source["label"]):
                st.write(source["preview"])

