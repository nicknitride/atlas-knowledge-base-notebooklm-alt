# Implementation Plan: Fix New Conversation Logic

**Branch**: `004-fix-new-conversation-logic` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-fix-new-conversation-logic/spec.md`

## Summary

Fix the post-create conversation flow so confirming a new conversation (including
via Enter) lands the user on a compose-ready empty thread with focus in the
message input—not the pre-start “three suggestion buttons” page. Root cause is
UI branching in `ChatPanel`: empty messages currently always render the
pre-start CTA, ignoring whether a conversation is already selected. Fix is
frontend-only: distinguish pre-start (`conversationId == null`) from
empty-thread (`conversationId` set, zero messages), switch nav to Chat on
create, and autofocus compose only immediately after successful create.

## Technical Context

**Language/Version**: TypeScript 5.7, React 19, Next.js 16 (App Router)

**Primary Dependencies**: Existing UI (`components/chat-panel`, `sidebar`,
`modal-id-name`, `ui-state`); Vitest + React Testing Library (+ jsdom) already
in repo for TDD; no new packages

**Storage**: N/A for this feature (existing conversation create API unchanged)

**Testing**: Vitest + RTL for ChatPanel empty-state branching, create→select→
focus flow, and Documents→Chat switch; manual quickstart for Enter confirm and
focus timing (SC-001)

**Target Platform**: Local web app (desktop/laptop browsers); Docker Compose
stack per README

**Project Type**: Web application (Next.js UI + Spring Boot API; this feature
touches UI only)

**Performance Goals**: After create succeeds, pure UI transition to
empty-thread + compose focus under 100ms (spec NFR / SC-001 focus within 1s)

**Constraints**: Local-first; no API/schema changes; YAGNI — selection /
empty-state / focus only; preserve pre-start guidance when no conversation
selected; preserve existing send behavior for conversations with messages

**Scale/Scope**: ~3 components (`chat-panel`, `sidebar`, optionally
`modal-id-name` / `page` for focus handoff) plus colocated tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|--------|
| **I. Test-First** | Pass | Vitest/RTL already available; each user story gets failing tests before implementation in tasks |
| **II. Local-First / Ollama** | Pass | UI-only; create/select/focus; no cloud LLM dependency |
| **III. Performance & UX** | Pass | &lt;100ms UI update after create; distinct pre-start / empty-thread / error states |
| **IV. Organization & Grounded Synthesis** | Pass | Preserves workspace/conversation selection and grounded chat; no citation changes |
| **V. Privacy** | Pass | No new telemetry or third-party sends |
| **VI. Configurability** | Pass | No new settings; reuses existing create dialog |
| **VII. Simplicity** | Pass | Branch on `conversationId` + focus signal; no new services/frameworks |

**Post-design re-check**: Still Pass — contracts are UI behavioral contracts;
data model is view-state only; no unjustified complexity.

## Project Structure

### Documentation (this feature)

```text
specs/004-fix-new-conversation-logic/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── conversation-create-flow.md
└── tasks.md             # /speckit-tasks — not created here
```

### Source Code (repository root)

```text
app/
└── page.tsx                 # selection + optional focus-request handoff

components/
├── chat-panel.tsx           # pre-start vs empty-thread branching; compose focus
├── sidebar.tsx              # create success → select + Chat tab; error path
├── modal-id-name.tsx        # focus restore vs post-create compose focus
└── ui-state.tsx             # reuse EmptyState if needed for empty-thread

lib/
└── api.ts                   # createConversation unchanged

__tests__/
├── chat-empty.test.tsx      # update/extend: pre-start vs empty-thread
├── conversation-create-flow.test.tsx   # new: create → select → focus
└── …
```

**Structure Decision**: Keep the existing Next.js app-root layout. Backend and
`lib/api.ts` conversation create contract remain unchanged.

## Complexity Tracking

> No Constitution Check violations requiring justification.
