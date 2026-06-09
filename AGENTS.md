# AGENTS.md

本文件给后续在本项目中工作的 Codex 或其他协作代理使用。执行任务时，请优先遵守本文件，再结合用户的最新指令。

## 项目定位

这是一个“法律检索 · 本地知识库 RAG 问答系统”原型，目标是将 PDF、Word、Markdown、TXT 等本地资料读取后切分、向量化、写入本地向量库，并基于检索结果生成带来源的克制回答。

当前阶段重点是完成可本地演示的 MVP，而不是生产级平台。

## 技术栈

- Python 包结构位于 `src/legal_rag/`。
- CLI 入口：`src/legal_rag/cli.py`。
- Streamlit 前端入口：`app.py`。
- 配置文件：`configs/default.yaml`。
- 示例资料目录：`data/documents/`。
- 测试目录：`tests/`。
- 依赖声明：`pyproject.toml`。

## 工作原则

- 非小改动先看现有代码结构，再制定简短计划。
- 优先保持当前轻量架构，不要过早引入复杂服务端、数据库或前端框架。
- 默认先保证 `extractive` 模式可跑通，再接入 ChatGLM 等本地大模型。
- 法律问答必须强调来源和依据，避免编造法条、案号、发布日期、裁判观点。
- 不要把演示资料当作正式法律文本。
- 不要提交本地模型、向量库、隐私资料或大文件。

## 推荐验证命令

在未安装完整开发依赖时，可以先运行：

```bash
PYTHONPATH=src python3 -m unittest discover -s tests -p 'test_*.py' -v
```

安装开发依赖后，优先运行：

```bash
python3 -m pytest -q
```

CLI 帮助检查：

```bash
PYTHONPATH=src python3 -m legal_rag.cli --help
```

轻量烟测建议使用抽取式模式：

```bash
legal-rag ingest --reset --llm-backend extractive
legal-rag ask "试用期工资有什么要求？" --llm-backend extractive
```

前端演示：

```bash
streamlit run app.py
```

## 下一阶段优先级

1. 先补齐本地环境与测试基线。
2. 再升级 Streamlit 页面为可演示工作台。
3. 然后导入真实法律资料并建立问答样例。
4. 最后接入本地 Embedding 与 ChatGLM 模型做真实验证。

详细任务请看 `NEXT_PHASE_TASKS.md`。

## 编辑约束

- 修改代码时尽量保持改动小而清晰。
- 新增功能要配套最小必要测试。
- 修改 Prompt、切分逻辑、检索逻辑时，要重点考虑法律场景下的准确性与可追溯性。
- 若发现当前机器缺少依赖，不要直接声称功能失败；先说明缺少的依赖和可替代验证方式。
- 遇到用户已有改动时，不要回滚，先理解并在其基础上继续工作。

