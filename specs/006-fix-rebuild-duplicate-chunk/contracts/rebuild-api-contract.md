# API Contract: Workspace Rebuild

**Feature**: 006-fix-rebuild-duplicate-chunk  
**Date**: 2026-08-02

---

## Endpoint

```
POST /api/workspaces/{id}/rebuild
```

**No request body required.**

---

## Response: Success (HTTP 200)

```json
{
  "workspaceId": "uuid",
  "status": "COMPLETED | PARTIAL_FAILURE | FAILED",
  "totalProcessed": 5,
  "rebuiltCount": 5,
  "failedCount": 0,
  "activeEmbeddingIdentity": {
    "model": "nomic-embed-text",
    "dimensions": 768
  },
  "errors": []
}
```

| Field            | Type   | Description                                                                                    |
| ---------------- | ------ | ---------------------------------------------------------------------------------------------- |
| `status`         | string | `COMPLETED` if all documents rebuilt; `PARTIAL_FAILURE` if some failed; `FAILED` if all failed |
| `totalProcessed` | int    | Number of documents attempted                                                                  |
| `rebuiltCount`   | int    | Successful rebuilds                                                                            |
| `failedCount`    | int    | Failed rebuilds                                                                                |
| `errors`         | array  | One entry per failed document                                                                  |

---

## Response: Rebuild Already In Progress (HTTP 409) — **New**

```json
{
  "error": "REBUILD_IN_PROGRESS",
  "message": "A rebuild is already in progress for this workspace. Please wait and retry."
}
```

**Trigger**: A second `POST /rebuild` arrives while one is already executing for the same workspace.

This response code and error body are **new** — previously the second request would race and produce a 500.

---

## Response: Workspace Not Found (HTTP 404)

```json
{
  "error": "NOT_FOUND",
  "message": "Workspace not found"
}
```

---

## Behaviour Changes vs. Current Implementation

| Behaviour                            | Before Fix                            | After Fix                                 |
| ------------------------------------ | ------------------------------------- | ----------------------------------------- |
| Rebuild on already-indexed workspace | 500 DuplicateKeyException             | 200 COMPLETED                             |
| Concurrent rebuild of same workspace | 500 / data corruption                 | 409 REBUILD_IN_PROGRESS                   |
| Per-document failure isolation       | Entire rebuild fails                  | Only that document fails; others continue |
| Transaction scope per document       | Outer `@Transactional` wraps all docs | Each document runs in its own transaction |
