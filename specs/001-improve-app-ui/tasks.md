---
description: "Task list for Improve Application UI"
---

# Tasks: Improve Application UI

**Input**: Design documents from `/specs/001-improve-app-ui/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: MANDATORY per Atlas constitution (I. Test-First). Failing tests MUST
precede implementation for each behavior (including shared helpers).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Next.js app at repository root: `app/`, `components/`, `lib/`, `__tests__/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Test runner only — no product behavior yet

- [x] T001 Add Vitest, jsdom, @testing-library/react, @testing-library/user-event, @testing-library/jest-dom as devDependencies in package.json
- [x] T002 [P] Create Vitest config in vitest.config.mts (jsdom environment, path aliases matching tsconfig)
- [x] T003 [P] Add `"test": "vitest run"` and `"test:watch": "vitest"` scripts in package.json

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared helpers with **red → green** for each module; layout privacy + tokens

**⚠️ CRITICAL**: No user story UI wiring until this phase is complete

### ui-state helpers (TDD)

- [x] T004 Write failing tests for empty/loading/error presentational helpers in **tests**/ui-state.test.tsx per contracts/ui-shell.md
- [x] T005 Implement shared empty/loading/error helpers in components/ui-state.tsx to pass **tests**/ui-state.test.tsx

### list-filter (TDD)

- [x] T006 [P] Write failing unit tests for case-insensitive match/clear/no-matches in **tests**/list-filter.test.ts
- [x] T007 Implement list filter pure helper in lib/list-filter.ts to pass **tests**/list-filter.test.ts

### appearance (TDD)

- [x] T008 [P] Write failing unit tests for get/set/apply and invalid→system fallback in **tests**/appearance.test.ts
- [x] T009 Implement appearance preference module (key `atlas.appearance`) in lib/appearance.ts to pass **tests**/appearance.test.ts

### shell chrome (non-story wiring)

- [x] T010 Remove or disable `@vercel/analytics` and bootstrap appearance (read preference, apply `dark` on documentElement) in app/layout.tsx
- [x] T011 [P] Refresh moderate-restyle design tokens (typography, spacing, color) in app/globals.css without changing three-region IA

**Checkpoint**: Helpers tested green; stories may wire UI

---

## Phase 3: User Story 1 - Clear first-run and empty workspace experience (Priority: P1) 🎯 MVP

**Goal**: Empty states + sources auto-hide/toggle calm-empty (FR-001, FR-002, FR-013)

**Independent Test**: No selection + empty workspace empty states; sources hidden with zero citations; toggle opens calm empty—without filters, appearance control, or narrow nav collapse

### Tests for User Story 1 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T012 [P] [US1] Failing component/contract tests for no-workspace, no-conversations, no-documents empty CTAs in **tests**/empty-states.test.tsx
- [x] T013 [P] [US1] Failing tests for sources visibility formula (auto-hide, forced open calm empty) in **tests**/panel-visibility.test.tsx per contracts/ui-shell.md
- [x] T014 [P] [US1] Failing ChatPanel empty primary-action test in **tests**/chat-empty.test.tsx

### Implementation for User Story 1

- [x] T015 [P] [US1] Add no-workspace / empty-chat messaging and primary actions in components/chat-panel.tsx using components/ui-state.tsx
- [x] T016 [P] [US1] Add empty conversation and empty documents list states with primary actions in components/sidebar.tsx
- [x] T017 [US1] Implement sources visibility state (`sourcesForcedOpen`, `sourcesUserCollapsed`, derived `sourcesVisible`) and restore toggle in app/page.tsx
- [x] T018 [US1] Wire sources panel calm empty (not error) when forced open with zero citations in components/sources-panel.tsx
- [x] T019 [US1] Ensure empty-state primary actions invoke existing create/upload/start flows without dead ends in app/page.tsx and components/sidebar.tsx

**Checkpoint**: US1 MVP independently demonstrable

---

## Phase 4: User Story 2 - Cohesive navigation and work layout (Priority: P2)

**Goal**: Selection hierarchy, nav collapse at constrained width, documents stay in nav, in-panel filters, label truncation

**Independent Test**: Seeded lists; filter; nav toggle at ~1280×800; documents tab keeps chat main

### Tests for User Story 2 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T020 [P] [US2] Failing component test that documents tab does not replace chat main surface in **tests**/documents-nav.test.tsx
- [x] T021 [P] [US2] Failing tests for workspace/conversation filter UI no-matches and clear in **tests**/sidebar-filter.test.tsx
- [x] T022 [P] [US2] Failing tests for nav collapse/restore control accessibility in **tests**/nav-collapse.test.tsx

### Implementation for User Story 2

