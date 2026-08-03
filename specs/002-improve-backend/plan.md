# Implementation Plan: Improve Backend Reliability

**Branch**: `002-improve-backend` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-improve-backend/spec.md`

## Summary

Harden the existing Spring Boot ingest → retrieve → grounded-chat pipeline so
local (Ollama) setups produce trustworthy results and honest failures. Primary
correctness work: remove silent LLM/embedding/search fallbacks that fabricate
“successful” grounded answers; enforce an 80 MB upload default with clear
status; ground only on `COMPLETE` documents; fail retrieval on embedding
identity mismatch until re-index; cancel/cleanup in-flight ingest on delete;
return consistent app-actionable API errors. Prefer fixing
`backend/src/main/java/dev/atlas/**` over new services. Overlaps with
`003-improve-vector-embeddings` are limited to fail-closed embeddings and
dimension identity—broader embedding quality stays in 003.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.9 (`backend/pom.xml`)

**Primary Dependencies**: Spring Web / WebFlux (WebClient for providers), Spring
Data JPA, Flyway, PostgreSQL + pgvector, Apache PDFBox; Testcontainers + JUnit 5
for API/service tests. Frontend (Next.js) consumes APIs only—UI redesign out of
scope (FR-013).

**Storage**: PostgreSQL 16 + pgvector (`document_chunks.embedding vector(768)`
per V3); local file storage under `atlas.storage-dir` / `ATLAS_STORAGE_DIR`

**Testing**: Maven Surefire (`backend/src/test/java`); existing suites under
`documents`, `chat`, `providers`, `retrieval`, `workspaces`—extend with TDD
before each user-story change. Frontend Vitest not required for this feature
except optional contract smoke against documented error shapes.

**Target Platform**: Local Docker Compose stack (`docker-compose.yml`: postgres,
api `:8080`, web `:3000`) + host Ollama (`ATLAS_OLLAMA_URL`)

**Project Type**: Web application — this feature is **backend/API reliability**
(Spring Boot); UI chrome for progress indicators is consumed via API status
fields (companion UI feature)

**Performance Goals**: Sample doc (≤5 pages) ingest success + chat-usable ≤ 2
min (SC-001); provider-down chat failure surfaced ≤ 15 s (SC-003); streaming
TTFT best-effort on local hardware

**Constraints**: Local-first / Ollama default; no required cloud keys; privacy
(no doc bodies/secrets in logs); YAGNI—no new microservice/queue framework;
default upload limit 80 MB (configurable); stuck `PROCESSING` must terminalize
via timeout/recovery (plan: 10 minutes, see research)

**Scale/Scope**: Single-user local deployment; touch existing controllers /
providers / ingestion / retrieval / chat services + Flyway if embedding identity
metadata requires a small schema add; document config in README / `.env.example`

## Constitution Check

_GATE: Must pass before Phase 0 research. Re-check after Phase 1 design._

| Gate                                      | Status | Notes                                                                                          |
| ----------------------------------------- | ------ | ---------------------------------------------------------------------------------------------- |
| **I. Test-First**                         | Pass   | Existing JUnit/Testcontainers; tasks will list failing tests before implementation per story   |
| **II. Local-First / Ollama**              | Pass   | Default `ATLAS_PROVIDER_TYPE` local path maps to Ollama-compatible endpoint; cloud opt-in only |
| **III. Performance & UX**                 | Pass   | SC-001/SC-003 targets; API exposes ingest progress/status + actionable errors for UI           |
| **IV. Organization & Grounded Synthesis** | Pass   | Workspace isolation; citations require document id/name + snippet; COMPLETE-only grounding     |
| **V. Privacy**                            | Pass   | No required third-party send; fail instead of silent cloud; logs avoid payloads                |
| **VI. Configurability**                   | Pass   | Ollama URL/models, upload max size, embedding model/dim via env/config                         |
| **VII. Simplicity**                       | Pass   | Fix existing pipeline; no new broker/service; Complexity Tracking empty                        |

**Post-design re-check**: Still Pass — contracts document API error/status
shapes and fail-closed provider behavior; data model adds optional embedding
identity fields only where needed for FR-004/SC-007; no unjustified frameworks.

## Project Structure

### Documentation (this feature)

```text
specs/002-improve-backend/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api-errors.md
│   ├── ingestion.md
│   └── grounded-chat.md
└── tasks.md             # /speckit-tasks — not created here
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/main/java/dev/atlas/
│   ├── AtlasApplication.java
│   ├── documents/          # upload, ingest jobs, extractor, storage
│   ├── chat/               # conversations, GroundedChatService, citations
│   ├── retrieval/          # VectorSearchService
│   ├── providers/          # DefaultLlmProvider, DefaultEmbeddingProvider
│   ├── workspaces/
│   └── support/            # CORS, correlation filter
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/       # Flyway V1–V3 (+ V4 if embedding identity)
└── src/test/java/dev/atlas/
    ├── documents/
    ├── chat/
    ├── providers/
    ├── retrieval/
    └── workspaces/

docker-compose.yml
.env.example
README.md
lib/api.ts                  # consumer of error/status shapes (minimal sync only)
```

**Structure Decision**: Keep the existing monorepo layout (Next.js UI +
`backend/` Spring Boot). All behavioral changes for this feature land in
`backend/` (plus docs/env examples). Do not add a separate reliability service.

## Complexity Tracking

> No constitution violations requiring justification.
