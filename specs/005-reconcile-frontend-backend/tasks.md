---
description: "Task list for Reconcile Frontend With New Backend"
---

# Tasks: Reconcile Frontend With New Backend

**Input**: Design documents from `/specs/005-reconcile-frontend-backend/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: MANDATORY per Atlas constitution (I. Test-First). Every user-story
phase MUST include failing test tasks before implementation tasks.

**Organization**: Tasks are grouped by user story to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Next.js app at repository root: `app/`, `components/`, `lib/`, `__tests__/`
- Backend is frozen for this feature (read-only reference)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Feature branch and confirm existing Vitest harness (already from
`001-improve-app-ui`) — no new dependencies

- [x] T001 Create and check out git branch `005-reconcile-frontend-backend` from current mainline work (or from HEAD if already integrating)
- [x] T002 [P] Confirm `pnpm test` runs green on the baseline before changes (vitest.config.mts, package.json scripts already present)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared API error typing, code→message mapping, and idempotent
DELETE helpers used by every story. Contracts:
[contracts/api-client-errors.md](./contracts/api-client-errors.md)

**⚠️ CRITICAL**: No user-story UI wiring until this phase is complete

### Tests (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T003 [P] Write failing unit tests for `ApiError` / `readApiError` parsing of `{ code, message, requestId }` and non-JSON fallbacks in `__tests__/api-errors.test.ts` per contracts/api-client-errors.md
- [x] T004 [P] Write failing unit tests for code→user-message mapping (`VALIDATION_ERROR`, `NOT_FOUND`, `UPLOAD_*`, `EMBEDDING_CONFIG_MISMATCH`, `PROVIDER_UNAVAILABLE`, `PROVIDER_MISCONFIGURED`, `RETRIEVAL_UNAVAILABLE`, network) in `__tests__/api-error-messages.test.ts`
- [x] T005 [P] Write failing unit tests that `deleteWorkspace` / `deleteDocument` / `deleteConversation` resolve successfully on 204 and on 404 with `code: NOT_FOUND`, and throw typed errors on other failures, in `__tests__/api-delete-idempotent.test.ts` (mock `fetch`; never call `res.json()` on 204)

### Implementation

- [x] T006 Implement typed `ApiError` (or equivalent) + generalize `readApiError` for all non-OK JSON responses in `lib/api.ts` to pass `__tests__/api-errors.test.ts`
- [x] T007 [P] Implement code→message helper in `lib/api-error-messages.ts` (prefer backend `message` when present) to pass `__tests__/api-error-messages.test.ts`
- [x] T008 Wire all mutating helpers in `lib/api.ts` (`createWorkspace`, `renameWorkspace`, `deleteWorkspace`, `uploadDocument`, `deleteDocument`, `createConversation`, `renameConversation`, `deleteConversation`, `fetchConversationDetail`, list fetches as needed) to throw typed errors via `readApiError`, and treat DELETE 404 `NOT_FOUND` as success, to pass `__tests__/api-delete-idempotent.test.ts` and `__tests__/api-errors.test.ts`

**Checkpoint**: Foundation green — stories may wire UI

---

## Phase 3: User Story 1 - Deleting a workspace actually deletes it (Priority: P1) 🎯 MVP

**Goal**: Confirm delete does not reload the page; DELETE completes; modal
closes; list/selection update; last workspace deletable; failures visible
(FR-001–FR-008). Contract: [contracts/workspace-mutations.md](./contracts/workspace-mutations.md)

**Independent Test**: Two workspaces → confirm delete → no reload, modal
closed, workspace gone; hard refresh still gone; delete last workspace → empty
state; API down → banner, list unchanged

### Tests for User Story 1 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T009 [P] [US1] Write failing component tests that delete confirmation `onSubmit` calls `preventDefault` (no navigation), awaits `deleteWorkspace` once, closes modal, and updates list in `__tests__/workspace-delete.test.tsx`
- [x] T010 [P] [US1] Write failing tests for last-workspace delete control visible (`workspaces.length === 1`) and empty-state path after delete in `__tests__/workspace-delete.test.tsx`
- [x] T011 [P] [US1] Write failing tests for delete failure showing `ErrorBanner` and list unchanged, and 404 treated as success, in `__tests__/workspace-delete.test.tsx`

### Implementation for User Story 1

- [x] T012 [US1] Fix delete confirmation form in `components/sidebar.tsx`: `e.preventDefault()`, await `handleDeleteWorkspace`, disable Confirm while in progress (FR-001, FR-002, FR-008)
- [x] T013 [US1] On successful delete in `components/sidebar.tsx`: close modal, clear retained id/name, update list, select remaining workspace or empty state, leave non-selected delete without changing selection (FR-003–FR-005)
- [x] T014 [US1] Remove `workspaces.length > 1` gate so delete is available for every workspace in `components/sidebar.tsx` (FR-006); ensure empty state clears docs/conversations/citations via existing `onSelectWorkspace` / parent props in `app/page.tsx` as needed
- [x] T015 [US1] Surface delete failures with `ErrorBanner` in `components/sidebar.tsx` using mapped messages; treat 404/idempotent success without error (FR-007); remove delete-path `console.log` (FR-026)

**Checkpoint**: US1 MVP independently demonstrable

---

## Phase 4: User Story 2 - Failed actions tell the user what went wrong (Priority: P2)

**Goal**: Create/rename/delete conversation/document failures show distinct
user-visible messages; no silent `console.error`-only paths; no optimistic
lies (FR-009–FR-014)

**Independent Test**: Mock API rejection codes for create/rename workspace,
rename/delete conversation, delete document → each shows banner/inline error
with distinct copy; lists revert or never apply failed optimistic updates

### Tests for User Story 2 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T016 [P] [US2] Write failing tests that workspace create/rename failures keep modal open, preserve input, and show error (not console-only) in `__tests__/mutation-errors.test.tsx`
- [x] T017 [P] [US2] Write failing tests that conversation rename/delete and document delete failures show `ErrorBanner` and do not leave stale optimistic state in `__tests__/mutation-errors.test.tsx`
- [x] T018 [P] [US2] Write failing unit/integration assertion that `EMBEDDING_CONFIG_MISMATCH` / `PROVIDER_UNAVAILABLE` message helpers produce distinct copy in `__tests__/api-error-messages.test.ts` (extend if needed)

### Implementation for User Story 2

- [x] T019 [US2] Wire create/rename workspace error state + `ErrorBanner` (or modal-local error) in `components/sidebar.tsx` using `lib/api-error-messages.ts`
- [x] T020 [US2] Wire create conversation / rename conversation / delete conversation / delete document error banners in `components/sidebar.tsx`; ensure failures do not leave incorrect list state (FR-013)
- [x] T021 [US2] Ensure upload path continues to surface typed upload codes via `ErrorBanner` in `components/sidebar.tsx` (align with FR-010; reuse T008 helpers)

**Checkpoint**: US1 + US2 independently testable

---

## Phase 5: User Story 3 - Chat failures surface instead of vanishing (Priority: P2)

**Goal**: SSE `error` → `onError` (not `onComplete`); loading ends with
actionable message; abort on navigation; no fake grounded success without
citations (FR-015–FR-018). Contract:
[contracts/sse-chat-stream.md](./contracts/sse-chat-stream.md)

**Independent Test**: Mock stream emitting `event: error` → error banner, not
empty success; successful `done` still reloads messages; abort does not apply
to wrong conversation

### Tests for User Story 3 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T022 [P] [US3] Write failing unit tests for `streamChatMessage` handling `error` event (parse ApiError, call `onError`, never `onComplete`) and incomplete close without `done` in `__tests__/stream-error-event.test.ts`
- [x] T023 [P] [US3] Write failing ChatPanel tests that stream `onError` sets user-visible error and clears loading in `__tests__/chat-stream-errors.test.tsx`
- [x] T024 [P] [US3] Write failing tests that abort/navigation cancels stream without treating as success in `__tests__/chat-stream-errors.test.tsx` or `__tests__/stream-error-event.test.ts`

### Implementation for User Story 3

- [x] T025 [US3] Update `streamChatMessage` in `lib/api.ts` to handle SSE `error`, avoid `onComplete` on failure/incomplete end, remove debug `console.log` on citations path (FR-015, FR-016, FR-026)
- [x] T026 [US3] Update `components/chat-panel.tsx` to display mapped stream/API errors (including embedding mismatch and provider unavailable), end loading on error, and abort in-flight stream on workspace/conversation change (FR-017)
- [x] T027 [US3] Ensure ChatPanel / sources path does not present grounded success when assistant message has no citations (FR-018) in `components/chat-panel.tsx` / `components/sources-panel.tsx` as appropriate

**Checkpoint**: US1–US3 independently testable

---

## Phase 6: User Story 4 - Upload progress reflects asynchronous processing (Priority: P3)

**Goal**: Queued → processing → ready/failed labels; show `failureReason`;
rejected uploads never listed (FR-019–FR-022)

**Independent Test**: Mock documents with each status → distinct UI labels;
FAILED shows reason; upload 4xx → banner and list unchanged

### Tests for User Story 4 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T028 [P] [US4] Write failing tests for document status label mapping (`PENDING`→Queued, `PROCESSING`→Processing, `COMPLETE`→Ready, `FAILED`→Failed + reason) in `__tests__/document-status.test.tsx`
- [x] T029 [P] [US4] Write failing test that rejected upload does not add a list row and shows upload error in `__tests__/document-status.test.tsx` or `__tests__/mutation-errors.test.tsx`

### Implementation for User Story 4

- [x] T030 [US4] Add user-facing status labels and visual distinction for non-ready documents in `components/sidebar.tsx` (and `components/sources-panel.tsx` only if needed); show `failureReason` when `FAILED` (FR-019–FR-021)
- [x] T031 [US4] Confirm upload rejection path in `components/sidebar.tsx` never inserts a document row and uses typed API messages (FR-022); keep existing poll interval/timeout per research.md

**Checkpoint**: All four stories independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Quickstart validation, YAGNI pass, privacy/UX hygiene

- [x] T032 [P] Remove remaining user-facing-path diagnostic `console.log` calls in `components/sidebar.tsx` and `lib/api.ts` (FR-026)
- [x] T033 [P] Update README.md only if frontend error/delete behavior needs a one-line note for local validators (skip if unchanged)
- [x] T034 Run `pnpm test` full suite green
- [x] T035 Execute manual scenarios in `specs/005-reconcile-frontend-backend/quickstart.md` (P1 delete no-reload; API-down banner; chat provider-down error; upload states)
- [x] T036 YAGNI/KISS pass: no new client libraries, no backend OpenAPI edits, no incremental token-render scope creep

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Start immediately
- **Foundational (Phase 2)**: Depends on Setup — **BLOCKS** all user stories
- **US1 (Phase 3)**: After Foundational — MVP
- **US2 (Phase 4)**: After Foundational; benefits from US1 error-banner patterns but independently testable
- **US3 (Phase 5)**: After Foundational; stream parser independent of US1 UI; uses same message map as US2
- **US4 (Phase 6)**: After Foundational; upload error wiring overlaps US2 T021
- **Polish (Phase 7)**: After desired stories complete

### User Story Dependencies

| Story    | Depends on | Notes                                          |
| -------- | ---------- | ---------------------------------------------- |
| US1 (P1) | Phase 2    | Delete idempotent helpers + ErrorBanner        |
| US2 (P2) | Phase 2    | Message map; can parallel US3 after foundation |
| US3 (P2) | Phase 2    | Stream tests/impl in `lib/api.ts` + chat-panel |
| US4 (P3) | Phase 2    | Prefer after US2 upload error path (T021)      |

### Within Each User Story

1. Write failing tests
2. Confirm they fail
3. Implement until green
4. Checkpoint before next story

### Parallel Opportunities

- T003, T004, T005 in parallel (different test files)
- T009, T010, T011 in parallel (same file ok if coordinated; prefer one author)
- T016, T017, T018 in parallel
- T022, T023, T024 in parallel
- T028, T029 in parallel
- After Phase 2: US2 and US3 can proceed in parallel on different files
  (`sidebar.tsx` vs `lib/api.ts` stream + `chat-panel.tsx`) — avoid dual
  editors on `lib/api.ts` until T008/T025 sequenced

---

## Parallel Example: User Story 1

```bash
# After Phase 2 green, launch US1 tests together:
Task: "Failing delete preventDefault/await/modal tests in __tests__/workspace-delete.test.tsx"
Task: "Failing last-workspace delete tests in __tests__/workspace-delete.test.tsx"
Task: "Failing delete error banner / 404 success tests in __tests__/workspace-delete.test.tsx"

