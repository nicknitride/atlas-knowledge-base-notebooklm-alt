# Feature Specification: Fix Rebuild Duplicate-Chunk Violation

**Feature Branch**: `006-fix-rebuild-duplicate-chunk`

**Created**: 2026-08-02

**Status**: Draft

**Input**: User description: "During workspace rebuild, document ingestion fails with a `DuplicateKeyException` when inserting into `document_chunks`. The ingestion process attempts to insert a chunk with `(document_id, ordinal = 0)` that already exists, violating the unique constraint `document_chunks_document_id_ordinal_key`. This triggers a PostgreSQL transaction abort, causing all subsequent SQL statements to fail and ultimately an `UnexpectedRollbackException`. Fix the rebuild pipeline so it reliably removes or replaces existing chunks without violating the uniqueness constraint."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Rebuild Completes Without Error (Priority: P1)

A workspace owner triggers a full index rebuild via the application. The rebuild should complete successfully even when the workspace has been rebuilt before and chunks already exist in the database. The user expects a reliable, idempotent rebuild operation.

**Why this priority**: This is the root bug. Without a reliable rebuild, the entire index-refresh workflow is broken. All other improvements depend on the ingestion pipeline being stable.

**Independent Test**: Can be fully tested by triggering a rebuild on a workspace that has been previously indexed, then verifying that the rebuild completes with a success response and the workspace becomes queryable with updated chunks.

**Acceptance Scenarios**:

1. **Given** a workspace whose documents have already been ingested (chunks exist in the database), **When** a rebuild is triggered, **Then** the rebuild completes successfully with no constraint violation errors and the workspace reflects the current document content.
2. **Given** a rebuild in progress, **When** a second rebuild request arrives for the same workspace, **Then** the second request either queues safely behind the first or is rejected with a clear conflict status — it MUST NOT produce a duplicate-key violation.
3. **Given** a document whose chunk count changes between rebuilds (e.g., a longer document produces more chunks), **When** a rebuild is triggered, **Then** all new chunks are stored correctly with ordinals starting at 0 and no old-chunk orphans remain.

---

### User Story 2 — Rebuild Is Idempotent (Priority: P2)

A workspace owner can trigger a rebuild multiple times without any side-effects. Each rebuild should produce the same final database state regardless of how many previous rebuilds have occurred.

**Why this priority**: Idempotency is a correctness property. Without it, repeated rebuilds accumulate state corruption, making the first rebuild succeed but subsequent ones fail.

**Independent Test**: Can be tested by triggering three consecutive rebuilds and asserting that the chunk count and content after each rebuild is identical to the result after the first.

**Acceptance Scenarios**:

1. **Given** a workspace with 5 documents successfully indexed, **When** a rebuild is triggered a second time with no document changes, **Then** the resulting chunk count and chunk content are identical to the first run.
2. **Given** a rebuild that previously failed mid-way (partial chunk set written), **When** a new rebuild is triggered, **Then** the rebuild succeeds and leaves the database in a clean, complete state.

---

### User Story 3 — Concurrent Rebuild Safety (Priority: P3)

The system prevents two rebuild jobs from simultaneously processing the same document, eliminating the race condition that causes interleaved inserts and duplicate-key violations.

**Why this priority**: Even with a correct delete-before-insert strategy, concurrent jobs can re-introduce duplicates. Safety under concurrency is a correctness requirement.

**Independent Test**: Can be tested by sending two simultaneous rebuild requests and confirming only one succeeds (or both succeed sequentially) without any database constraint violations.

**Acceptance Scenarios**:

1. **Given** a rebuild is already in progress for workspace W, **When** a second rebuild request is received for workspace W, **Then** the second job does not begin document ingestion concurrently; it either waits or is rejected with an appropriate status code.
2. **Given** the rebuild job scheduler, **When** processing a batch of documents, **Then** each document is processed at most once per rebuild run (no duplicate document processing).

---

### Edge Cases

