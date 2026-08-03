# Data Model: Fix Rebuild Duplicate-Chunk Violation

**Feature**: 006-fix-rebuild-duplicate-chunk  
**Date**: 2026-08-02

---

## No Schema Changes Required

This fix does not alter any database tables, columns, indexes, or constraints. The existing schema is correct; the bug is purely in application logic.

---

## Existing Entities (Relevant Subset)

### `documents` table — mapped to `KnowledgeDocument`

| Column                 | Type                 | Notes                                    |
| ---------------------- | -------------------- | ---------------------------------------- |
| `id`                   | UUID PK              | Auto-generated                           |
| `workspace_id`         | UUID FK → workspaces | Cascade delete                           |
| `ingestion_status`     | VARCHAR(32)          | PENDING / PROCESSING / COMPLETE / FAILED |
| `embedding_model`      | VARCHAR              | Set on successful ingest                 |
| `embedding_dimensions` | INTEGER              | Set on successful ingest                 |
| `failure_reason`       | TEXT                 | Set on failed ingest                     |

### `document_chunks` table

| Column           | Type                | Notes                                    |
| ---------------- | ------------------- | ---------------------------------------- |
| `id`             | UUID PK             | Auto-generated                           |
| `document_id`    | UUID FK → documents | Cascade delete                           |
| `ordinal`        | INTEGER             | 0-based position within document         |
| `content`        | TEXT                | Chunk text                               |
| `source_locator` | JSONB               | `{"location": "...", "filename": "..."}` |
| `embedding`      | vector(N)           | Dense float vector                       |

**Constraint preserved**: `UNIQUE(document_id, ordinal)` — remains intact.

**Invariant enforced by fix**: Before any `INSERT INTO document_chunks` for a given `document_id`, all existing rows with that `document_id` MUST be deleted in the same transaction.

### `ingestion_jobs` table — mapped to `IngestionJob`

| Column          | Type                | Notes                                     |
| --------------- | ------------------- | ----------------------------------------- |
| `id`            | UUID PK             | Auto-generated                            |
| `document_id`   | UUID FK → documents | —                                         |
| `status`        | VARCHAR(32)         | PENDING / PROCESSING / COMPLETED / FAILED |
| `error_message` | TEXT                | Set on failure                            |
| `started_at`    | TIMESTAMPTZ         | Set when job transitions to PROCESSING    |

---

## State Transitions (No Change)

```
Document:  PENDING → PROCESSING → COMPLETE
                              ↘ FAILED

IngestionJob: PENDING → PROCESSING → COMPLETED
                                 ↘ FAILED
```

**Fix impact on transitions**: The document state transition sequence is unchanged. The fix adds a side-effect at the start of the PROCESSING → COMPLETE path: old chunks are deleted before new ones are inserted.

---

## In-Process Concurrency State (New — Application-Level Only)

`RebuildService` adds a `ConcurrentHashMap<UUID, ReentrantLock> workspaceRebuildLocks` field (no DB column).

- Key: `workspaceId`
- Value: `ReentrantLock` (non-fair)
- Lifecycle: lock is acquired at start of `rebuildWorkspace`, released in `finally`
- Effect: second concurrent rebuild for same workspace blocks until lock is free OR receives 409 if non-blocking check is used
