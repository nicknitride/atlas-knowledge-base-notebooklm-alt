---
description: "Task list for Improve Backend Reliability"
---

# Tasks: Improve Backend Reliability

**Input**: Design documents from `/specs/002-improve-backend/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: MANDATORY per Atlas constitution (I. Test-First). Every user-story
phase MUST include failing test tasks before implementation tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Spring Boot API: `backend/src/main/java/dev/atlas/`, `backend/src/main/resources/`, `backend/src/test/java/dev/atlas/`
- Config/docs: `backend/src/main/resources/application.yml`, `.env.example`, `README.md`, `docker-compose.yml`
- Optional consumer sync: `lib/api.ts` (error shape only; no UI redesign)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Align local config docs and confirm test entrypoint — no product behavior yet

- [x] T001 Verify backend tests run via `cd backend && mvn test` (or `./mvnw test`) and note baseline failures in a short comment at top of `backend/src/test/java/dev/atlas/documents/IngestionWorkflowTest.java` only if a suite is already red
- [x] T002 [P] Update `.env.example` for local-first defaults: `ATLAS_PROVIDER_TYPE=local` (or `ollama`), `ATLAS_OLLAMA_URL`, chat model, and placeholders for embedding model + max upload per `specs/002-improve-backend/research.md` R2/R3
- [x] T003 [P] Add a short “Backend reliability” pointer in `README.md` linking to `specs/002-improve-backend/quickstart.md` (no implementation yet)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared API error envelope + configurable atlas properties + embedding-identity schema that ALL stories use

**⚠️ CRITICAL**: No user story implementation until this phase is complete

### Error envelope (TDD)

- [x] T004 Write failing tests for stable JSON error body `{code,message,requestId?}` in `backend/src/test/java/dev/atlas/support/ApiErrorHandlerTest.java` per `specs/002-improve-backend/contracts/api-errors.md`
- [x] T005 Implement `ApiError` record + `@ControllerAdvice` mapping in `backend/src/main/java/dev/atlas/support/ApiError.java` and `backend/src/main/java/dev/atlas/support/ApiErrorHandler.java` (map `ResponseStatusException` / domain errors; preserve `requestId` from `backend/src/main/java/dev/atlas/support/RequestCorrelationFilter.java`)

### Config properties

- [x] T006 [P] Add configurable properties in `backend/src/main/resources/application.yml`: `atlas.max-upload-bytes` (default 83886080), `atlas.ingestion.processing-timeout` (default `10m`), `atlas.provider.ollama.embedding-model` (default `nomic-embed-text`), multipart `max-file-size`/`max-request-size` aligned to 80MB
- [x] T007 [P] Create `@ConfigurationProperties` (or `@Value`-backed) settings type in `backend/src/main/java/dev/atlas/support/AtlasProperties.java` bound to the keys in T006

### Embedding identity schema

- [x] T008 Write failing persistence/repository test that workspace can store `embedding_model` + `embedding_dimensions` in `backend/src/test/java/dev/atlas/workspaces/WorkspaceEmbeddingIdentityTest.java` per `specs/002-improve-backend/data-model.md`
- [x] T009 Add Flyway migration `backend/src/main/resources/db/migration/V4__embedding_identity_and_job_started_at.sql` (workspace columns; optional `ingestion_jobs.started_at`) and update `backend/src/main/java/dev/atlas/workspaces/Workspace.java` (+ `IngestionJob.java` if `started_at` added)

**Checkpoint**: Error envelope + config + schema ready — user stories may proceed

---

## Phase 3: User Story 1 - Trustworthy document ingestion (Priority: P1) 🎯 MVP

**Goal**: Reliable upload → status → COMPLETE/FAILED with 80 MB default, timeout path, delete mid-ingest cleanup, UI-pollable status (FR-001–003, FR-012)

**Independent Test**: Upload valid/invalid sources; terminal status with reason; delete while PROCESSING leaves no retrievable residue; successful docs become COMPLETE (quickstart A/B/E)

### Tests for User Story 1 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T010 [P] [US1] Failing contract/integration tests for empty / oversize (default 80 MB) / unsupported upload → 400 + codes `UPLOAD_EMPTY` / `UPLOAD_TOO_LARGE` / `UPLOAD_UNSUPPORTED_TYPE` in `backend/src/test/java/dev/atlas/documents/DocumentUploadValidationTest.java` per `contracts/ingestion.md` and `contracts/api-errors.md`
- [x] T011 [P] [US1] Failing test that delete during PROCESSING removes document and yields zero chunks for that id in `backend/src/test/java/dev/atlas/documents/IngestionDeleteCleanupTest.java`
- [x] T012 [P] [US1] Failing test that PROCESSING beyond configured timeout becomes FAILED with reason in `backend/src/test/java/dev/atlas/documents/IngestionTimeoutTest.java`
- [x] T013 [P] [US1] Extend/fail assertions in `backend/src/test/java/dev/atlas/documents/IngestionWorkflowTest.java` for COMPLETE success path + FAILED never marked COMPLETE on extract/embed error

### Implementation for User Story 1

- [x] T014 [US1] Enforce configurable max upload (default 80 MB) and typed error codes in `backend/src/main/java/dev/atlas/documents/DocumentController.java` (replace hard-coded 25 MB); keep list response fields `status` + `failureReason` for UI indicators
- [x] T015 [US1] On document delete in `backend/src/main/java/dev/atlas/documents/DocumentController.java` + `IngestionService.java`, abort in-flight job safely (no re-insert after row gone); rely on CASCADE for chunks/jobs per research R6
- [x] T016 [US1] Implement PROCESSING timeout / terminalization in `backend/src/main/java/dev/atlas/documents/IngestionService.java` (use `started_at` or equivalent + `atlas.ingestion.processing-timeout`); extend startup recovery in `recoverOrphanedJobs` so jobs cannot stick forever (FR-002)
- [x] T017 [US1] On FAILED mid-ingest, delete any partial `document_chunks` for that document in `backend/src/main/java/dev/atlas/documents/IngestionService.java` before/with markFailed
- [x] T018 [US1] Stamp workspace embedding identity on successful COMPLETE ingest in `backend/src/main/java/dev/atlas/documents/IngestionService.java` (model + dimensions from config/provider) per data-model.md
- [x] T019 [US1] Ensure ingest/provider failures set user-safe `failureReason` without document body payloads in logs in `backend/src/main/java/dev/atlas/documents/IngestionService.java`

**Checkpoint**: US1 MVP — upload/status/delete/timeout independently demonstrable

---

## Phase 4: User Story 2 - Correct grounded answers with honest failure modes (Priority: P1)

**Goal**: Fail-closed providers/retrieval; COMPLETE-only grounding; real citations; no fake similarity or offline placeholder success (FR-004–007, FR-011)

**Independent Test**: In-scope citations map to real passages; provider down → clear failure ≤15s; no COMPLETE-pending leakage; mismatch → `EMBEDDING_CONFIG_MISMATCH` (quickstart C/D/F/G)

### Tests for User Story 2 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T020 [P] [US2] Failing tests that configured Ollama/embedding failure does NOT return deterministic hash vectors as success in `backend/src/test/java/dev/atlas/providers/DefaultEmbeddingProviderTest.java`
- [x] T021 [P] [US2] Failing tests that LLM provider failure does NOT invoke offline `generateFallback`/`streamFallback` as success in `backend/src/test/java/dev/atlas/providers/DefaultLlmProviderFailClosedTest.java`
- [x] T022 [P] [US2] Failing tests that vector search does NOT fabricate similarity 0.75 via ILIKE fallback on error in `backend/src/test/java/dev/atlas/retrieval/VectorSearchServiceTest.java`
- [x] T023 [P] [US2] Failing tests that search/chat only use `ingestion_status=COMPLETE` documents in `backend/src/test/java/dev/atlas/retrieval/CompleteOnlyRetrievalTest.java`
- [x] T024 [P] [US2] Failing tests for embedding identity mismatch → clear failure (no silent search) in `backend/src/test/java/dev/atlas/retrieval/EmbeddingMismatchTest.java`
- [x] T025 [P] [US2] Failing citation assertions (documentId/filename + snippet required) in `backend/src/test/java/dev/atlas/chat/GroundedChatServiceTest.java` per `contracts/grounded-chat.md`
- [x] T026 [P] [US2] Failing cross-workspace isolation assertions remain green/extended in `backend/src/test/java/dev/atlas/workspaces/WorkspaceIsolationTest.java` (SC-004)

### Implementation for User Story 2

- [x] T027 [US2] Remove/disable silent `embedDeterministic` fallback after configured provider failure in `backend/src/main/java/dev/atlas/providers/DefaultEmbeddingProvider.java`; fail with typed exception; stop truncating vectors to hard-coded 1536 when DB is 768 — use configured dimensions / actual embedding length
- [x] T028 [US2] Make `DefaultLlmProvider` fail-closed in `backend/src/main/java/dev/atlas/providers/DefaultLlmProvider.java`: on configured provider error/timeout, call `onError` / throw — do not `streamFallback`/`generateFallback` as a successful grounded answer (FR-007 / SC-003; surface within 15s via timeouts)
- [x] T029 [US2] Filter retrieval to COMPLETE documents only and remove fake-similarity ILIKE success path in `backend/src/main/java/dev/atlas/retrieval/VectorSearchService.java`; on failure throw `RETRIEVAL_UNAVAILABLE` / propagate
- [x] T030 [US2] Before search/chat, enforce embedding identity match (workspace vs config) in `backend/src/main/java/dev/atlas/retrieval/VectorSearchService.java` and/or `backend/src/main/java/dev/atlas/chat/GroundedChatService.java`; mismatch → `EMBEDDING_CONFIG_MISMATCH`
- [x] T031 [US2] Ensure `CitationResponse` path in `backend/src/main/java/dev/atlas/chat/GroundedChatService.java` always populates document id/name + snippet; never invent citations when chunks empty
- [x] T032 [US2] Map provider/retrieval/mismatch exceptions to API/SSE error outcomes in `backend/src/main/java/dev/atlas/chat/ConversationController.java` (no synthetic success payload)

**Checkpoint**: US2 independently testable — honest failures + trustworthy citations

---

## Phase 5: User Story 3 - Predictable errors the app can surface (Priority: P2)

**Goal**: Consistent codes/messages across workspace, document, and chat operations (FR-008)

**Independent Test**: Missing resources, invalid uploads, provider-down each yield non-success with stable `code` + UI-safe `message` (quickstart + contract codes table)

### Tests for User Story 3 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T033 [P] [US3] Failing contract tests for `NOT_FOUND` on missing workspace/document/conversation in `backend/src/test/java/dev/atlas/support/NotFoundErrorContractTest.java`
- [x] T034 [P] [US3] Failing contract tests for `PROVIDER_UNAVAILABLE` / `PROVIDER_MISCONFIGURED` on chat when backend down in `backend/src/test/java/dev/atlas/chat/ProviderErrorContractTest.java`
- [x] T035 [P] [US3] Failing test that error JSON never includes stack traces or internal hostnames in `backend/src/test/java/dev/atlas/support/ApiErrorSafetyTest.java`

### Implementation for User Story 3

- [x] T036 [US3] Ensure `WorkspaceController`, `DocumentController`, and `ConversationController` under `backend/src/main/java/dev/atlas/` throw/map codes from `contracts/api-errors.md` (not opaque 500s for expected cases)
- [x] T037 [US3] Align SSE/stream error signaling with the same codes/messages in `backend/src/main/java/dev/atlas/chat/ConversationController.java` so the client can show error state
- [x] T038 [US3] Optionally sync error parsing helpers in `lib/api.ts` to read `{code,message,requestId}` without UI redesign (only if current client would swallow the new body)

**Checkpoint**: App-actionable errors stable across core operations

---

## Phase 6: User Story 4 - Configurable local AI path stays first-class (Priority: P2)

**Goal**: `local`/Ollama path works end-to-end without cloud keys; models/URL configurable (FR-009, FR-010)

**Independent Test**: Local-only `.env` (no cloud keys) completes upload → index → grounded chat when Ollama + models available (quickstart A with local provider)

### Tests for User Story 4 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T039 [P] [US4] Failing test that `ATLAS_PROVIDER_TYPE=local` uses Ollama-compatible embed/chat endpoints (not hash embeddings) in `backend/src/test/java/dev/atlas/providers/LocalProviderRoutingTest.java`
- [x] T040 [P] [US4] Failing test that embedding model name is read from config (not hard-coded only) in `backend/src/test/java/dev/atlas/providers/DefaultEmbeddingProviderTest.java`

### Implementation for User Story 4

- [x] T041 [US4] Route `local` provider type to Ollama URL/models in `backend/src/main/java/dev/atlas/providers/DefaultLlmProvider.java` and `DefaultEmbeddingProvider.java` (alias of ollama path per research R2)
- [x] T042 [US4] Wire configurable embedding model (+ dimensions) from `AtlasProperties` / `application.yml` into `backend/src/main/java/dev/atlas/providers/DefaultEmbeddingProvider.java` (replace hard-coded `"nomic-embed-text"` only / fix gemini→ollama mis-route if still present)
- [x] T043 [US4] Document local-only setup (Ollama pull models, env vars, 80 MB limit, re-index on embedding change) in `README.md` and `.env.example`; keep `docker-compose.yml` defaults local-first without requiring `ATLAS_GEMINI_API_KEY`

**Checkpoint**: Local-only path is the documented acceptance baseline

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Performance, privacy, YAGNI, validation against quickstart

- [x] T044 [P] Privacy pass: confirm logs in `IngestionService`, `DefaultLlmProvider`, `DefaultEmbeddingProvider`, `GroundedChatService` omit document bodies and secrets
- [x] T045 [P] YAGNI/KISS pass on `backend/src/main/java/dev/atlas/` — no new queue/service frameworks; remove dead fallback code paths
- [x] T046 Run full `cd backend && mvn test` and fix regressions for SC-006 journeys (upload→index→chat, missing resource, provider down, mismatch)
- [x] T047 Manually execute `specs/002-improve-backend/quickstart.md` scenarios A–G against `docker compose` + host Ollama; note results
- [x] T048 [P] Confirm SC-001 (≤2 min sample ingest) and SC-003 (≤15s provider-down failure) against measured local run; adjust timeouts in `application.yml` only if needed without weakening fail-closed behavior

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — **BLOCKS** all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational — 🎯 MVP
- **User Story 2 (Phase 4)**: Depends on Foundational; benefits from US1 COMPLETE docs for E2E but independently testable with fixtures
- **User Story 3 (Phase 5)**: Depends on Foundational error envelope; should run after US1/US2 throw the domain exceptions it maps
- **User Story 4 (Phase 6)**: Depends on Foundational config; overlaps providers with US2 — implement after US2 fail-closed changes to avoid rework
- **Polish (Phase 7)**: After desired stories complete

### User Story Dependencies

- **US1 (P1)**: After Phase 2 — no dependency on US2–US4
- **US2 (P1)**: After Phase 2 — uses COMPLETE docs + embedding identity from US1 when doing full E2E; unit/fixture tests can proceed in parallel
- **US3 (P2)**: After Phase 2 + preferably after US1/US2 exception types exist
- **US4 (P2)**: After Phase 2 + preferably after US2 provider fail-closed work

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Services before controller wiring where applicable
- Story complete before moving to next priority (unless parallel staffing)

### Parallel Opportunities

- Phase 1: T002/T003 in parallel
- Phase 2: T006/T007 in parallel after T005 started; T004→T005 sequential; T008→T009 sequential
- US1 tests T010–T013 in parallel; then T014–T019 mostly sequential on `IngestionService`/`DocumentController`
- US2 tests T020–T026 in parallel; T027/T028 can proceed in parallel (different provider files); T029–T032 after
- US3 tests T033–T035 in parallel
- US4 tests T039–T040 in parallel
- Polish T044/T045/T048 in parallel after T046

---

## Parallel Example: User Story 1

```bash
# Launch US1 failing tests together:
Task: "DocumentUploadValidationTest.java — empty/oversize/unsupported"
Task: "IngestionDeleteCleanupTest.java — delete during PROCESSING"
Task: "IngestionTimeoutTest.java — PROCESSING → FAILED"
Task: "IngestionWorkflowTest.java — COMPLETE / FAILED assertions"

