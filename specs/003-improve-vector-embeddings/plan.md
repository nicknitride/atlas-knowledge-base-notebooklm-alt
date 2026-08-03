# Implementation Plan: Improve Vector Embeddings

**Branch**: `003-improve-vector-embeddings` | **Date**: 2026-08-02 | **Spec**: [specs/003-improve-vector-embeddings/spec.md](file:///Users/nicholopardines/Desktop/Github_Projects/atlas_knowledge_base/specs/003-improve-vector-embeddings/spec.md)

**Input**: Feature specification from `/specs/003-improve-vector-embeddings/spec.md`

## Summary

This feature eliminates non-semantic stand-in / placeholder vectors and fake similarity scores, decoupling embedding configuration from chat model settings, enforcing honest failure signaling when embedding backends are down or misconfigured, and implementing a workspace index rebuild mechanism to re-embed existing documents under a new embedding identity.

---

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.5.9 backend), TypeScript 5.7 (Next.js 16 / React 19 frontend)  
**Primary Dependencies**: Spring Data JPA, JdbcTemplate, Flyway (pgvector extension), Jackson, Testcontainers, Next.js, Lucide React  
**Storage**: PostgreSQL with pgvector (vector 768d default), local file storage (`FileStorageService`)  
**Testing**: JUnit 5, Mockito, Testcontainers (backend), Vitest, React Testing Library (frontend)  
**Target Platform**: Local stack (macOS / Linux / Docker Compose)  
**Project Type**: Web application (Java REST backend + Next.js frontend)  
**Performance Goals**: Short document indexing <= 2 min; small workspace rebuild (<= 5 documents) <= 5 min on standard local hardware  
**Constraints**: Local-first with Ollama embeddings; strict privacy (no content exfiltration); explicit error envelopes for failures  
**Scale/Scope**: Self-hosted knowledge workspace

---

## Constitution Check

_GATE: Passed before Phase 0 research. Re-evaluated post-design._

Verify against `.specify/memory/constitution.md` (Atlas v1.0.0+):

- **I. Test-First**: Plan includes TDD test suite coverage (`DefaultEmbeddingProviderTest`, `IngestionServiceTest`, `VectorSearchServiceTest`, `WorkspaceRebuildTest`, frontend unit tests). Failing tests will precede implementation in `tasks.md`.
- **II. Local-First / Ollama**: Default path uses local Ollama (`nomic-embed-text`, 768d) with pgvector. No cloud credentials required.
- **III. Performance & UX**: Response performance targets defined. Index health states (`READY`, `STALE`, `PENDING`, `FAILED`) surfaced to frontend UI with clear user feedback.
- **IV. Organization & Grounded Synthesis**: Workspace isolation strictly maintained (`d.workspace_id = ?`). Citation provenance preserved.
- **V. Privacy**: All embeddings generated locally via Ollama. No exfiltration of document text or secrets.
- **VI. Configurability**: Embedding endpoint, model, and dimensions (`ATLAS_OLLAMA_EMBEDDING_MODEL`, `ATLAS_OLLAMA_EMBEDDING_DIMENSIONS`) configurable independently from chat model (`ATLAS_OLLAMA_CHAT_MODEL`).
- **VII. Simplicity**: Single active embedding identity per workspace with on-demand rebuild. No redundant abstractions or parallel multi-model tables.

---

## Project Structure

### Documentation (this feature)

```text
specs/003-improve-vector-embeddings/
├── plan.md              # Implementation plan
├── research.md          # Phase 0 technical research & decisions
├── data-model.md        # Phase 1 entity definitions & state machine
├── quickstart.md        # Phase 1 end-to-end validation guide
├── contracts/           # Phase 1 API and error envelope contracts
│   ├── workspace-rebuild-api.md
│   └── embedding-failure-contracts.md
└── tasks.md             # Phase 2 implementation tasks (generated via /speckit-tasks)
```

### Source Code Layout

```text
backend/
└── src/
    ├── main/java/dev/atlas/
    │   ├── documents/
    │   │   ├── Document.java                     # Added embedding_model & embedding_dimensions fields
    │   │   ├── DocumentHealthStatus.java          # Derived enum: READY, STALE, PENDING, FAILED
    │   │   ├── IngestionService.java              # Honest embedding error handling
    │   │   └── RebuildService.java                # [NEW] Workspace re-indexing logic
    │   ├── providers/
    │   │   └── DefaultEmbeddingProvider.java      # Strict non-fallback Ollama embedding provider
    │   ├── retrieval/
    │   │   └── VectorSearchService.java           # Strict vector search with config mismatch check
    │   ├── workspaces/
    │   │   ├── Workspace.java
    │   │   ├── WorkspaceController.java           # [NEW] /api/workspaces/{id}/index-health & /rebuild
    │   │   └── WorkspaceLookup.java               # Embedding identity validation
    │   └── support/
    │       └── AtlasProperties.java               # Decoupled chat vs embedding config
    └── test/java/dev/atlas/
        ├── documents/
        │   ├── IngestionWorkflowTest.java
        │   └── RebuildServiceTest.java            # [NEW] Rebuild service unit tests
        ├── providers/
        │   └── DefaultEmbeddingProviderTest.java  # Strict failure & dimension validation tests
        ├── retrieval/
        │   └── VectorSearchServiceTest.java       # Conflict and 503 test cases
        └── workspaces/
            └── WorkspaceRebuildIntegrationTest.java # [NEW] Integration test suite

components/
├── workspace/
│   ├── index-health-badge.tsx                    # [NEW] Displays READY/STALE/PENDING/FAILED badge
│   └── rebuild-index-dialog.tsx                  # [NEW] Rebuild confirm & execution UI component
lib/
├── api-client.ts                                 # Updated API client for index-health and rebuild
└── types.ts                                      # Updated Document & Workspace types for health status
```

**Structure Decision**: Web application layout (backend Java Spring Boot + frontend Next.js React components).

---

## Complexity Tracking

| Violation | Why Needed                                                                         | Simpler Alternative Rejected Because |
| --------- | ---------------------------------------------------------------------------------- | ------------------------------------ |
| _None_    | Rebuild workflow and document health status use existing storage and API patterns. | N/A                                  |
