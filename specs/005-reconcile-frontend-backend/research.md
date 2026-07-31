# Research: Reconcile Frontend With New Backend

**Feature**: `005-reconcile-frontend-backend` | **Date**: 2026-08-01

All Technical Context items were known from the repo and from
`002-improve-backend` contracts. Research focuses on defect causes and
repair patterns, not stack selection.

## R1 — Workspace delete reloads the page

**Decision**: Suppress native form submission on the delete-confirmation
form (`preventDefault`), await the delete handler, close the modal on
success, and surface errors via `ErrorBanner`. Remove the
`workspaces.length > 1` gate so the last workspace can be deleted.

**Rationale**: The Confirm control is `type="submit"` inside a `<form>`
whose `onSubmit` never suppresses default navigation. The browser navigates
away (full reload) before the async `DELETE` finishes, so the list resets
and the workspace appears undeleted. Create/rename modals already suppress
submit; delete does not. The length gate contradicts the existing
no-workspace empty state and FR-006.

**Alternatives considered**:
- Replace the form with button `onClick` only — also works, but keeping the
  form + suppress matches sibling modals and preserves Enter-to-confirm.
- Soft-delete / archive — out of scope; backend hard-deletes.

## R2 — Centralize API error parsing

**Decision**: Extend `lib/api.ts` so every non-OK response is parsed as the
flat `{ code, message, requestId }` envelope (already used by
`uploadDocument` via `readApiError`). Throw a small typed error that
carries `code` and `message`. Map codes to user-facing copy in one place;
prefer backend `message` when present and safe.

**Rationale**: Spec FR-009–FR-011 require distinct outcomes. Today only
upload parses the envelope; other mutations throw generic strings and UI
handlers only `console.error`. Centralizing avoids per-handler duplication
and matches `002`’s `api-errors.md` contract.

**Alternatives considered**:
- React Query / SWR with global `onError` — rejected (constitution
  simplicity; YAGNI).
- Parse only in components — rejected (duplication; easy to miss a path).

## R3 — Idempotent delete (“already gone”)

**Decision**: Treat HTTP 404 with `code: NOT_FOUND` on DELETE (workspace,
document, conversation) as success from the user’s perspective: remove the
item from local state; do not show an error.

**Rationale**: FR-007 / FR-012 and Story 1 scenario 6. Matches common REST
idempotent-delete UX and avoids confusing “failed” after a double-confirm
or race.

**Alternatives considered**:
- Show “already deleted” toast — acceptable but noisier; prefer silent
  success per spec.

## R4 — SSE chat `error` event

**Decision**: In `streamChatMessage`, handle `event: error` by parsing
`data` as `ApiError` JSON (or a JSON string wrapping it), invoke `onError`,
and **do not** call `onComplete`. On stream end without `done` after an
error, still treat as error. Abort on workspace/conversation navigation
already cancels via `AbortController`; ensure abort does not fire
`onComplete` as success.

**Rationale**: Backend `ConversationController` emits SSE `error` with the
same envelope. The client currently only handles `chunk` / `citations` /
`done`; unknown events are ignored, and loop exit calls `onComplete()`, so
failures look like empty success (Story 3 / FR-015–016).

**Alternatives considered**:
- Switch UI to sync `POST .../messages` — works for fail-closed but drops
  existing stream path; out of scope to remove streaming.
- Treat any stream close without citations as error — too aggressive for
  legitimate “no evidence” answers.

## R5 — Failure outcome messaging

**Decision**: Map at least these codes to distinct user copy (backend
message preferred when present):

| code | User-facing intent |
|------|--------------------|
| `VALIDATION_ERROR` | Fix the input (keep modal open where applicable) |
| `NOT_FOUND` | Resource gone — delete: success; other ops: refresh/recover |
| `UPLOAD_*` | Specific upload rejection (already partially done) |
| `EMBEDDING_CONFIG_MISMATCH` | Workspace needs documents re-processed |
| `PROVIDER_UNAVAILABLE` / `PROVIDER_MISCONFIGURED` | AI service unreachable / misconfigured |
| `RETRIEVAL_UNAVAILABLE` | Search temporarily unavailable |
| Network / non-JSON | Generic “could not reach Atlas” / action failed |

**Rationale**: FR-011; aligns with `002` contract codes without inventing
new ones.

**Alternatives considered**:
- Show only `message` with no code-specific copy — weaker for empty or
  transport-limit bodies (e.g. Spring 413 without `ApiError`).

## R6 — Document status presentation

**Decision**: Keep existing 2s polling; map status labels for UI:
`PENDING` → queued, `PROCESSING` → processing, `COMPLETE` → ready,
`FAILED` → failed + `failureReason`. Do not treat upload `202` as “ready.”

**Rationale**: FR-019–FR-022; polling already exists; only clarity and
rejection messaging need work.

**Alternatives considered**:
- WebSocket/SSE for ingest — unjustified complexity for local single-user.

## R7 — Testing approach

**Decision**: Red–green Vitest tests first per story:
1. Workspace delete: form submit does not cause navigation; modal closes;
   `deleteWorkspace` called once; last workspace deletable; error banner on
   failure; 404 treated as success.
2. API helpers: non-OK → typed error with code/message; DELETE 204 no JSON
   parse; DELETE 404 → success helper or documented convention at call site.
3. Stream: `error` event → `onError` not `onComplete`; `done` still
   completes.
4. Sidebar/chat: mutation failures show `ErrorBanner` with mapped text.

**Rationale**: Constitution I; SC-008.

**Alternatives considered**:
- Playwright E2E against Docker only — valuable later; Vitest covers the
  regression faster for this repair.

## R8 — Branch / working tree note

**Decision**: Plan artifacts live under
`specs/005-reconcile-frontend-backend/`. Git branch name
`005-reconcile-frontend-backend` is the intended feature branch; if the
checkout remains on another branch during planning, create/switch before
implementation (`/speckit-tasks` / implement). Spec directory is independent
of branch (Spec Kit feature.json).

**Rationale**: `setup-plan.sh` reported BRANCH
`005-reconcile-frontend-backend`; no `before_plan` git hook was registered.
