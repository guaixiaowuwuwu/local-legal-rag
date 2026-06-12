# RAG Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add repeatable RAG evaluation for the labor-law examples and introduce a testable retrieval service boundary with minimum-score filtering.

**Architecture:** Backend retrieval moves into `RetrievalService`, while `QuestionService` remains the answer orchestration layer. Evaluation is a Python HTTP client script under `data/evaluation`, so it tests the running application path without importing backend internals.

**Tech Stack:** Spring Boot 3.5.x, Java 17, JUnit 5/Mockito, Python 3 standard library, Markdown report output.

---

### Task 1: Backend Retrieval Boundary

**Files:**
- Create: `backend/src/main/java/com/locallegalrag/service/RetrievalService.java`
- Create: `backend/src/test/java/com/locallegalrag/service/RetrievalServiceTest.java`
- Modify: `backend/src/main/java/com/locallegalrag/config/RagProperties.java`
- Modify: `backend/src/main/resources/application.yml`

- [x] Add failing tests for min-score filtering.
- [x] Add `minScore` configuration to `RagProperties` and `application.yml`.
- [x] Implement `RetrievalService.search`.
- [x] Run the targeted retrieval test.

### Task 2: Question Orchestration

**Files:**
- Modify: `backend/src/main/java/com/locallegalrag/service/QuestionService.java`
- Create: `backend/src/test/java/com/locallegalrag/service/QuestionServiceTest.java`

- [x] Add failing tests showing no-context responses do not call chat.
- [x] Refactor `QuestionService` to call `RetrievalService`.
- [x] Keep `[来源1]` source numbering behavior unchanged.
- [x] Run the targeted question-service test.

### Task 3: Evaluation Script and Report

**Files:**
- Create: `data/evaluation/evaluate_labor_rag.py`
- Create: `data/evaluation/reports/labor_rag_evaluation_example.md`

- [x] Implement CLI arguments for backend URL, knowledge base ID, examples path, top-K, and output path.
- [x] Call `POST /api/knowledge-bases/{id}/questions`.
- [x] Compute expected source hits, expected keyword hits, per-question pass/fail, and overall pass rate.
- [x] Write a Markdown report.
- [x] Add a representative example report.

### Task 4: Documentation and Verification

**Files:**
- Modify: `README.md`

- [x] Document `LEGAL_RAG_MIN_SCORE`.
- [x] Add evaluation setup and command examples.
- [x] Run `cd backend && mvn test`.
- [x] Run the Python script help command.
