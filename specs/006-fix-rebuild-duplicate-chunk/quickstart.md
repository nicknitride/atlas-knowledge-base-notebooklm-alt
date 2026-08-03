# Quickstart / Validation Guide: Fix Rebuild Duplicate-Chunk Violation

**Feature**: 006-fix-rebuild-duplicate-chunk  
**Date**: 2026-08-02

---

## Prerequisites

- Docker Compose stack running: `docker compose up --build -d`
- At least one workspace created and at least one document uploaded and fully ingested (status = COMPLETE)

---

## Scenario 1 — Idempotent Rebuild (P1)

Validates that rebuilding an already-indexed workspace succeeds with no constraint violations.

```bash
# 1. Get workspace ID
curl -s http://localhost:8080/api/workspaces | jq '.[0].id'

# 2. First rebuild (may already be done from initial upload)
curl -s -X POST http://localhost:8080/api/workspaces/{WORKSPACE_ID}/rebuild | jq .

# 3. Second rebuild — this is the regression test
curl -s -X POST http://localhost:8080/api/workspaces/{WORKSPACE_ID}/rebuild | jq .
```

**Expected outcome for step 3**:

```json
{
  "status": "COMPLETED",
  "failedCount": 0,
  "errors": []
}
```

**Failure indicator (pre-fix)**: HTTP 500 with `DuplicateKeyException` in server logs.

---

## Scenario 2 — Concurrent Rebuild Guard (P3)

Validates that a second simultaneous rebuild returns 409 instead of crashing.

```bash
# Send two rebuild requests simultaneously
curl -s -X POST http://localhost:8080/api/workspaces/{WORKSPACE_ID}/rebuild &
curl -s -X POST http://localhost:8080/api/workspaces/{WORKSPACE_ID}/rebuild

# One should return 200 COMPLETED; the other should return:
# HTTP 409 with body: {"error":"REBUILD_IN_PROGRESS","message":"..."}
```

---

## Scenario 3 — Rebuild with Changed Document (P2)

Validates idempotency when a document's content length differs between rebuilds.

1. Upload a short document (e.g. 100 words) → confirm ingestion COMPLETE
2. Trigger rebuild → confirm COMPLETED
3. Delete the document and re-upload a longer version (e.g. 2000 words, same name)
4. Trigger rebuild → confirm COMPLETED with no chunk ordinal conflicts

---

## Running the Unit Tests

```bash
cd backend
./mvnw test -pl . -Dtest="IngestionWorkflowTest,RebuildServiceTest,RebuildIdempotencyTest,RebuildConcurrencyTest"
```

**Expected**: All tests GREEN. Specifically, `IngestionWorkflowTest#executeJobDeletesExistingChunksBeforeInserting` must pass (verifying DELETE is called before INSERT).

---

## Checking Logs for Clean Rebuild

After a rebuild, search the API logs for constraint violations:

```bash
docker logs atlas_knowledge_base-api-1 2>&1 | grep -i "duplicate\|constraint\|rollback"
```

**Expected**: Zero matches for a successful rebuild.