- [x] T023 [US2] Implement `navOpen` shell state and restore toggle wiring in app/page.tsx
- [x] T024 [US2] Wire nav collapse/expand control with accessible name in components/sidebar.tsx
- [x] T025 [P] [US2] Add in-panel workspace filter UI bound to lib/list-filter.ts in components/sidebar.tsx
- [x] T026 [P] [US2] Add in-panel conversation filter UI bound to lib/list-filter.ts in components/sidebar.tsx
- [x] T027 [US2] Enforce documents tab lists docs in nav only while ChatPanel remains main in app/page.tsx and components/sidebar.tsx
- [x] T028 [US2] Strengthen selected workspace/conversation/active-tab visual hierarchy in components/sidebar.tsx
- [x] T029 [US2] Truncate long labels with native `title` affordance (FR-016) in components/sidebar.tsx

**Checkpoint**: US1 + US2 independently valid

---

## Phase 5: User Story 3 - Predictable feedback and accessible interaction (Priority: P3)

**Goal**: Loading/error recovery (incl. SC-003 200ms), keyboard focus, appearance control UI

**Independent Test**: Load/send failures; tab primary controls; change appearance and reload

### Tests for User Story 3 (REQUIRED — write first, ensure FAIL) ⚠️

- [x] T030 [P] [US3] Failing component tests: loading indicator within 200ms (fake timers) and recoverable error UI in **tests**/async-feedback.test.tsx (SC-003)
- [x] T031 [P] [US3] Failing keyboard/focus smoke tests for filter, panel toggles, appearance control in **tests**/keyboard-focus.test.tsx
- [x] T032 [P] [US3] Failing test that appearance control persists mode across reload simulation in **tests**/appearance-control.test.tsx

### Implementation for User Story 3

- [x] T033 [P] [US3] Add region-specific loading indicators for workspace/conversation/document lists in components/sidebar.tsx
- [x] T034 [P] [US3] Add chat send/response loading and recoverable error UI in components/chat-panel.tsx
- [x] T035 [US3] Surface upload/list failure messages with retry/dismiss in components/sidebar.tsx
- [x] T036 [US3] Add appearance preference control (light/dark/system) calling lib/appearance.ts in components/sidebar.tsx
- [x] T037 [US3] Ensure visible focus styles on primary controls in app/globals.css and components/ui/button.tsx
- [x] T038 [US3] Fix modal focus trap / restore on close in components/modal-id-name.tsx

**Checkpoint**: All three stories independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validation against plan targets and constitution

- [x] T039 [P] Run and fix full unit suite via `pnpm test` until green; update failing tests under **tests**/ as needed
- [x] T040 Execute manual scenarios in specs/001-improve-app-ui/quickstart.md (1280×800 SC-006, panel toggles, privacy no-analytics, SC-003 stopwatch if needed)
- [x] T041 YAGNI/KISS/DRY pass: remove unused abstractions; confirm no new CDN/cloud UI deps in package.json and app/layout.tsx
- [x] T042 [P] Regression smoke checklist: workspace CRUD entry points, document upload entry, conversation list, chat compose, citations/sources still reachable in components/sidebar.tsx, components/chat-panel.tsx, components/sources-panel.tsx (FR-010)
- [x] T043 Confirm loading/error/empty coverage still matches FR-001–FR-006 across components/sidebar.tsx, components/chat-panel.tsx, components/sources-panel.tsx

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Start immediately
- **Foundational (Phase 2)**: Depends on Setup — each helper pair is test→impl; BLOCKS stories
- **US1 → US2 → US3**: Recommended priority order after Foundational
- **Polish**: After desired stories

### User Story Dependencies

- **US1**: Sources visibility in shell; no dependency on filters/appearance UI
- **US2**: Builds on US1 sources toggle; adds nav collapse + filters
- **US3**: Feedback/a11y/appearance control across shell

### Within Each Behavior

- Tests MUST FAIL before implementation (Phase 2 pairs and each US test block)
- Do not “pre-implement” helpers without T004/T006/T008 (etc.) red

### Parallel Opportunities

- T002 || T003 after T001
- After T005: T006||T008 then T007||T009
- US1: T012||T013||T014; T015||T016
- US2: T020||T021||T022; T025||T026
- US3: T030||T031||T032; T033||T034

---

## Parallel Example: Foundational list-filter

```bash
Task: "Failing unit tests in __tests__/list-filter.test.ts"   # must FAIL
Task: "Implement lib/list-filter.ts"                         # then green
```

---

## Parallel Example: User Story 1

```bash
Task: "Failing empty-states tests in __tests__/empty-states.test.tsx"
Task: "Failing panel-visibility tests in __tests__/panel-visibility.test.tsx"
Task: "Failing chat-empty test in __tests__/chat-empty.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup
2. Phase 2 Foundational (TDD helpers)
3. Phase 3 US1 (empty states + sources auto-hide/toggle)
4. **STOP and VALIDATE** via quickstart §1
5. Demo if ready

### Incremental Delivery

1. Setup + Foundational
2. US1 → empty + sources lifecycle MVP
3. US2 → layout, nav collapse, filters
4. US3 → feedback, keyboard, appearance UI
5. Polish → quickstart + FR-010 smoke

---

## Notes

- Remediation 2026-08-01: C1 TDD order, C2 sources in US1, I1 visibility formula, G1 SC-003 fake timers
- [P] = different files, no incomplete dependencies
- Backend (`backend/`) out of scope
