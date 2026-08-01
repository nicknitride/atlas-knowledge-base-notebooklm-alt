# API Contract: Document Ingestion

**Feature**: `002-improve-backend` | **Date**: 2026-08-01

Base path: `/api/workspaces/{workspaceId}/documents`

## Upload

`POST /api/workspaces/{workspaceId}/documents`  
`Content-Type: multipart/form-data` field `file`

| Rule | Behavior |
|------|----------|
| Workspace missing | `404` `NOT_FOUND` |
| Empty file | `400` `UPLOAD_EMPTY` |
| Size > configured max (default 80 MB) | `400` `UPLOAD_TOO_LARGE` |
| Unsupported type | `400` `UPLOAD_UNSUPPORTED_TYPE` |
| Accepted | `202` + `DocumentResponse` with `status` PENDING or PROCESSING |

Supported types (minimum): PDF, Markdown, plain text (existing extractor rules).

## List / status (UI indicators)

`GET /api/workspaces/{workspaceId}/documents`

Each item MUST expose at least:

| Field | Meaning |
|-------|---------|
| id | Document id |
| filename | Original name |
| status | `PENDING` \| `PROCESSING` \| `COMPLETE` \| `FAILED` |
| failureReason | Null unless FAILED |
| createdAt | Timestamp |

Clients poll this (or get-by-id if added) for progress indicators on large
uploads. Optional future fields (`progressPercent`, `phase`) are not required
for MVP if status transitions are timely.

## Delete (including mid-ingest)

`DELETE /api/workspaces/{workspaceId}/documents/{documentId}` → `204`

| Requirement | Behavior |
|-------------|----------|
| In-flight job | Cancelled/abandoned; worker must not resurrect the document |
| Chunks / job rows | Removed (CASCADE / explicit cleanup) |
| Later retrieval | Zero passages from deleted document |

## Retry / replace

After FAILED (or delete), user may upload again without workspace reset
(FR-012). No dedicated “retry” endpoint required if re-upload creates a new
document + job.

## Terminalization

A document MUST NOT remain `PROCESSING` indefinitely: provider/extract failure
→ `FAILED` with reason; timeout (default 10 minutes) → `FAILED`; process restart
recovery must not leave permanent orphans without a path to PENDING/FAILED/
COMPLETE (FR-002).
