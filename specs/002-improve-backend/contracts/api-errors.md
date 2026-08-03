# API Contract: Error Outcomes

**Feature**: `002-improve-backend` | **Date**: 2026-08-01

Stable, app-actionable errors for workspace / document / chat operations
(FR-008). No stack traces or internal hostnames in `message`.

## Error body

HTTP non-2xx responses SHOULD include JSON:

```json
{
  "code": "UPLOAD_TOO_LARGE",
  "message": "Upload a non-empty file smaller than 80 MB",
  "requestId": "optional-correlation-id"
}
```

| Field     | Required | Description                                           |
| --------- | -------- | ----------------------------------------------------- |
| code      | yes      | Stable machine identifier (UPPER_SNAKE)               |
| message   | yes      | Human-safe string suitable for UI                     |
| requestId | no       | From existing request correlation filter when present |

SSE chat streams: on provider/retrieval failure, emit an error event or close
with a client-visible failure; MUST NOT complete with a synthetic grounded
answer body (FR-007 / SC-003). Exact SSE event name may match existing client
parser; document in implementation tasks if renamed.

## Codes (minimum set)

| code                        | HTTP                         | When                                                              |
| --------------------------- | ---------------------------- | ----------------------------------------------------------------- |
| `NOT_FOUND`                 | 404                          | Workspace / document / conversation missing or outside workspace  |
| `VALIDATION_ERROR`          | 400                          | Generic invalid request                                           |
| `UPLOAD_EMPTY`              | 400                          | Empty file                                                        |
| `UPLOAD_TOO_LARGE`          | 400                          | Exceeds configured max (default 80 MB)                            |
| `UPLOAD_UNSUPPORTED_TYPE`   | 400                          | Not PDF / Markdown / plain text                                   |
| `PROVIDER_UNAVAILABLE`      | 503                          | Configured AI backend unreachable / timed out                     |
| `PROVIDER_MISCONFIGURED`    | 503 / 400                    | Model missing or invalid provider config                          |
| `EMBEDDING_CONFIG_MISMATCH` | 409                          | Stored vectors ≠ configured embedding identity                    |
| `RETRIEVAL_UNAVAILABLE`     | 503                          | Vector/search subsystem failure (no fake ILIKE success)           |
| `INGESTION_FAILED`          | 422 or reflected on document | Terminal ingest failure (detail also on document `failureReason`) |

## Success shapes (unchanged unless noted)

- Document list/get: includes `status` (`PENDING` \| `PROCESSING` \| `COMPLETE` \|
  `FAILED`) and `failureReason` for UI indicators.
- Upload: `202 ACCEPTED` + document with non-terminal or PENDING/PROCESSING
  status.
- Delete document: `204` ; subsequent retrieval has no residue.
