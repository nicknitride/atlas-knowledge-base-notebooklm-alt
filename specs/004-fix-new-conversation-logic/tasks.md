---
description: "Task list for Fix New Conversation Logic"
---

# Tasks: Fix New Conversation Logic

**Input**: Design documents from `/specs/004-fix-new-conversation-logic/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: MANDATORY per Atlas constitution (I. Test-First). Failing tests MUST
precede implementation for each behavior (including shared helpers).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- Next.js app at repository root: `app/`, `components/`, `lib/`, `__tests__/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm existing test tooling; no new product packages

- [x] T001 Verify Vitest + RTL scripts and config already work (`pnpm test`) via package.json and vitest.config.mts — document no new dependencies needed for this feature
- [x] T002 [P] Add test file stubs (empty `describe` placeholders only) in **tests**/chat-main-mode.test.ts and **tests**/conversation-create-flow.test.tsx so paths exist before red tests

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared pure mode derivation used by ChatPanel branching (R2 / data-model)

**⚠️ CRITICAL**: No user story UI wiring until this phase is complete

### chat-main-mode helper (TDD)

- [x] T003 Write failing unit tests for derived modes (`no-workspace` | `pre-start` | `empty-thread` | `active-thread`) in **tests**/chat-main-mode.test.ts per data-model.md and contracts/conversation-create-flow.md
- [x] T004 Implement pure helper `deriveChatMainMode({ workspaceId, conversationId, messageCount })` in lib/chat-main-mode.ts to pass **tests**/chat-main-mode.test.ts

**Checkpoint**: Mode helper green; stories may wire UI

---

## Phase 3: User Story 1 - Land in ready-to-type chat after starting a conversation (Priority: P1) 🎯 MVP

**Goal**: After successful create (any entry point, including Enter), select conversation, switch to Chat tab if needed, show empty-thread (not pre-start), and autofocus compose (FR-001, FR-002, FR-005, FR-006)

**Independent Test**: From workspace with no active conversation, create via main CTA / sidebar / footer, confirm with Enter; compose focused within 1s; three-suggestion pre-start gone; Documents tab create switches to Chat

### Tests for User Story 1 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T005 [P] [US1] Failing ChatPanel tests: with `conversationId` set and zero messages, Start CTA and three suggestion prompts are absent and compose is enabled in **tests**/chat-empty.test.tsx (extend; keep pre-start case for `conversationId === null`)
- [x] T006 [P] [US1] Failing create-flow tests: mocked successful create selects conversation, sets Chat tab, and focuses compose textarea in **tests**/conversation-create-flow.test.tsx per contracts/conversation-create-flow.md
- [x] T007 [P] [US1] Failing create-flow tests: create while Documents tab active switches to Chat + selects + focuses compose; cancel/blank name leave selection unchanged; API failure does not select in **tests**/conversation-create-flow.test.tsx
- [x] T008 [P] [US1] Failing test: selecting an existing empty conversation shows empty-thread UI but does **not** autofocus compose in **tests**/conversation-create-flow.test.tsx (FR-002)

### Implementation for User Story 1

- [x] T009 [US1] Add one-shot `focusComposeToken` (or equivalent) state and pass it into ChatPanel; bump only from successful create callback path in app/page.tsx
- [x] T010 [US1] Wire ChatPanel to use `deriveChatMainMode` from lib/chat-main-mode.ts so `empty-thread` omits Start CTA and three suggestions in components/chat-panel.tsx
- [x] T011 [US1] Autofocus message textarea when `focusComposeToken` changes and mode is `empty-thread`; do not focus on ordinary conversationId list selection in components/chat-panel.tsx
- [x] T012 [US1] Ensure successful `handleNewConversation` selects id, calls `onSelectTab("chat")`, notifies parent to bump focus token, and shows recoverable error without selecting on API failure in components/sidebar.tsx
- [x] T013 [US1] Coordinate modal close so post-create compose focus wins over `ModalIdName` focus restore (optional `restoreFocus` / close timing) in components/modal-id-name.tsx and components/sidebar.tsx
- [x] T014 [US1] Confirm main pre-start CTA still opens create modal via existing `requestStartConversation` path in app/page.tsx and components/sidebar.tsx (no dead ends)

**Checkpoint**: US1 MVP independently demonstrable (create → empty-thread → focused compose)

---

## Phase 4: User Story 2 - Distinguish “no conversation chosen” from “empty new conversation” (Priority: P2)

**Goal**: Pre-start guidance (CTA + three suggestions) only when no conversation selected; selected empty conversation uses empty-thread compose experience (FR-003, FR-004)

**Independent Test**: Workspace + no selection → pre-start visible; select/create empty conversation → pre-start gone, compose available

