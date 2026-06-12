# AGENTS.md

本文件给后续在本项目中工作的 Codex 或其他协作代理使用。执行任务时，请优先遵守本文件，再结合用户的最新指令。

## 项目定位

这是一个“法律检索 · 本地知识库 RAG 问答系统”，当前已升级为 Spring Boot + Vue 前后端分离架构。目标是支持多知识库、文档上传、异步建库、pgvector 向量检索、OpenAI-compatible 模型问答和可核验来源展示。

当前阶段重点是完成可本地 Docker Compose 演示的 MVP，而不是生产级多租户平台。

## 技术栈

- 后端：`backend/`，Spring Boot 3.5.x，Java 17，Maven。
- 前端：`frontend/`，Vue 3 + Vite + TypeScript。
- 数据库：PostgreSQL + pgvector，通过 Docker Compose 启动。
- 数据库迁移：`backend/src/main/resources/db/migration/`。
- 示例资料：`data/documents/`。
- 启动编排：`docker-compose.yml`。

## 工作原则

- 非小改动先看现有代码结构，再制定简短计划。
- 优先保持当前 MVP 架构，不要过早引入登录、多租户、复杂消息队列或微服务拆分。
- 法律问答必须强调来源和依据，避免编造法条、案号、发布日期、裁判观点。
- 不要把演示资料当作正式法律文本。
- 不要提交模型、上传文件、数据库数据、向量库或隐私资料。
- 前端保持工作台风格，优先清晰、密集、可扫描。

## 推荐验证命令

后端：

```bash
cd backend
mvn test
```

前端：

```bash
cd frontend
npm test
npm run build
```

端到端演示：

```bash
cp .env.example .env
docker compose up --build
```

然后访问 `http://localhost:5173`，创建知识库、上传 `data/documents/formal/` 中资料、开始建库并提问。

## 配置约定

- API Key 只放在后端环境变量中，前端不得读取或展示密钥。
- `SPRING_AI_OPENAI_BASE_URL` 可以指向 OpenAI-compatible `/v1` 地址。
- `SPRING_AI_VECTORSTORE_PGVECTOR_DIMENSIONS` 默认 1536；如果模型维度不同，需要同步调整数据库 schema 并重建数据库 volume。
- 文档上传目录通过 `LEGAL_RAG_UPLOAD_DIR` 配置。

## 暂缓事项

- 登录与权限系统。
- 多租户隔离。
- 联网法律检索。
- 自动法律意见书生成。
- 生产监控、审计、备份与权限治理。
