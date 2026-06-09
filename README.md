# 法律检索 · 本地知识库 RAG 问答系统

基于 LangChain、中文 Embedding、本地向量库和 ChatGLM 的私有化法律知识库问答骨架。它把 PDF、Word、Markdown、TXT 文档读取后按法条结构切分，写入轻量本地 JSON、Chroma 或 FAISS，再通过 Top-K 检索和严格 Prompt 约束生成带来源的回答。

## RAG 7 步对应

1. 文档加载：`legal_rag.loaders.load_local_documents`
2. 文本分割：`legal_rag.splitter.split_documents`
3. 文本向量化：`legal_rag.embeddings.build_embeddings`
4. 向量入库：`legal_rag.vectorstores.create_vector_store`
5. Query 向量化：由配置的 Embedding 与 VectorStore 检索时完成
6. Top-K 检索：`LegalRAGService.retrieve`
7. Prompt + LLM：`legal_rag.prompting` + `legal_rag.llm.LocalChatGLM`

## 快速开始

### 轻量开发与测试

这条路径只安装项目本身和测试工具，不拉取 Chroma、FAISS、Torch、Transformers
等大依赖，适合先确认本地代码、切分、Prompt、轻量烟测是否可运行。

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -U pip
python3 -m pip install --no-deps -e .
python3 -m pip install pytest
python3 -m pytest -q
```

也可以不安装项目，直接用源码运行单元测试：

```bash
PYTHONPATH=src python3 -m unittest discover -s tests -p 'test_*.py' -v
```

### 全量演示依赖

需要 Streamlit、Chroma、FAISS、Embedding 模型和本地 LLM 时，再安装完整依赖：

```bash
source .venv/bin/activate
python3 -m pip install -e ".[ui,faiss,dev]"
```

2026-06-09 本机安装记录：

- `.venv` 创建成功，`pip` 从 21.2.4 升级到 26.0.1 成功；升级后访问 PyPI
  末尾出现一次 `SSLEOFError` 提示，但升级已经完成。
- 已执行 `python3 -m pip install -e ".[ui,faiss,dev]"`。安装未观察到版本冲突，
  但网络吞吐过低：`numpy` 5.3 MB 下载约 4 分 09 秒，随后 `chromadb`
  21.7 MB 长时间无新进度，本次手动停止。
- 当前可用替代验证：使用上面的轻量安装流程，`python3 -m pytest -q` 已通过。

把法律条文、司法解释、判例或内部制度放入 `data/documents/formal/`。支持 `.pdf`、`.docx`、`.md`、`.txt`。
默认配置只导入 `formal/`，不会把 `data/documents/examples/` 下的演示资料混入正式知识库。

先用可重复的轻量抽取式模式做端到端烟测：

```bash
PYTHONPATH=src python3 -m legal_rag.cli smoke
```

`smoke` 默认使用 `--vector-store local`、`--embedding-model hash` 和
`--llm-backend extractive`，会完成示例文档加载、切分、写入本地 JSON 向量库、
检索问答和来源输出。安装项目后也可以运行：

```bash
legal-rag smoke
```

如果已经安装完整依赖，也可以用 Chroma/FAISS 和真实 Embedding 做烟测：

```bash
legal-rag ingest --reset --llm-backend extractive
legal-rag ask "试用期工资有什么要求？" --llm-backend extractive
```

接入本地 ChatGLM：

```bash
export LEGAL_RAG_LLM_BACKEND=chatglm
export LEGAL_RAG_LLM_MODEL=/path/to/local/chatglm3-6b
export LEGAL_RAG_EMBEDDING_MODEL=/path/to/local/bge-m3
legal-rag ingest --reset
legal-rag ask "劳动合同试用期有哪些限制？"
```

启动界面：

```bash
streamlit run app.py
```

## 配置

默认配置在 `configs/default.yaml`，也可以使用环境变量覆盖。常用项：

- `LEGAL_RAG_DOCS_DIR`：本地法律文档目录
- `LEGAL_RAG_PERSIST_DIR`：本地向量库持久化目录
- `LEGAL_RAG_VECTOR_STORE`：`local`、`chroma` 或 `faiss`
- `LEGAL_RAG_EMBEDDING_MODEL`：中文向量模型名称或本地路径
- `LEGAL_RAG_LLM_MODEL`：ChatGLM 模型名称或本地路径
- `LEGAL_RAG_CHUNK_SIZE` / `LEGAL_RAG_CHUNK_OVERLAP`：法条切分窗口
- `LEGAL_RAG_TOP_K`：召回数量

## 知识库资料

当前首批资料位于 `data/documents/formal/labor/`，覆盖劳动合同、劳动法、社会保险和劳动争议调解仲裁的常见演示问题。

- `data/documents/README.md`：正式资料与示例资料的目录规范。
- `data/documents/FORMAL_SOURCES.md`：每份正式资料的来源、版本、发布日期和适用范围台账。
- `data/evaluation/labor_qa_examples.json`：10 条真实问答回归样例，每条包含期望来源文件和关键命中词。

首批正文是“权威文本摘录版”，用于 MVP 检索链路和问答回归验证，不是完整法律库。正式使用前请导入全文资料，复核现行有效状态，然后重新建库。

## 本地运行要求

- 轻量 smoke：Python 3.9+、CPU 即可，通常 2 GB 以上内存足够；不需要 GPU、模型权重或外网。
- Chroma/FAISS + HuggingFace Embedding：建议 8 GB 以上内存、稳定网络和数 GB 磁盘空间；首次运行会下载或读取 Embedding 模型。
- ChatGLM 本地生成：CPU 可跑但会很慢，建议 16-32 GB 内存；GPU 显存需求取决于模型大小和量化方式，6B 级模型通常建议至少 8-16 GB 显存，FP16 需要更多。
- 离线运行时，把模型放到本机目录，并设置 `LEGAL_RAG_EMBEDDING_MODEL` 与 `LEGAL_RAG_LLM_MODEL` 指向这些目录。不要提交 `models/`、向量库或隐私资料。

## 法律场景约束

Prompt 要求模型只基于检索原文回答，并在回答中标注 `[来源1]` 这类来源编号。若知识库没有足够依据，模型应明确说明无法回答，避免编造法条、案号或裁判观点。

本项目是检索与问答工具，不替代律师正式法律意见。生产使用时请导入权威、完整、可追溯来源的法律文本，并定期重建向量库。
