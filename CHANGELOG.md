# 更新日志

## 2026-06-10

- 将默认 MVP 演示配置调整为 `hash` Embedding 与 `extractive` 回答模式，保证未配置本地大模型时也能完成 Chroma 建库和问答烟测。
- 完成 Chroma 与 FAISS 的本机烟测对比，并在 `NEXT_PHASE_TASKS.md` 记录耗时、磁盘占用和检索结果。
- 为 `retrieve` 增加假向量库单元测试，覆盖打分、回退和异常不吞掉的场景。
- 改进坏路径、无支持文件、缺失向量库、损坏 PDF 等异常提示，CLI 统一输出明确的 `错误：...` 信息。
- 更新 `.gitignore`，避免 Chroma/FAISS 本地向量库目录被提交。
