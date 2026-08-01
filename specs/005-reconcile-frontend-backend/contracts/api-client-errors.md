# Contract: API Client Errors

**Feature**: `005-reconcile-frontend-backend` | **Date**: 2026-08-01

**Upstream**: `specs/002-improve-backend/contracts/api-errors.md` (authoritative
server shape). This document is the **client consumption** contract.

## Envelope

Non-success HTTP responses from `/api/**` are interpreted as:

```json
{
  "code": "PROVIDER_UNAVAILABLE",
  "message": "Human-safe explanation",
  "requestId": "optional"
}
```

| Field | Client behavior |
|-------|-----------------|
| code | Attached to thrown error; drives distinct messaging |
| message | Preferred user-visible text when present |
| requestId | Optional; may be logged locally, not required in UI |

If the body is missing or not JSON (e.g. gateway/multipart hard limit), the
client uses a generic actionable message for the failed action.

## Client API surface (`lib/api.ts`)

| Helper | Success | Failure |
|--------|---------|---------|
| `fetchWorkspaces` | 200 JSON array | Typed error |
| `createWorkspace` | 201 JSON | Typed error (VALIDATION_ERROR, …) |
| `renameWorkspace` | 200 JSON | Typed error |
| `deleteWorkspace` | 204 empty **or** 404 `NOT_FOUND` → resolve void | Other non-OK → typed error |
| `fetchDocuments` | 200 JSON array | Typed error |
| `uploadDocument` | 202 JSON DocumentItem | Typed error (UPLOAD_*) |
| `deleteDocument` | 204 or 404 NOT_FOUND → void | Other → typed error |
| `fetchConversations` / `createConversation` / `renameConversation` / `fetchConversationDetail` | 2xx JSON | Typed error |
| `deleteConversation` | 204 or 404 NOT_FOUND → void | Other → typed error |
| `streamChatMessage` | SSE stream | See [sse-chat-stream.md](./sse-chat-stream.md) |

**MUST NOT** call `res.json()` on 204 success bodies.

## Code → UX (minimum)

| code | UX |
|------|-----|
| VALIDATION_ERROR | Inline/banner; keep create/rename modal open |
| NOT_FOUND | Deletes: treat as success; other: recover selection / message |
| UPLOAD_EMPTY / UPLOAD_TOO_LARGE / UPLOAD_UNSUPPORTED_TYPE | Upload banner with specific reason |
| EMBEDDING_CONFIG_MISMATCH | Chat/workspace message: re-process documents |
| PROVIDER_UNAVAILABLE / PROVIDER_MISCONFIGURED | Chat: AI unavailable / misconfigured |
| RETRIEVAL_UNAVAILABLE | Chat: search unavailable |
| (network) | Action failed / cannot reach backend |

## Explicit non-goals

- Changing server codes or HTTP statuses
- Nested `{ success, error }` envelopes
- `/api/v1` prefix
