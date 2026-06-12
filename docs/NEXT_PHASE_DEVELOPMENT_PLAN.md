# 下一阶段开发文档

## 1. 阶段目标

下一阶段目标不是把系统扩展成生产级多租户法律平台，而是把当前本地 MVP 提升为“工程化可演示版本”：

- 本地 Docker Compose 一键启动后，核心流程稳定可复现。
- RAG 效果有自动化回归评估，而不是只靠人工感觉。
- 建库、检索、问答链路具备基础可靠性、错误可见性和可测试性。
- 前端工作台可以支撑完整演示、定位失败和核验来源。
- 明确生产化缺口，避免把演示能力误认为法律服务能力。

本阶段完成后，项目应达到：新开发者按 README 启动后，可以导入示例资料、运行评测、执行测试、完成一次可解释的法律 RAG 演示。

## 2. 不做事项

以下事项继续暂缓，除非后续单独立项：

- 登录、权限、多租户隔离。
- 联网法律检索和法规自动更新。
- 自动生成正式法律意见书。
- 生产级审计、备份、监控、告警。
- 大规模分布式任务调度。
- 商业模型路由、计费和额度系统。

这些能力重要，但会显著扩大范围。当前更应该先补齐 RAG 质量、测试、可靠性和演示闭环。

## 3. 成功标准

### 3.1 功能验收

- 能通过 Docker Compose 启动 PostgreSQL pgvector、后端和前端。
- 前端可以创建知识库、上传 `data/documents/formal/` 示例资料、触发建库、查看任务结果。
- 建库失败时，前端能显示具体失败原因，后端能保留任务和文档级错误。
- 问答结果必须返回答案、来源卡片和召回 chunk。
- 用户可以查看召回 chunk 的完整内容，用于核验回答依据。

### 3.2 质量验收

- 后端 `mvn test` 通过。
- 前端 `npm test` 和 `npm run build` 通过。
- 新增 RAG 回归脚本可读取 `data/evaluation/labor_qa_examples.json` 并输出评估报告。
- 至少覆盖 10 条劳动用工示例问题的检索来源命中和关键词命中。
- 新增 pgvector 集成测试，验证按知识库过滤和 Top-K 排序。

### 3.3 文档验收

- README 中补充完整演示步骤、常见失败原因和评测命令。
- 补充 API 示例请求。
- 补充 embedding 维度变更和重建数据库 volume 的说明。
- 补充法律风险、资料来源和模型幻觉边界说明。

## 4. 里程碑安排

### M1：基线验证与开发体验

目标：确保当前系统能稳定启动、测试和演示。

主要任务：

- 跑通后端测试、前端测试和前端构建。
- 跑通 Docker Compose smoke test。
- 修复启动、配置、测试或构建中的确定性问题。
- 补充 `.env.example` 说明。
- 整理 README 的本地开发和演示流程。

验收命令：

```bash
cd backend && mvn test
cd frontend && npm test && npm run build
docker compose up --build
```

产出：

- 可复现启动说明。
- 已知问题列表。
- 最小演示流程记录。

### M2：RAG 评测与检索质量

目标：让 RAG 效果可以被重复评估。

主要任务：

- 基于 `data/evaluation/labor_qa_examples.json` 新增评测脚本。
- 评测脚本至少输出：
  - 问题 ID。
  - Top-K 召回来源。
  - 期望来源命中情况。
  - 期望关键词命中情况。
  - 总体通过率。
- 后端增加可测试的检索服务边界，避免评测脚本直接耦合控制器细节。
- 为检索增加最低相似度阈值配置，低于阈值时返回“知识库没有足够依据”。
- 保留来源编号 `[来源1]`、`[来源2]` 的回答约束。

建议新增配置：

```yaml
legal-rag:
  min-score: ${LEGAL_RAG_MIN_SCORE:0.0}
```

建议通过环境变量覆盖：

```bash
LEGAL_RAG_TOP_K=8
LEGAL_RAG_MIN_SCORE=0.2
```

产出：

- `data/evaluation` 下的评测脚本或后端测试入口。
- 评测报告示例。
- README 中新增评测命令。

### M3：建库可靠性

目标：建库过程失败可定位、可重试、不会因为单个文档完全破坏演示。

主要任务：

- 将单文档失败与任务整体失败区分开。
- 支持建库任务继续处理后续文档，并在任务结果中统计失败文档数。
- embedding 请求按批次发送，避免一次请求过大。
- 对模型 API 请求增加 timeout。
- 对 429、5xx、网络超时增加有限重试和退避。
- 在任务状态中保留最后错误摘要。
- 文档删除时同步清理 chunk 和上传文件。

建议新增字段：