# Then implement T012 → T015 sequentially in components/sidebar.tsx (+ page if needed)
```

## Parallel Example: After Foundation

```bash
# Developer A — US2 mutation error UI in components/sidebar.tsx
# Developer B — US3 stream parser in lib/api.ts + components/chat-panel.tsx
# Sequence: finish T008 before T025 if both touch lib/api.ts
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup (branch + baseline tests)
2. Phase 2 Foundational (typed errors + idempotent DELETE)
3. Phase 3 US1 workspace delete
4. **STOP and VALIDATE** via quickstart P1 + `pnpm test`
5. Demo: delete no longer reloads; workspace stays gone after refresh

### Incremental Delivery

1. Setup + Foundational → shared client contract green
2. US1 → MVP delete fix
3. US2 → all mutation errors visible
4. US3 → chat stream failures honest
5. US4 → upload status clarity
6. Polish → quickstart sign-off

### Suggested MVP Scope

**US1 only** (plus Phase 1–2) ships the reported defect fix. US2–US4 complete
backend reconciliation.

---

## Notes

- [P] = different files / no incomplete-task dependency
- Backend endpoints and OpenAPI are **out of scope** — do not edit
- Prefer mocking `fetch` or `lib/api.ts` in Vitest; no Docker required for unit/component tests
- Commit after each logical group (foundation, then each story)
- Verify tests fail before implementing each story
