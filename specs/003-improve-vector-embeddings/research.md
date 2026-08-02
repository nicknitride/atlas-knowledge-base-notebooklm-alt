# Phase 0 Research: Vector Embedding & Rebuild Architecture

**Feature**: `003-improve-vector-embeddings`  
**Date**: 2026-08-02  

## Overview & Goals

This research evaluates the technical approach for enforcing real, honest vector embeddings, independent embedding configuration, workspace index rebuilding, and accurate document searchability state tracking in Atlas.

---

## Technical Decisions

### Decision 1: Embedding Identity & Compatibility Contract

- **Decision**: Define Embedding Identity strictly as the pair `(embedding_model, embedding_dimensions)`:
  - Active identity is read from `AtlasProperties` (`atlas.provider.ollama.embedding-model` and `atlas.provider.ollama.embedding-dimensions`).
  - Stored at the workspace level (`workspaces.embedding_model`, `workspaces.embedding_dimensions`) and stamped when indexing completes.
  - Stored/tracked at the document level (`documents.embedding_model`, `documents.embedding_dimensions`) so each document can be compared against the currently active identity.
- **Rationale**: Combining model name and dimensions guarantees exact vector compatibility. A change to either field invalidates vector cosine distance operations in pgvector.
- **Alternatives Considered**:
  - *Storing vector hashes*: Unnecessary complexity; model name + dimension uniquely identifies the vector space.
  - *Parallel multi-model index tables*: Violates Simplicity principle (YAGNI/KISS). Spec explicitly calls for single active identity with rebuild capability.

---

### Decision 2: Document Indexing Health Status (`READY`, `STALE`, `PENDING`, `FAILED`)

- **Decision**: Extend document status calculation to combine `ingestion_status` and embedding identity comparison:
  - `PENDING` / `PROCESSING`: Ingestion job currently running or queued.
  - `READY`: Ingestion status `COMPLETE` AND document embedding identity matches active system configuration.
  - `STALE`: Ingestion status `COMPLETE` BUT document embedding identity differs from active system configuration (or is null).
  - `FAILED`: Ingestion status `FAILED` or embedding generation error occurred.
- **Rationale**: Frontend needs clear status to explain why retrieval might be disabled or why a rebuild action is prompted, fulfilling FR-008 and User Story 3.
- **Alternatives Considered**:
  - *Workspace-only status*: Doesn't allow users to pinpoint which specific documents failed or need rebuilding in a mixed-state workspace.

---

### Decision 3: Rebuild Workflow Execution

- **Decision**: Implement synchronous/asynchronous workspace index rebuild via `POST /api/workspaces/{id}/rebuild`:
  - 1. Set workspace rebuild status and mark target documents as `PROCESSING`/`INDEXING`.
  - 2. For each document, clear existing `document_chunks`.
  - 3. Re-read source document text from file storage repository (`FileStorageService`).
  - 4. Re-chunk text using `DocumentExtractor` / `TextChunker`.
  - 5. Generate fresh embeddings via `EmbeddingProvider.embedAll()` under active configuration.
  - 6. Save new `document_chunks` with new embedding vectors.
  - 7. Update document `embedding_model` and `embedding_dimensions` to active config and set status to `COMPLETE`.
  - 8. Upon successful completion of all documents, stamp workspace embedding identity to active config.
  - 9. If any document fails (e.g. Ollama unreachable or model missing), record document status as `FAILED` and return summary response indicating partial or total failure.
- **Rationale**: Re-indexing from stored original document files allows users to switch embedding models locally (e.g. from `nomic-embed-text` to `mxbai-embed-large`) without re-uploading source files.
- **Alternatives Considered**:
  - *Storing raw chunks permanently*: Original files are already preserved in file storage; re-extracting guarantees chunking strategy improvements can also apply during rebuild.

---

### Decision 4: Honest Embedding Failures & Strict Non-Fallback

- **Decision**: Eliminate any possibility of fallback to deterministic/hash vectors or mock similarity scores in non-test runtime:
  - If Ollama embedding endpoint is unreachable or returns HTTP error, `DefaultEmbeddingProvider` throws `ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE")`.
  - If embedding model returns empty array or wrong dimension count (e.g., returned 1024 when 768 configured), throw `ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_MISCONFIGURED")`.
  - Ingestion workflow MUST catch embedding failure, set document `ingestion_status = 'FAILED'`, record error message, and NOT write partial zero/placeholder chunks.
  - `VectorSearchService` MUST fail honestly when query embedding fails or when workspace is `STALE` without rebuild.
- **Rationale**: Satisfies FR-003, FR-004, FR-005, SC-002, SC-003, and User Story 2 ("Honest embedding failures"). Placeholder vectors create false confidence and break grounded retrieval trust.
- **Alternatives Considered**:
  - *Falling back to keyword search when vectors fail*: Out of scope per spec assumptions; spec requires honest vector failures rather than hybrid fallback hiding embedding outage.

---

### Decision 5: Configuration Decoupling (Chat vs Embedding)

- **Decision**: Ensure properties and environment variables strictly separate LLM chat settings from Embedding settings:
  - Chat: `atlas.provider.ollama.chat-model` / `ATLAS_OLLAMA_CHAT_MODEL` (default: `llama3`)
  - Embedding: `atlas.provider.ollama.embedding-model` / `ATLAS_OLLAMA_EMBEDDING_MODEL` (default: `nomic-embed-text`)
  - Dimensions: `atlas.provider.ollama.embedding-dimensions` / `ATLAS_OLLAMA_EMBEDDING_DIMENSIONS` (default: `768`)
  - Changing `chat-model` has ZERO effect on `embedding-model` or document index health (FR-010, SC-005).
- **Rationale**: Users routinely swap chat models (e.g. llama3 -> mistral) without needing to re-embed gigabytes of vector data.

---

## Risk & Mitigation Matrix

| Risk | Impact | Mitigation Strategy |
|------|--------|---------------------|
| Ollama stopped during rebuild | Partial workspace rebuild failure | Transactional per-document rebuild; set failed documents to `FAILED` with explicit error message; allow retry. |
| Vector dimension mismatch in pgvector column | Database SQL query exception | Database column configured for 768d by default (Flyway V3); pre-flight validation in `DefaultEmbeddingProvider` checks returned vector length against expected dimensions before DB insert. |
| Large workspace rebuild timeout | HTTP request timeout | Return HTTP 202 Accepted with background job ID or stream rebuild progress via job endpoint if needed; synchronous batch for small workspaces. |