# Then implement sequentially on shared ingest/upload files:
Task: "DocumentController upload limit + codes"
Task: "IngestionService delete abort + timeout + chunk cleanup + identity stamp"
```

## Parallel Example: User Story 2

```bash
# Launch US2 failing tests together:
Task: "DefaultEmbeddingProviderTest — no deterministic success fallback"
Task: "DefaultLlmProviderFailClosedTest — no offline success fallback"
Task: "VectorSearchServiceTest — no fake ILIKE similarity"
Task: "CompleteOnlyRetrievalTest / EmbeddingMismatchTest / citation + isolation"

# Parallel implementation (different files):
Task: "DefaultEmbeddingProvider.java fail-closed + dimensions"
Task: "DefaultLlmProvider.java fail-closed"
# Then:
Task: "VectorSearchService.java COMPLETE-only + no ILIKE fake"
Task: "GroundedChatService + ConversationController error mapping"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Upload/status/delete/timeout independently (quickstart A/B/E)
5. Demo MVP ingestion reliability

### Incremental Delivery

1. Setup + Foundational → shared errors/config/schema
2. US1 → trustworthy ingest (MVP)
3. US2 → honest grounded chat (P1 critical trust)
4. US3 → polish error contracts for the app
5. US4 → local/Ollama first-class config + docs
6. Polish → quickstart A–G + `mvn test` green

### Parallel Team Strategy

1. Team completes Setup + Foundational together
2. Dev A: US1 | Dev B: US2 test fixtures (merge carefully on providers)
3. After US1/US2: Dev A: US3 | Dev B: US4
4. Polish together

---

## Notes

- [P] = different files, no incomplete-task dependencies
- Do not expand into feature 003 embedding-quality redesign beyond fail-closed + identity (research R10)
- UI chrome for progress indicators is out of scope; API `status`/`failureReason` must be sufficient (FR-013)
- Verify tests fail before implementing; commit after each task or logical group
- Suggested MVP scope: **US1 only**, then US2 before any release claiming “grounded” trust
