# 法律检索 · 本地知识库 RAG 问答系统

基于 Spring Boot、Vue 3、PostgreSQL + pgvector 和 OpenAI-compatible API 的前后端分离法律知识库 RAG 系统。系统支持多知识库、文档上传、异步建库、向量检索问答和来源核验。

## 架构

- `backend/`：Spring Boot 3.5.x REST API，Java 17。
- `frontend/`：Vue 3 + Vite + TypeScript 工作台。
- `postgres-pgvector`：PostgreSQL + pgvector，保存知识库、文档、建库任务、chunk 和向量。
- `data/documents/`：保留的演示资料，可通过前端上传到知识库。

RAG 流程：

1. 上传 `.pdf`、`.docx`、`.md`、`.txt`。
2. 后端异步解析文档，PDF 保留页码。
3. 按中文法条结构优先切分，超长文本按句子/窗口切分。
4. 调用 OpenAI-compatible Embedding API。
5. 写入 PostgreSQL pgvector。
6. 问答时按知识库过滤 Top-K chunk。
7. 构造严格法律 Prompt，调用 Chat API，返回回答和来源卡片。

## 快速启动

仅验证系统能启动时，可以不配置 API Key：

```bash
cp .env.example .env
docker compose up --build
```

如需完成建库和问答演示，请先编辑 `.env`，设置有效的 `SPRING_AI_OPENAI_API_KEY`、模型名和对应 embedding 维度。

访问：

- 前端：http://localhost:5173
- 后端健康检查：http://localhost:8080/actuator/health
- PostgreSQL：localhost:15433，库名/用户/密码均为 `legal_rag`

如果端口被占用，可在 `.env` 中调整：

```bash
LEGAL_RAG_FRONTEND_PORT=5174
LEGAL_RAG_BACKEND_PORT=8081
LEGAL_RAG_POSTGRES_PORT=15434
```

首次演示：

1. 打开前端并创建知识库。
2. 上传 `data/documents/examples/sample_internal_policy.md`，或上传你准备好的 `.pdf`、`.docx`、`.md`、`.txt` 文件。
3. 点击“开始”创建建库任务。
4. 任务成功后提问：`试用期工资有什么要求？`
5. 查看回答和来源卡片。

停止服务：

```bash
docker compose down
```

## 本地开发

本地开发通常需要先启动 PostgreSQL：

```bash
docker compose up -d postgres-pgvector
```

后端：

```bash
cd backend
mvn test
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm test
npm run dev
```

前端开发服务器默认代理 `/api` 到 `http://localhost:8080`。

完整本地验证：

```bash
cd backend && mvn test
cd frontend && npm test && npm run build
docker compose up --build
```

## 配置

后端通过环境变量读取配置：

- `LEGAL_RAG_FRONTEND_PORT`：Docker Compose 暴露的前端端口，默认 `5173`。
- `LEGAL_RAG_BACKEND_PORT`：Docker Compose 暴露的后端端口，默认 `8080`。
- `LEGAL_RAG_POSTGRES_PORT`：Docker Compose 暴露的 PostgreSQL 端口，默认 `15433`。
- `SPRING_AI_OPENAI_BASE_URL`：OpenAI-compatible API 地址，默认 `https://api.openai.com/v1`。
- `SPRING_AI_OPENAI_API_KEY`：API Key，前端不会读取或展示。
- `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`：Chat 模型名。
- `SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL`：Embedding 模型名。
- `SPRING_AI_VECTORSTORE_PGVECTOR_DIMENSIONS`：Embedding 维度，默认 `1536`。
- `LEGAL_RAG_UPLOAD_DIR`：上传文件保存目录。
- `LEGAL_RAG_CHUNK_SIZE` / `LEGAL_RAG_CHUNK_OVERLAP`：切分参数。
- `LEGAL_RAG_TOP_K`：默认召回数量。

当前 Flyway 迁移将 `rag_chunks.embedding` 创建为 `vector(1536)`。如果使用不同维度的 embedding 模型，需要修改迁移或重建数据库 volume 后再运行。

如果只做启动 smoke test，`SPRING_AI_OPENAI_API_KEY` 可以为空；后端健康检查和知识库 CRUD 可用，但建库和问答会在调用模型 API 时失败并提示缺少 Key。

## API 概览

- `GET/POST /api/knowledge-bases`
- `GET /api/knowledge-bases/{id}`
- `GET/POST /api/knowledge-bases/{id}/documents`
- `GET/DELETE /api/knowledge-bases/{id}/documents/{documentId}`
- `POST /api/knowledge-bases/{id}/ingest-jobs`
- `GET /api/ingest-jobs/{jobId}`
- `POST /api/knowledge-bases/{id}/questions`
- `GET /api/runtime-config`

## 测试

```bash
cd backend && mvn test
cd frontend && npm test && npm run build
```

如 Maven 中央仓库下载中断，可重试 `mvn -U test`。Docker 首次构建会下载 Maven/NPM 依赖，依赖 Docker Hub、Maven Central 和 npm registry 网络可用；后续构建会复用缓存。

## Docker Compose smoke test

推荐 smoke test：

```bash
cp .env.example .env
docker compose up --build
```

看到后端启动完成后，在另一个终端验证：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:5173/
```

预期：

- 健康检查返回包含 `"status":"UP"` 的 JSON。
- 前端首页返回 HTML。
- 如果没有设置 API Key，页面会显示 API Key 未配置；这是预期状态，不影响启动 smoke test。

## 已知问题

- `npm run build` 可能输出 Rolldown 对 `@vueuse/core` pure annotation 的警告，以及前端 chunk 超过 500 kB 的警告；当前构建退出码为 0，属于非阻塞项。
- 真实建库和问答依赖 OpenAI-compatible 服务、有效 API Key、模型名和 embedding 维度一致性。
- 示例资料仅用于演示流程，不是权威或完整法律文本。

## 法律场景约束

系统 Prompt 要求模型只依据检索原文回答，并在回答中标注 `[来源1]` 等来源编号。若知识库没有足够依据，应明确说明无法回答，避免编造法条、案号、发布日期或裁判观点。

本项目是检索与问答工具，不替代律师正式法律意见。生产使用前请导入权威、完整、可追溯来源的法律文本，复核现行有效状态，并在资料更新后重新建库。