```sql
alter table ingest_jobs
    add column if not exists failed_documents integer not null default 0;
```

建议保留当前进程内异步执行方式，不在本阶段引入消息队列。原因是当前目标是本地演示稳定性，不是多节点生产调度。

产出：

- 更明确的任务状态。
- 单文档失败场景测试。
- 模型 API 失败场景测试。

### M4：pgvector 集成测试

目标：验证数据库层行为，而不是只测试纯 Java 逻辑。

主要任务：

- 引入 Testcontainers PostgreSQL + pgvector。
- 测试 Flyway 迁移能完整执行。
- 测试 chunk 写入和向量检索。
- 测试不同知识库之间检索隔离。
- 测试删除文档后 chunk 被清理。

建议测试类：

```text
backend/src/test/java/com/locallegalrag/repository/RagChunkRepositoryIntegrationTest.java
backend/src/test/java/com/locallegalrag/service/IngestServiceIntegrationTest.java
```

注意事项：

- 集成测试可以用固定小维度 embedding 专用 schema，或者继续使用 1536 维测试向量。
- 如果继续使用 1536 维，测试工具应生成确定性的稀疏向量，避免大段硬编码。

产出：

- pgvector 检索集成测试。
- 数据库迁移测试。

### M5：前端工作台补强

目标：让演示者能看懂系统状态，并能核验答案来源。

主要任务：

- 上传文档时显示上传进度。
- 建库任务失败时显示任务错误和文档错误。
- 增加 chunk 预览抽屉，展示召回原文、分数、页码、条文信息。
- 增加知识库重命名和删除。
- 增加空状态和错误重试入口。
- 优化移动端和窄屏布局，但仍以桌面工作台为主。

页面原则：

- 保持工作台风格，不做营销页。
- 信息密度优先，来源和状态优先。
- 不隐藏错误，不用泛化的“操作失败”替代具体原因。

产出：

- 完整演示路径：创建知识库 -> 上传 -> 建库 -> 提问 -> 查看来源 -> 查看 chunk。
- 前端 API 测试覆盖新增接口。
- 前端构建通过。

### M6：文档、风险和发布清单

目标：让项目具备交付说明，明确能力边界。

主要任务：

- README 补充 API 示例。
- 新增生产部署注意事项。
- 新增法律风险说明。
- 新增数据合规说明。
- 新增 release checklist。
- 更新 CHANGELOG。

建议新增文档：

```text
docs/API_EXAMPLES.md
docs/DEPLOYMENT_NOTES.md
docs/LEGAL_AND_DATA_RISKS.md
docs/RELEASE_CHECKLIST.md
```

产出：

- 文档闭环。
- 发布前检查清单。

## 5. 工作包拆分

### WP1：测试与基线修复

范围：

- 后端单元测试。
- 前端测试和构建。
- Docker Compose smoke test。
- README 启动说明。

涉及文件：

- `backend/pom.xml`
- `backend/src/test/java/com/locallegalrag/**`
- `frontend/package.json`
- `frontend/src/**/*.test.ts`
- `README.md`
- `.env.example`

完成标准：

- 推荐验证命令全部通过。
- README 的启动步骤可以被新开发者复现。

### WP2：RAG 评测

范围：

- 读取 `data/evaluation/labor_qa_examples.json`。
- 调用本地 API 或后端测试服务完成评测。
- 输出结构化结果。

建议优先级：

1. 先做离线检索评测，只评估召回来源。
2. 再做端到端问答评测，评估答案关键词。
3. 最后引入人工验收记录。

涉及文件：

- `data/evaluation/labor_qa_examples.json`
- `backend/src/main/java/com/locallegalrag/service/QuestionService.java`
- `backend/src/test/java/com/locallegalrag/**`
- `README.md`

完成标准：

- 能看到每条问题是否命中期望来源。
- 能看到整体通过率。
- 不要求模型回答每次字面完全一致。

### WP3：建库任务可靠性

范围：

- 任务统计增强。
- 单文档失败隔离。
- embedding 批处理。
- API timeout/retry。

涉及文件：

- `backend/src/main/java/com/locallegalrag/service/IngestService.java`
- `backend/src/main/java/com/locallegalrag/service/EmbeddingService.java`
- `backend/src/main/java/com/locallegalrag/service/OpenAiCompatibleClient.java`
- `backend/src/main/java/com/locallegalrag/repository/IngestJobRepository.java`
- `backend/src/main/resources/db/migration/`

完成标准：

- 一个坏文档不会阻止其他文档入库。
- 模型 API 临时失败时有有限重试。
- 前端能看到失败文档和失败原因。

### WP4：前端核验体验

范围：

