# Labor RAG Evaluation Report

> 示例格式，非真实运行结果。请用 `evaluate_labor_rag.py` 连接已建库的本地后端生成实际报告。

- Generated At: `2026-06-12T00:00:00+00:00`
- Backend: `http://localhost:8080`
- Knowledge Base ID: `00000000-0000-0000-0000-000000000000`
- Examples: `data/evaluation/labor_qa_examples.json`
- Corpus Root: `data/documents/formal`
- Top-K: `8`
- Overall Pass Rate: **50.00%** (1/2)

## Summary

| Question ID | Source Hits | Keyword Hits | Result |
| --- | ---: | ---: | --- |
| labor-001 | 2/2 | 3/3 | PASS |
| labor-002 | 1/1 | 1/2 | FAIL |

## labor-001

**Question:** 劳动合同最晚应当在什么时候签订？

**Result:** PASS

### Top-K Recalled Sources

| Rank | Source ID | Source | Article | Score |
| ---: | --- | --- | --- | ---: |
| 1 | 来源1 | storage/uploads/.../laodong_hetong_fa_excerpt.md | 第十条 | 0.8721 |
| 2 | 来源2 | storage/uploads/.../laodong_hetong_fa_shishi_tiaoli_excerpt.md | 第六条 | 0.8244 |

### Expected Source Hits

- [x] labor/national_law/laodong_hetong_fa_excerpt.md
- [x] labor/administrative_regulation/laodong_hetong_fa_shishi_tiaoli_excerpt.md

### Expected Keyword Hits

- [x] 自用工之日起一个月内订立书面劳动合同
- [x] 超过一个月不满一年
- [x] 每月支付两倍的工资

## labor-002

**Question:** 劳动合同试用期最长可以约定多久？同一员工能约定几次试用期？

**Result:** FAIL

### Top-K Recalled Sources

| Rank | Source ID | Source | Article | Score |
| ---: | --- | --- | --- | ---: |
| 1 | 来源1 | storage/uploads/.../laodong_hetong_fa_excerpt.md | 第十九条 | 0.8610 |

### Expected Source Hits

- [x] labor/national_law/laodong_hetong_fa_excerpt.md

### Expected Keyword Hits

- [x] 试用期不得超过六个月
- [ ] 同一用人单位与同一劳动者只能约定一次试用期
