# Changelog

## 0.3.0

- 新增劳动法规 RAG 评测脚本，可按 `labor_qa_examples.json` 输出问题 ID、Top-K 召回来源、期望来源命中、关键词命中和总体通过率。
- 新增示例评测报告，方便本地演示和后续回归对比。
- 后端新增 `RetrievalService` 检索服务边界，降低评测与问答控制器细节的耦合。
- 新增 `LEGAL_RAG_MIN_SCORE` / `legal-rag.min-score` 最低相似度阈值配置；低于阈值的召回结果会被过滤。
- 无足够检索依据时统一返回“本地知识库没有足够依据”，并保留 `[来源1]`、`[来源2]` 来源编号约束。
- README 补充 RAG 评测命令和推荐参数 `LEGAL_RAG_TOP_K=8`、`LEGAL_RAG_MIN_SCORE=0.2`。

## 0.2.1

- 修复 Docker 后端镜像构建在 Maven `dependency:go-offline` 阶段长时间无输出的问题。
- Docker Compose 端口改为可配置，并将 PostgreSQL 宿主机默认端口调整为 `15433` 以减少本地冲突。
- 补充 `.env.example` 中 API Key、模型维度、端口和演示参数说明。
- 整理 README 的本地开发、Docker Compose smoke test、最小演示流程和已知问题。

## 0.2.0

- 重构为 Spring Boot + Vue 前后端分离系统。
- 新增多知识库、文档上传、异步建库、pgvector 向量检索和来源卡片。
- 模型调用切换为 OpenAI-compatible API 环境变量配置。
- 新增 Docker Compose 编排 PostgreSQL pgvector、后端和前端。
- 移除 Python/Streamlit 运行时入口。

## 0.1.0

- 初始 Python/Streamlit 本地 RAG 原型。