- 上传进度。
- 任务错误详情。
- chunk 预览抽屉。
- 知识库管理。

涉及文件：

- `frontend/src/views/KnowledgeBaseDetailView.vue`
- `frontend/src/views/KnowledgeBaseListView.vue`
- `frontend/src/api/client.ts`
- `frontend/src/stores/knowledgeBases.ts`
- `frontend/src/assets/styles.css`

完成标准：

- 用户可以从回答来源跳到对应 chunk 内容。
- 用户可以判断回答是否有足够依据。
- 失败状态清晰可见。

### WP5：文档和发布

范围：

- API 示例。
- 部署说明。
- 风险说明。
- 发布检查清单。

涉及文件：

- `README.md`
- `CHANGELOG.md`
- `docs/API_EXAMPLES.md`
- `docs/DEPLOYMENT_NOTES.md`
- `docs/LEGAL_AND_DATA_RISKS.md`
- `docs/RELEASE_CHECKLIST.md`

完成标准：

- 项目能力边界清楚。
- 演示流程、验证命令、常见失败原因都有文档。

## 6. 技术决策

### 6.1 暂不引入消息队列

当前建库任务用进程内异步执行即可。下一阶段只补任务状态、失败隔离和重试。引入 RabbitMQ、Kafka 或 Redis Queue 会增加部署复杂度，不符合本地 MVP 目标。

### 6.2 优先做评测，不优先换模型

模型质量问题不能只靠换模型解决。应先建立评测集和指标，再根据结果调整 chunk、Top-K、Prompt、rerank 或模型。

### 6.3 保留 OpenAI-compatible 接口

继续使用 OpenAI-compatible API，避免绑定单一供应商。但需要补充 timeout、retry、错误分类和维度校验。

### 6.4 权限系统继续暂缓

本阶段不实现登录和多租户。若要部署到共享环境，必须先单独立项安全边界，不应直接暴露当前 API。

## 7. 风险清单

### 7.1 法律准确性风险

风险：模型可能基于不完整资料生成看似合理但没有依据的回答。

缓解：

- 所有回答必须展示来源。
- 低置信度时拒答。
- 文档中明确系统不替代律师意见。
- 示例资料标注为演示资料，不作为正式法律文本。

### 7.2 资料时效风险

风险：法规摘录可能过期、缺失或非现行有效。

缓解：

- 文档元数据增加来源、发布日期、采集日期、有效状态字段的设计预留。
- README 和风险文档说明必须使用权威、完整、可追溯来源。

### 7.3 数据泄露风险

风险：上传文档内容会发送给外部模型 API 做 embedding 和问答。

缓解：

- 前端不展示 API Key。
- 文档说明外部模型调用边界。
- 后续生产化前必须加入模型供应商合规审查、脱敏和访问控制。

### 7.4 演示不稳定风险

风险：外部模型 API 不可用、网络失败或 key 未配置导致演示中断。

缓解：

- 启动页或配置页显示 API Key 配置状态。
- 模型请求增加 timeout/retry。
- README 增加常见错误排查。

## 8. 推荐实施顺序

1. 先跑通基线验证，记录当前失败项。
2. 修复确定性测试、构建和 Docker 启动问题。
3. 做 RAG 评测脚本，建立质量基线。
4. 补 pgvector 集成测试，防止检索逻辑回退。
5. 改建库任务可靠性。
6. 补前端错误和来源核验体验。
7. 补文档和发布清单。

不要先做知识库美化、复杂权限或模型切换。这些工作会掩盖当前最核心的问题：系统结果是否可靠、失败是否可见、流程是否可复现。

## 9. 发布检查清单

发布下一阶段版本前，必须确认：

- [ ] `cd backend && mvn test` 通过。
- [ ] `cd frontend && npm test && npm run build` 通过。
- [ ] `docker compose up --build` 可以启动。
- [ ] 能创建知识库。
- [ ] 能上传 `data/documents/formal/` 示例资料。
- [ ] 能完成建库任务。
- [ ] 提问“试用期工资有什么要求？”能返回来源。
- [ ] RAG 评测脚本能输出报告。
- [ ] README、CHANGELOG 和风险文档已更新。
- [ ] `.env`、上传文件、数据库 volume、模型输出缓存没有被提交。

## 10. 后续生产化方向

下一阶段完成后，再考虑生产化路线：

- 登录、权限和知识库隔离。
- 文档来源治理和法规有效性管理。
- 联网法律检索和定期更新。
- 审计日志、监控、备份和灾备。
- 私有化模型或合规模型网关。
- 大规模异步任务队列。
- 混合检索和 rerank 服务化。

这些方向应分别立项，不建议一次性混入当前工程化 MVP 迭代。
