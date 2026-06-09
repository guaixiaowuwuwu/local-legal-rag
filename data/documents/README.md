# 本地知识库资料目录规范

默认导入目录为 `data/documents/formal/`。该目录只放已完成来源、版本、发布日期、适用范围记录的正式知识库资料。

推荐结构：

```text
data/documents/
  formal/
    labor/
      national_law/
      administrative_regulation/
  examples/
```

## 正式资料

- `formal/{法律领域}/{效力层级}/` 存放正式资料。
- 首批 MVP 资料按劳动用工领域整理，效力层级包括 `national_law` 和 `administrative_regulation`。
- 每份资料文件开头必须记录：资料名称、资料类型、正文完整性、官方来源、版本/公布日期、施行日期、制定机关、适用范围、采集日期。
- 当前首批资料是“权威文本摘录版”，用于检索链路和问答回归验证，不代表已导入完整法律库。上线或正式评估前，应替换为完整、权威、可追溯文本并重建向量库。

## 示例资料

- `examples/` 只放演示用、虚构或未完成审核的材料。
- 示例资料不属于正式知识库资料；默认配置不会导入 `examples/`。
- 如需单独演示示例资料，请在命令行显式传入 `--docs-dir data/documents/examples`，不要与正式资料混用。

## 资料台账

正式资料的来源、版本、发布日期和适用范围记录在 `data/documents/FORMAL_SOURCES.md`。
