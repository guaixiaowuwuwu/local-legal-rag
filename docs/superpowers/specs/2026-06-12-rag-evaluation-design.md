# RAG Evaluation Design

## Goal

Make RAG retrieval quality repeatable to evaluate for the labor-law demo corpus, without coupling evaluation code to controller internals.

## Decisions

- Add a backend `RetrievalService` as the service boundary for question retrieval.
- Keep `QuestionService` responsible for answer orchestration: validate the question, call retrieval, build sources, and call chat only when enough context remains.
- Add `legal-rag.min-score`, defaulting to `0.0`, and expose it via `LEGAL_RAG_MIN_SCORE`.
- Apply the minimum score threshold after repository search. If all retrieved chunks are filtered out, return the existing no-context answer and do not call the chat model.
- Preserve the existing prompt requirement that answers cite numbered sources such as `[来源1]`.
- Add a standard-library Python evaluation script under `data/evaluation` that calls the running HTTP API.
- The script reads `labor_qa_examples.json`, asks each question, checks expected source paths in returned sources, checks expected keywords in `answer + retrievedChunks.content`, and writes a Markdown report.

## Evaluation Contract

The evaluation script requires:

- A running backend.
- A knowledge base ID that has already ingested the labor-law formal documents.
- Optional `topK`, defaulting to the backend default if not provided.

Each example report row includes:

- Question ID.
- Question text.
- Top-K recalled sources with source number, path/source, score, and article when available.
- Expected source hit status.
- Expected keyword hit status.
- Pass/fail status.

The summary includes total examples, passed examples, failed examples, and overall pass rate.

## Testing

- Unit-test `RetrievalService` for score threshold behavior.
- Unit-test `QuestionService` for no-context behavior when retrieval returns no chunks.
- Run backend Maven tests.
- Run the evaluation script in dry/sample mode by generating an example Markdown report from fixture-style data or documented sample output.
