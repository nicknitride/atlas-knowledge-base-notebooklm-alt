# Implementation Plan: Fix Rebuild Duplicate-Chunk Violation

**Branch**: `006-fix-rebuild-duplicate-chunk` | **Date**: 2026-08-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-fix-rebuild-duplicate-chunk/spec.md`

---

## Summary

During workspace rebuild, `IngestionService.executeJob()` inserts new `document_chunks` rows starting at `ordinal = 0` without first deleting the existing chunks for that document. On a second rebuild, the first `INSERT` violates the `UNIQUE(document_id, ordinal)` constraint. PostgreSQL aborts the transaction, causing all subsequent statements — including the failure-path `DELETE` — to fail with "current transaction is aborted", ultimately surfacing as `UnexpectedRollbackException`.

The fix has three parts:
1. **Delete before insert**: Add `DELETE FROM document_chunks WHERE document_id = ?` at the start of the success path in `executeJob()`, inside the same transaction as the inserts.
2. **Transaction isolation**: Remove `@Transactional` from `RebuildService.rebuildWorkspace()` so each document's ingestion runs in its own independent transaction (the existing `@Transactional` on `executeJob()` is sufficient).
3. **Concurrency guard**: Add an in-process `ConcurrentHashMap<UUID, ReentrantLock>` in `RebuildService` to prevent two concurrent rebuilds for the same workspace; return HTTP 409 if a rebuild is already in progress.

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.9 (spring-boot-starter-data-jpa, spring-boot-starter-web), Spring JDBC (`JdbcTemplate`), Spring `@Transactional`

**Storage**: PostgreSQL via JPA + raw `JdbcTemplate`; Flyway migrations (no schema change needed)

**Testing**: JUnit 5 + Mockito (via `spring-boot-starter-test`); Testcontainers available but not used for unit tests

**Target Platform**: Local Docker Compose (single-node, single JVM process)

**Project Type**: Web service / REST API (Spring Boot)

**Performance Goals**: Rebuild latency increase ≤ 20% (one additional `DELETE` per document, negligible for local stack)

**Constraints**: Must not remove or weaken `UNIQUE(document_id, ordinal)` constraint; fix must work within the existing schema

**Scale/Scope**: Single-workspace rebuilds with up to hundreds of documents; single JVM (no distributed locking needed)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Verify against `.specify/memory/constitution.md` (Atlas v1.0.0+):

- **I. Test-First** ✅ — Three new failing test classes (`executeJobDeletesExistingChunksBeforeInserting`, `RebuildIdempotencyTest`, `RebuildConcurrencyTest`) are defined before any implementation task in `tasks.md`.
- **II. Local-First / Ollama** ✅ — Fix operates entirely on local PostgreSQL + Spring; no cloud dependency added.
- **III. Performance & UX** ✅ — One extra `DELETE` per document per rebuild: negligible latency. UX: rebuild UI already shows in-progress/success/failure; 409 response is surfaced by existing error handler.
- **IV. Organization & Grounded Synthesis** ✅ — Fix preserves chunk → document → workspace provenance; no citation/retrieval logic changed.
- **V. Privacy** ✅ — No content is logged or exfiltrated; error messages reference document IDs, not content.
- **VI. Configurability** ✅ — No new hard-coded values; concurrency guard uses in-process lock with no user-visible knob (correct default for single-node deployment).
- **VII. Simplicity** ✅ — Minimal change: one `DELETE` line, `@Transactional` annotation removal, one `ConcurrentHashMap` field. No new services, tables, or abstractions.

**No Complexity Tracking entries required.**

---

## Project Structure

### Documentation (this feature)

```text
specs/006-fix-rebuild-duplicate-chunk/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   └── rebuild-api-contract.md   ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit-tasks — not yet created)
```

### Source Code (affected files)

```text
backend/src/main/java/dev/atlas/documents/
├── IngestionService.java          ← MODIFY: add DELETE before INSERT in executeJob()
└── RebuildService.java            ← MODIFY: remove @Transactional; add concurrency guard

