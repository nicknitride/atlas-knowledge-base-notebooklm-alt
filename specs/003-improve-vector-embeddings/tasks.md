# Tasks: Improve Vector Embeddings

**Input**: Design documents from `/specs/003-improve-vector-embeddings/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: MANDATORY per Atlas constitution (I. Test-First). Every user-story phase MUST include failing test tasks before implementation tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `backend/src/main/java/dev/atlas/`, `backend/src/test/java/dev/atlas/`
- **Frontend**: `app/`, `components/`, `lib/`, `__tests__/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Infrastructure setup and verification

- [x] T001 Verify project structure and spec design artifacts in `specs/003-improve-vector-embeddings/`
- [x] T002 [P] Verify pgvector database extension and Flyway migrations in `backend/src/main/resources/db/migration/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core model and property adjustments required before user story work

- [x] T003 [P] Update `KnowledgeDocument.java` entity to include `embedding_model` and `embedding_dimensions` fields
- [x] T004 [P] Decouple `embedding-model` and `embedding-dimensions` configuration properties from chat model settings in `AtlasProperties.java`
- [x] T005 [P] Update database repository queries to return document-level embedding identity in `DocumentRepository.java`

**Checkpoint**: Foundation ready - user story implementation can begin ✅

---

## Phase 3: User Story 1 - Indexed sources are findable with real embeddings (Priority: P1) 🎯 MVP

**Goal**: Store real semantic vector embeddings during document ingestion and execute exact cosine search during query time with zero stand-in or hash fallback.

**Independent Test**: With local Ollama running `nomic-embed-text` and a fixture document containing distinctive facts, ask questions hitting those facts; verify retrieved passages have high semantic similarity and match the fixture content.

### Tests for User Story 1 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T006 [P] [US1] Unit test for real embedding generation in `DefaultEmbeddingProviderTest.java`
- [x] T007 [P] [US1] Integration test for writing real passage vectors during ingest in `IngestionWorkflowTest.java`
- [x] T008 [P] [US1] Integration test for semantic vector retrieval in `VectorSearchServiceTest.java`

### Implementation for User Story 1

- [x] T009 [P] [US1] Implement strict real vector embedding extraction in `DefaultEmbeddingProvider.java`
- [x] T010 [US1] Update `IngestionService.java` to insert real passage embeddings and stamp identity upon completion
- [x] T011 [US1] Update `VectorSearchService.java` to query pgvector using active embedding identity

**Checkpoint**: User Story 1 is functional - real embeddings index and retrieve correctly ✅

---

## Phase 4: User Story 2 - Honest embedding failures (Priority: P1)

**Goal**: Fail ingestion, retrieval, and search honestly with HTTP 503 error envelopes when embedding backend is down, missing, or misconfigured. Zero stand-in vectors or fake similarity scores.

**Independent Test**: Stop local Ollama endpoint; attempt ingest and query search; verify clear 503 error responses, no zero-length or fake similarity chunks inserted, and document state set to `FAILED`.

### Tests for User Story 2 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T012 [P] [US2] Contract test for `PROVIDER_UNAVAILABLE` and `PROVIDER_MISCONFIGURED` error envelopes in `DefaultEmbeddingProviderTest.java`
- [x] T013 [P] [US2] Integration test for honest ingestion failure when embedding endpoint is offline in `IngestionWorkflowTest.java`
- [x] T014 [P] [US2] Integration test for honest search failure (HTTP 503) when query embedding fails in `VectorSearchServiceTest.java`

### Implementation for User Story 2

- [x] T015 [P] [US2] Enforce non-fallback exception throwing (no mock/hash vector fallbacks) in `DefaultEmbeddingProvider.java`
- [x] T016 [US2] Catch embedding exceptions during ingest, mark document `ingestion_status = FAILED`, and omit partial chunks in `IngestionService.java`
- [x] T017 [US2] Catch query vector embedding exceptions and return 503 `RETRIEVAL_UNAVAILABLE` error response in `VectorSearchService.java`

**Checkpoint**: User Stories 1 AND 2 operate honestly with strict failure signaling ✅

---

## Phase 5: User Story 3 - Configure embeddings independently and rebuild (Priority: P2)

**Goal**: Allow independent configuration of embedding settings, surface document health states (`READY`, `STALE`, `PENDING`, `FAILED`), and provide index rebuild capabilities (`POST /api/workspaces/{id}/rebuild`).

**Independent Test**: Switch system embedding model in config; verify document index health indicates `STALE`; trigger rebuild; verify documents re-embed under new identity and transition back to `READY`.

### Tests for User Story 3 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T018 [P] [US3] Unit test for document health status derivation in `DocumentHealthStatusTest.java`
- [ ] T019 [P] [US3] Contract test for `GET /api/workspaces/{id}/index-health` in `WorkspaceControllerTest.java`
- [ ] T020 [P] [US3] Contract test for `POST /api/workspaces/{id}/rebuild` in `WorkspaceControllerTest.java`
- [x] T021 [P] [US3] Integration test for workspace document re-indexing in `RebuildServiceTest.java`

### Implementation for User Story 3

- [x] T022 [P] [US3] Create `DocumentHealthStatus` enum and health calculator in `DocumentHealthStatus.java`
- [x] T023 [US3] Implement `RebuildService.java` to re-extract, chunk, and embed documents from file storage
- [x] T024 [US3] Add `/api/workspaces/{id}/index-health` and `/api/workspaces/{id}/rebuild` endpoints in `WorkspaceController.java`
- [x] T025 [P] [US3] Add TypeScript types for `DocumentHealthStatus`, `IndexHealthResponse`, `RebuildResponse` in `lib/api.ts`
- [x] T026 [P] [US3] Add `getIndexHealth` and `rebuildWorkspaceIndex` API functions in `lib/api.ts`
- [x] T027 [P] [US3] Create `IndexHealthBadge` component in `components/workspace/index-health-badge.tsx`
- [x] T028 [US3] Create `RebuildIndexDialog` component with rebuild progress/trigger UI in `components/workspace/rebuild-index-dialog.tsx`

**Checkpoint**: User Story 3 complete - index health display and rebuild workflow fully functional ✅

---

## Phase 6: User Story 4 - Clear default: real local embeddings for knowledge search (Priority: P2)

**Goal**: Document and provide real local embeddings by default using Ollama `nomic-embed-text`, with clear user guidance if model is not pulled.

**Independent Test**: Start Ollama without `nomic-embed-text`; attempt indexing; verify error message clearly instructs user to pull `nomic-embed-text`.

### Tests for User Story 4 (REQUIRED — write first, ensure FAIL) ⚠️

- [ ] T029 [P] [US4] Unit test for missing Ollama model error guidance in `DefaultEmbeddingProviderTest.java`
- [ ] T030 [P] [US4] Component test for embedding configuration warning alert in `__tests__/components/workspace-detail.test.tsx`

### Implementation for User Story 4

- [x] T031 [P] [US4] Verified default properties set `nomic-embed-text` and 768 dimensions in `application.yml`
- [x] T032 [US4] Explicit model installation message ("Is 'nomic-embed-text' pulled in Ollama?") already present in `DefaultEmbeddingProvider.java`
- [x] T033 [US4] Index health section with status badge and rebuild button added to sidebar Docs tab in `components/sidebar.tsx`

**Checkpoint**: All user stories functional and verified

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, privacy review, and quickstart validation

- [ ] T034 [P] Update `README.md` with instructions for independent embedding model configuration and rebuild workflow
- [ ] T035 [P] Audit logs in `DefaultEmbeddingProvider.java` to ensure zero document text or secret exfiltration
- [ ] T036 Run end-to-end quickstart scenarios in `specs/003-improve-vector-embeddings/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - starts immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: Depend on Foundational phase completion
  - US1 (P1 MVP) → US2 (P1 Failure Signaling) → US3 (P2 Rebuild) → US4 (P2 Defaults & Guidance)
- **Polish (Phase 7)**: Depends on all user story phases being complete

### Parallel Opportunities

- All tests marked [P] within a phase can run in parallel
- Models and DTOs marked [P] can run in parallel
- Backend and Frontend tasks marked [P] in Phase 5 (US3) can run in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & 2 (Setup & Foundational)
2. Complete Phase 3 (User Story 1 - Real embeddings for ingest & search)
3. Test User Story 1 independently with local Ollama fixture document

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. User Story 1 → Real embeddings (MVP!)
3. User Story 2 → Honest failure envelopes
4. User Story 3 → Health status & index rebuild
5. User Story 4 → Local defaults & Ollama pull guidance
6. Polish & Quickstart Validation
