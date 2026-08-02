# Research: Fix Rebuild Duplicate-Chunk Violation

**Feature**: 006-fix-rebuild-duplicate-chunk  
**Date**: 2026-08-02

---

## Finding 1 — Root Cause: Missing Pre-Delete in executeJob()

**Decision**: The fix MUST add a `DELETE FROM document_chunks WHERE document_id = ?` immediately before the chunk-insert loop inside `executeJob()`.

**Rationale**: `IngestionService.executeJob()` (lines 150–178) inserts chunks starting at `ordinal = 0` without removing any existing chunks first. On a rebuild, `document_chunks` already contains rows for the document, so the first `INSERT` at `ordinal = 0` violates `UNIQUE(document_id, ordinal)`. PostgreSQL aborts the transaction; every subsequent statement in the same connection (including the `DELETE` inside `failDocument()`) fails with "current transaction is aborted".

**Evidence**:
- `executeJob()` line 171: raw `jdbc.update("INSERT INTO document_chunks ...")` — no preceding delete
- `failDocument()` line 209: `jdbc.update("DELETE FROM document_chunks WHERE document_id = ?")` — cleanup exists **only** on the failure path, not the success path

**Alternatives considered**:
- Upsert (`INSERT ... ON CONFLICT DO UPDATE`): Valid, but more complex SQL and changes semantics. Deferred unless delete-then-insert proves insufficient.
- Schema change (remove unique constraint): Rejected — the constraint is correct and necessary for retrieval integrity.

---

## Finding 2 — Transaction Boundary Mismatch Amplifies the Bug

**Decision**: Each document's ingestion should run in its **own independent transaction**, not the outer `rebuildWorkspace` transaction.

**Rationale**: `RebuildService.rebuildWorkspace()` is annotated `@Transactional`. It calls `ingestionService.executeJob(job.id())` in a loop. Because `executeJob` also has `@Transactional`, Spring's default `REQUIRED` propagation makes it join the outer transaction. A `DuplicateKeyException` in the first document's chunks aborts the **entire** outer transaction — all remaining documents fail with "transaction is aborted".

**Fix**: Move per-document chunk clearing and ingestion so each document runs in its own `REQUIRES_NEW` sub-transaction, or (simpler) keep `rebuildWorkspace` non-transactional at the outer level and rely solely on per-document transactions in `executeJob`.

**Alternatives considered**:
- Propagation `REQUIRES_NEW` on `executeJob`: Achieves isolation but requires a Spring proxy boundary (self-invocation pitfall). The method is already called via a Spring proxy, so this is safe.
- Wrapping each document loop iteration in a try-catch and savepoint: More complex, PostgreSQL-specific; rejected in favour of standard Spring transaction semantics.

---

## Finding 3 — Concurrent Rebuild Race: Application-Level Guard

**Decision**: Use an in-memory `ConcurrentHashMap<UUID, Boolean>` lock (or `ReentrantLock`) keyed by `workspaceId` to prevent two concurrent rebuild requests from ingesting the same workspace simultaneously.

**Rationale**: `WorkspaceController.rebuild()` is synchronous and blocking. Two simultaneous HTTP requests can both enter `rebuildWorkspace`, both read the document list, and both create `IngestionJob` rows and call `executeJob` for the same documents at the same time. Even with a correct delete-before-insert, concurrent jobs produce duplicate inserts.

**Alternatives considered**:
- PostgreSQL advisory locks: Correct, but adds DB round-trip and requires careful lock release. Deferred unless in-process guard proves insufficient for the single-node deployment.
- Database-level SERIALIZABLE isolation: Too broad; hurts all reads.
- `synchronized(workspaceId.toString().intern())`: Fragile string interning. Rejected.

**Chosen approach**: A `ConcurrentHashMap<UUID, ReentrantLock>` (or `ConcurrentHashMap<UUID, Boolean>` with `putIfAbsent`) inside `RebuildService`. Return HTTP 409 Conflict if a rebuild is already in progress for the workspace.

---

## Finding 4 — Duplicate Document Processing in a Single Rebuild

**Decision**: No change needed here; `rebuildWorkspace` iterates `documentRepository.findByWorkspaceIdOrderByCreatedAtDesc()` which returns a `List<KnowledgeDocument>`. A JPA `List` result contains no duplicates by entity identity. The issue is not duplicate documents in one run — it is old chunks not being cleared before re-ingestion.

**Rationale**: The `findBy*` query returns each document once. The loop only processes each document once. Verified by reading the loop (lines 128–151 of `RebuildService`).

---

## Finding 5 — Test Strategy

**Decision**: Add new unit tests to `IngestionWorkflowTest` and a new `RebuildIdempotencyTest`, following the existing Mockito pattern.

**Rationale**: All existing tests use plain Mockito (no Spring context, no Testcontainers) for unit coverage. Idempotency (delete-before-insert) can be verified by asserting that `jdbc.update("DELETE FROM ...")` is called with the document ID before any `INSERT` call. Concurrency can be verified with a thread pool test in `RebuildServiceTest`.

**Test files to add / modify**:
1. `IngestionWorkflowTest` — add test: `executeJobDeletesExistingChunksBeforeInserting`
2. New `RebuildIdempotencyTest` — verify second rebuild of same workspace succeeds
3. New `RebuildConcurrencyTest` — verify second concurrent rebuild receives 409 or is blocked