backend/src/test/java/dev/atlas/documents/
├── IngestionWorkflowTest.java     ← MODIFY: add test for delete-before-insert
├── RebuildIdempotencyTest.java    ← NEW: idempotent rebuild test
└── RebuildConcurrencyTest.java    ← NEW: concurrent rebuild 409 test
```

**Structure Decision**: Option 2 (Web application — existing backend layout). No new packages or files in main source; test additions follow existing Mockito unit-test pattern in `backend/src/test/java/dev/atlas/documents/`.

---

## Phase 0: Research

See [research.md](./research.md) — all NEEDS CLARIFICATION resolved.

**Key decisions**:
| Decision | Chosen Approach | Rationale |
|----------|----------------|-----------|
| Fix strategy | Delete-then-insert | Simplest correct fix within existing schema |
| Transaction scope | Remove outer `@Transactional` from `rebuildWorkspace` | Prevents single-doc failure from aborting entire rebuild |
| Concurrency guard | In-process `ConcurrentHashMap<UUID, ReentrantLock>` | Sufficient for single-JVM deployment; no DB round-trip |
| Upsert (`ON CONFLICT`) | Deferred | Not needed once delete-then-insert is in place |

---

## Phase 1: Design & Contracts

### data-model.md — [data-model.md](./data-model.md)

**No schema changes.** `UNIQUE(document_id, ordinal)` constraint preserved. In-process concurrency state (the lock map) is JVM-only, no DB column.

### contracts — [rebuild-api-contract.md](./contracts/rebuild-api-contract.md)

**New behaviour**: `POST /api/workspaces/{id}/rebuild` returns **HTTP 409** with `{"error":"REBUILD_IN_PROGRESS","message":"..."}` when a rebuild is already in progress for the same workspace. All other status codes and response shapes are unchanged.

### quickstart.md — [quickstart.md](./quickstart.md)

Three end-to-end validation scenarios: idempotent rebuild, concurrent rebuild guard, and rebuild after document content change.

---

## Detailed Change Descriptions

### 1. `IngestionService.executeJob()` — Add DELETE before chunk INSERT

**File**: `backend/src/main/java/dev/atlas/documents/IngestionService.java`

**Current code** (line 150):
```java
int ordinal = 0;
for (DocumentExtractor.ExtractedSection section : sections) {
    for (String chunkText : chunk(section.content())) {
        // ... INSERT into document_chunks
    }
}
```

**After fix** — insert the DELETE immediately before the ordinal counter:
```java
jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", document.id());
int ordinal = 0;
for (DocumentExtractor.ExtractedSection section : sections) {
    for (String chunkText : chunk(section.content())) {
        // ... INSERT into document_chunks (unchanged)
    }
}
```

This single `DELETE` executes within the `@Transactional` of `executeJob()`, atomically clearing old chunks before inserting new ones.

### 2. `RebuildService.rebuildWorkspace()` — Remove outer @Transactional

**File**: `backend/src/main/java/dev/atlas/documents/RebuildService.java`

**Current**: Method annotated `@Transactional` (line 105).

**After fix**: Remove `@Transactional`. Each per-document `executeJob()` call runs in its own Spring-managed transaction (the `@Transactional` on `executeJob` handles that). Removing the outer annotation ensures that a failure in one document's transaction does not abort others.

### 3. `RebuildService` — Add concurrency guard

**File**: `backend/src/main/java/dev/atlas/documents/RebuildService.java`

Add a field:
```java
private final ConcurrentHashMap<UUID, ReentrantLock> workspaceRebuildLocks = new ConcurrentHashMap<>();
```

In `rebuildWorkspace(UUID workspaceId)`:
```java
ReentrantLock lock = workspaceRebuildLocks.computeIfAbsent(workspaceId, id -> new ReentrantLock());
if (!lock.tryLock()) {
    throw new ApiException(HttpStatus.CONFLICT, "REBUILD_IN_PROGRESS",
        "A rebuild is already in progress for this workspace. Please wait and retry.");
}
try {
    // ... existing rebuild logic
} finally {
    lock.unlock();
}
```

### 4. Tests — Three test additions

#### `IngestionWorkflowTest` — new test (added to existing file)
```
executeJobDeletesExistingChunksBeforeInserting:
  Given a document with existing chunks (simulated by the fact that DELETE should be called)
  When executeJob() runs successfully
  Then jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", docId)
       is called BEFORE any INSERT call
```

#### `RebuildIdempotencyTest` — new file
```
rebuildTwiceSucceedsBothTimes:
  Given documentRepository returns one document
  And ingestionService.executeJob() succeeds both times
  When rebuildWorkspace() is called twice in sequence
  Then both invocations return status "COMPLETED" with failedCount == 0
```

#### `RebuildConcurrencyTest` — new file
```
concurrentRebuildReturnConflict:
  Given rebuildWorkspace is running on a background thread (lock held)
  When a second rebuildWorkspace() call arrives on the main thread
  Then ApiException with CONFLICT / "REBUILD_IN_PROGRESS" is thrown
```

---

## Complexity Tracking

*No constitution violations — no entries required.*

