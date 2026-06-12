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

```bash
cp .env.example .env
# 编辑 .env，填入 SPRING_AI_OPENAI_API_KEY 和模型名
docker compose up --build
```

访问：

- 前端：http://localhost:5173
- 后端健康检查：http://localhost:8080/actuator/health
- PostgreSQL：localhost:5432，库名/用户/密码均为 `legal_rag`

首次演示：

1. 打开前端并创建知识库。
2. 上传 `data/documents/formal/` 下的资料。
3. 点击“开始”创建建库任务。
4. 任务成功后提问：`试用期工资有什么要求？`
5. 查看回答和来源卡片。

## 本地开发

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

## 配置

后端通过环境变量读取配置：

- `SPRING_AI_OPENAI_BASE_URL`：OpenAI-compatible API 地址，默认 `https://api.openai.com/v1`。
- `SPRING_AI_OPENAI_API_KEY`：API Key，前端不会读取或展示。
- `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`：Chat 模型名。
- `SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL`：Embedding 模型名。
- `SPRING_AI_VECTORSTORE_PGVECTOR_DIMENSIONS`：Embedding 维度，默认 `1536`。
- `LEGAL_RAG_UPLOAD_DIR`：上传文件保存目录。
- `LEGAL_RAG_CHUNK_SIZE` / `LEGAL_RAG_CHUNK_OVERLAP`：切分参数。
- `LEGAL_RAG_TOP_K`：默认召回数量。

当前 Flyway 迁移将 `rag_chunks.embedding` 创建为 `vector(1536)`。如果使用不同维度的 embedding 模型，需要修改迁移或重建数据库 volume 后再运行。

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

如 Maven 中央仓库下载中断，可重试 `mvn -U test`。Docker 构建同样依赖 Maven/NPM 网络可用。

## 法律场景约束

系统 Prompt 要求模型只依据检索原文回答，并在回答中标注 `[来源1]` 等来源编号。若知识库没有足够依据，应明确说明无法回答，避免编造法条、案号、发布日期或裁判观点。

本项目是检索与问答工具，不替代律师正式法律意见。生产使用前请导入权威、完整、可追溯来源的法律文本，复核现行有效状态，并在资料更新后重新建库。
