# Data Model: Reconcile Frontend With New Backend

**Feature**: `005-reconcile-frontend-backend` | **Date**: 2026-08-01

Client-side view of entities already owned by the Spring API. No new
persistence schema. Field names match JSON from the backend (camelCase).

## Entities

### Workspace

| Field     | Type              | Notes                             |
| --------- | ----------------- | --------------------------------- |
| id        | string (UUID)     | Stable identity                   |
| name      | string            | Display; max 120 on create/rename |
| createdAt | string (ISO-8601) |                                   |

**Relationships**: Owns documents and conversations (cascade delete on
backend).

**Client rules**:

- Delete confirmation retains `id` + `name` until success or cancel.
- After delete of selected workspace → select another or empty state.
- After delete of non-selected → selection unchanged.
- Last workspace may be deleted → empty state (create CTA).

### DocumentItem

| Field         | Type              | Notes                                               |
| ------------- | ----------------- | --------------------------------------------------- |
| id            | string (UUID)     |                                                     |
| filename      | string            |                                                     |
| contentType   | string            |                                                     |
| status        | enum              | `PENDING` \| `PROCESSING` \| `COMPLETE` \| `FAILED` |
| failureReason | string?           | Shown when `FAILED`                                 |
| createdAt     | string (ISO-8601) |                                                     |

**UI status mapping**:

| API status | User label | Grounding eligible |
| ---------- | ---------- | ------------------ |
| PENDING    | Queued     | No                 |
| PROCESSING | Processing | No                 |
| COMPLETE   | Ready      | Yes                |
| FAILED     | Failed     | No                 |

**Client rules**: Rejected uploads never enter the list. Poll until all
non-terminal or timeout (existing 2 min).

### Conversation

| Field                 | Type              | Notes |
| --------------------- | ----------------- | ----- |
| id                    | string (UUID)     |       |
| workspaceId           | string (UUID)     |       |
| title                 | string            |       |
| createdAt / updatedAt | string (ISO-8601) |       |

### Message

| Field     | Type                  | Notes                                                                                                   |
| --------- | --------------------- | ------------------------------------------------------------------------------------------------------- |
| id        | string (UUID)         |                                                                                                         |
| role      | `USER` \| `ASSISTANT` | Client type may still allow `SYSTEM` historically; API does not emit it — do not invent SYSTEM messages |
| content   | string                |                                                                                                         |
| createdAt | string (ISO-8601)     |                                                                                                         |
| citations | Citation[]?           | Required for presenting answer as grounded                                                              |

### Citation

| Field            | Type          | Notes |
| ---------------- | ------------- | ----- |
| chunkId          | string (UUID) |       |
| documentId       | string (UUID) |       |
| documentFilename | string        |       |
| ordinal          | number        |       |
| sourceLocator    | string        |       |
| snippet          | string        |       |
| similarity       | number        |       |

### ApiFailure (client)

| Field     | Type    | Notes                             |
| --------- | ------- | --------------------------------- |
| code      | string  | UPPER_SNAKE from API when present |
| message   | string  | User-safe                         |
| requestId | string? | Correlation; optional display     |

Not persisted; attached to thrown errors from `lib/api.ts`.

## State transitions

### Workspace delete (UI)

```text
[idle] --open confirm--> [confirm open]
[confirm open] --cancel--> [idle]
[confirm open] --confirm--> [deleting]  (submit suppressed; button disabled)
[deleting] --204 or 404 NOT_FOUND--> [idle] + list updated + modal closed
[deleting] --other failure--> [confirm open or idle] + error banner + list unchanged
```

### Document ingest (UI)

```text
[not listed] --upload accepted 202--> PENDING --poll--> PROCESSING --poll--> COMPLETE
                                                      \--> FAILED (+ failureReason)
[not listed] --upload 4xx--> [not listed] + upload error banner
```

### Chat stream (UI)

```text
[idle] --send--> [loading]
[loading] --SSE done--> [idle] + reload messages/citations
[loading] --SSE error / HTTP fail--> [idle] + error banner (not success)
[loading] --abort (nav away)--> [idle] (no apply to other conversation)
```

## Validation (client)

| Action                  | Rule                                                |
| ----------------------- | --------------------------------------------------- |
| Create/rename workspace | Non-blank name; respect backend max length          |
| Create conversation     | Non-blank title (existing)                          |
| Delete confirm          | Must have non-empty retained id/name                |
| Upload                  | Backend validates type/size; surface returned codes |

## Integrity

- Optimistic list updates MUST revert or never apply on failure (FR-013).
- In-flight chat/upload for a deleted workspace MUST abort and not write into
  UI for a missing workspace.
- Correlation: prefer showing API `message`; never show stack traces.