- What happens when the rebuild is triggered for a workspace with zero documents?
- How does the system handle a partial failure (one document fails to embed) — do other documents' chunks get committed or are all rolled back?
- What happens if the delete of old chunks succeeds but the insert of new chunks fails — is the document left with no chunks?
- How does the system behave when a document has been deleted from the workspace between two rebuilds?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The rebuild pipeline MUST delete all existing chunks for a document before inserting the replacement chunks for that document.
- **FR-002**: The delete and the insert of chunks for a single document MUST be atomic — either both complete or neither is committed.
- **FR-003**: The rebuild job MUST NOT process the same document more than once per rebuild invocation.
- **FR-004**: The system MUST prevent two concurrent rebuild jobs from ingesting the same document simultaneously.
- **FR-005**: Chunk ordinals for a document MUST always be generated starting from 0, applied only after old chunks for that document have been removed.
- **FR-006**: If an individual document's ingestion fails, the system MUST log the failure and continue processing remaining documents rather than aborting the entire rebuild.
- **FR-007**: After a rebuild, the set of chunks in the database MUST exactly match the chunks derived from the current document content — no stale or orphaned chunks from previous runs.

### Key Entities

- **Document**: A source file belonging to a workspace; has a unique identifier used as the foreign key for its chunks.
- **Document Chunk**: A sub-segment of a document stored with a `(document_id, ordinal)` pair that is globally unique. Ordinal starts at 0 within each document.
- **Rebuild Job**: A unit of work that re-ingests all documents in a workspace; may process documents sequentially or in a controlled batch.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A rebuild of a previously indexed workspace completes without any database constraint violation errors in 100% of invocations under normal conditions.
- **SC-002**: Triggering a rebuild three times in a row on the same unchanged workspace produces identical chunk counts and no errors on all three runs.
- **SC-003**: Two simultaneous rebuild requests for the same workspace result in zero duplicate-key violations in the database — at most one job proceeds with ingestion at a time.
- **SC-004**: A rebuild that encounters one failing document does not prevent the remaining documents from being successfully re-ingested; the partial failure is surfaced in the rebuild response or logs.
- **SC-005**: The rebuild completes within the same order-of-magnitude time as before the fix — no more than a 20% increase in wall-clock time for a representative workspace.

---

## Non-Functional Constraints *(mandatory for Atlas)*

- **Local-first**: The fix MUST operate entirely within the local stack (local database, local job execution) with no dependency on cloud services.
- **Privacy**: No document content is transmitted outside the local environment as a result of this fix.
- **Performance**: Rebuilds MUST complete in a time proportional to document count; the additional delete step MUST NOT introduce unbounded latency for large workspaces. Target: rebuild latency increases by no more than 20% compared to the current (broken) implementation.
- **UX**: The rebuild trigger UI MUST reflect in-progress, success, and failure states. If a rebuild fails, a meaningful error description MUST be surfaced — not a generic 500.
- **Configurability**: No new user-configurable settings are required; concurrency safeguards should be enforced by default with no opt-out.
- **Simplicity**: The fix MUST prefer the simplest correct approach (delete-then-insert or upsert within the existing schema) before introducing new tables, locks, or services.
- **TDD**: All acceptance scenarios MUST be represented as failing tests before the fix implementation is written.

---

## Assumptions

- The `document_chunks` table has an existing `UNIQUE(document_id, ordinal)` constraint that must be preserved — the fix works within this schema, not by removing the constraint.
- The current rebuild implementation lacks an explicit delete of existing chunks before inserting new ones; the fix will add this step.
- PostgreSQL is the database in use; transaction semantics follow standard PostgreSQL behavior.
- Concurrency control (preventing two jobs from processing the same document simultaneously) can be achieved without distributed locking — either via application-level guards or database advisory locks available in the local stack.
- Out-of-scope: Changes to the embedding model or chunk-splitting logic are not part of this fix. Only the transactional correctness of delete-then-insert is in scope.
- Out-of-scope: Migrating the schema to use upsert semantics (`ON CONFLICT DO UPDATE`) is a viable alternative but is deferred unless the delete-then-insert approach proves insufficient.