### Tests for User Story 2 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T015 [P] [US2] Failing/locked tests: workspace + `conversationId === null` still shows Start CTA and three suggestion prompts in **tests**/chat-empty.test.tsx (SC-003 regression guard)
- [x] T016 [P] [US2] Failing tests: empty-thread and pre-start are mutually exclusive for the same messageCount=0 in **tests**/chat-empty.test.tsx and/or **tests**/chat-main-mode.test.ts

### Implementation for User Story 2

- [x] T017 [US2] Audit components/chat-panel.tsx empty UI copy so empty-thread is calm compose-ready (no “Start a conversation” CTA language) while pre-start keeps guidance
- [x] T018 [US2] Ensure list selection of an existing empty conversation reuses the same empty-thread surface as post-create (no second code path) in components/chat-panel.tsx
- [x] T019 [US2] Align any leftover assertions/docs in **tests**/chat-empty.test.tsx with contracts/conversation-create-flow.md main-mode table

**Checkpoint**: US1 and US2 both independently verifiable; pre-start not regressed

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Performance, privacy, YAGNI, quickstart validation

- [x] T020 [P] Run manual scenarios in specs/004-fix-new-conversation-logic/quickstart.md (Enter confirm, Documents→Chat, cancel/fail)
- [x] T021 Confirm pure UI transition after create feels &lt;100ms and compose focus within 1s (SC-001); note any timing fix in components/chat-panel.tsx focus effect if needed
- [x] T022 [P] YAGNI/KISS pass: no new API fields, no redesign beyond branching/focus/error in components/chat-panel.tsx, components/sidebar.tsx, app/page.tsx
- [x] T023 Privacy/config review: no new telemetry or cloud deps; create still uses existing local API in lib/api.ts
- [x] T024 Run full `pnpm test` and fix regressions in **tests**/ related to chat empty states

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational — MVP
- **User Story 2 (Phase 4)**: Depends on Foundational; best after US1 empty-thread wiring exists (shares ChatPanel), but tests can be written in parallel
- **Polish (Phase 5)**: Depends on desired stories complete

### User Story Dependencies

- **User Story 1 (P1)**: After Phase 2 — no dependency on US2
- **User Story 2 (P2)**: After Phase 2 — locks pre-start vs empty-thread distinction; implementation mostly asserts/refines US1 branching

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Helper (Phase 2) before ChatPanel wiring
- Focus token (page) before ChatPanel autofocus consumption
- Sidebar create success before modal focus coordination polish

### Parallel Opportunities

- T002 stubs in parallel with T001
- T005–T008 US1 tests in parallel (same or adjacent test files; coordinate if editing same file)
- T015–T016 US2 tests in parallel after/with US1 tests
- T020 / T022 / T023 polish items in parallel

---

## Parallel Example: User Story 1

```bash
# Launch US1 failing tests together:
Task: "Extend chat-empty empty-thread assertions in __tests__/chat-empty.test.tsx"
Task: "Create-flow select+focus tests in __tests__/conversation-create-flow.test.tsx"
Task: "Documents tab + cancel/fail tests in __tests__/conversation-create-flow.test.tsx"
Task: "No autofocus on list select in __tests__/conversation-create-flow.test.tsx"

# Then implement sequentially where files overlap:
Task: "focusComposeToken in app/page.tsx"
Task: "ChatPanel mode + focus in components/chat-panel.tsx"
Task: "Sidebar create success/error in components/sidebar.tsx"
```

---

## Parallel Example: User Story 2

```bash
# Regression locks in parallel:
Task: "Pre-start still visible when conversationId null in __tests__/chat-empty.test.tsx"
Task: "Mutual exclusion pre-start vs empty-thread tests"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (`deriveChatMainMode`)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Create via Enter → focused compose; Documents→Chat
5. Demo if ready

### Incremental Delivery

1. Setup + Foundational → mode helper ready
2. US1 → create lands in compose (MVP)
3. US2 → lock pre-start vs empty-thread distinction / polish copy
4. Polish → quickstart + full test suite

### Parallel Team Strategy

1. Pair on Setup + Foundational
2. Dev A: US1 create-flow + focus
3. Dev B: US2 regression tests + empty-thread copy (after T010 lands)

---

## Notes

- [P] = different files, no incomplete-task dependencies
- Existing `__tests__/chat-empty.test.tsx` currently asserts Start CTA when
  `conversationId === null` only for one case — extend carefully so pre-start
  remains covered (US2 / SC-003)
- Sidebar already selects + switches Chat tab on create; focus token + ChatPanel
  branching are the critical gaps (research R1)
- Commit after each task or logical group
- Avoid new backend/OpenAPI changes
