# Changelog

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
