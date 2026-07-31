# Contract: SSE Chat Stream (Client)

**Feature**: `005-reconcile-frontend-backend` | **Date**: 2026-08-01

**Upstream**: `specs/002-improve-backend/contracts/grounded-chat.md` +
`ConversationController` SSE events.

## Endpoint

`POST /api/workspaces/{workspaceId}/conversations/{id}/messages/stream`

- Request JSON: `{ "query": "<non-blank string>" }`
- Response: `text/event-stream`

## Events the client MUST handle

| event | data | Client action |
|-------|------|----------------|
| `chunk` | plain text | `onChunk` (may be no-op for UI for now) |
| `citations` | JSON array of Citation | `onCitations` |
| `done` | e.g. `[DONE]` | `onComplete` once; stop reading |
| `error` | JSON `ApiError` object **or** JSON string of that object | `onError`; **MUST NOT** call `onComplete` |

## Failure rules

1. Non-OK HTTP before stream body → `onError` (prefer parsed envelope).
2. Mid-stream `error` event → `onError` with code/message; end loading as
   failure; do not present as successful empty answer.
3. Stream closes without `done` after an `error` → still failure.
4. Stream closes without `done` and without prior `error` → treat as error
   (incomplete), not success.
5. `AbortError` (user stop / navigate away) → neither success nor error
   banner required; MUST NOT apply results to a different conversation.

## Grounding presentation

- After successful `done`, reload conversation detail (existing behavior)
  is acceptable.
- UI MUST NOT claim grounded success when the assistant message has no
  citations (FR-018); empty/no-evidence cases show honest empty or decline
  messaging from content, not fake sources.

## Codes commonly seen on stream `error`

`PROVIDER_UNAVAILABLE`, `PROVIDER_MISCONFIGURED`, `EMBEDDING_CONFIG_MISMATCH`,
`RETRIEVAL_UNAVAILABLE`, `NOT_FOUND`, `VALIDATION_ERROR`.
