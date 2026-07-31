# Implementation Plan: Improve Application UI

**Branch**: `001-improve-app-ui` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-improve-app-ui/spec.md`

## Summary

Moderate restyle and UX polish of the existing Atlas three-region shell
(navigation sidebar, chat work surface, sources panel): refreshed design tokens
(typography/spacing/color), purposeful empty/loading/error states, collapsible
nav/sources with restore toggles, auto-hide sources when citations are empty,
documents view remaining in navigation only, client-side in-panel filter for
workspaces and conversations, keyboard-visible focus, and persisted appearance
preference (light / dark / system). Frontend-only; no backend API changes.

## Technical Context

**Language/Version**: TypeScript 5.7, React 19, Next.js 16 (App Router)

**Primary Dependencies**: Tailwind CSS 4, existing `components/ui` (shadcn/Base
UI), lucide-react; add Vitest + React Testing Library (+ jsdom) for TDD; no new
UI framework or design-system package

**Storage**: Browser `localStorage` for appearance preference only; domain data
continues via existing local API (`lib/api.ts`) — unchanged

**Testing**: Vitest + React Testing Library for component/UI state tests; manual
quickstart checklist for layout at 1280×800 and keyboard paths (Playwright not
required for v1 of this feature)

**Target Platform**: Local web app (desktop/laptop browsers); Docker Compose
stack per README

**Project Type**: Web application (Next.js UI + Spring Boot API; this feature
touches UI only)

**Performance Goals**: Pure UI state changes (selection, panel toggle, filter,
empty-state render) perceived under 100ms; loading indicators visible within
200ms of async action start (SC-003)

**Constraints**: Local-first / no CDN or cloud theming; no new telemetry; keep
three-region IA; YAGNI — no full redesign or new AI features; remove or disable
required third-party analytics when touching root layout (privacy)

**Scale/Scope**: Single main page shell; ~4 primary components
(`page`, `sidebar`, `chat-panel`, `sources-panel`) plus small presentational
helpers (empty state, list filter, appearance control)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|--------|
| **I. Test-First** | Pass | Vitest + RTL introduced; each user story gets failing tests before implementation in tasks |
| **II. Local-First / Ollama** | Pass | UI-only; no cloud LLM; no CDN fonts/themes |
| **III. Performance & UX** | Pass | Targets above; empty/loading/error specified in spec |
| **IV. Organization & Grounded Synthesis** | Pass | Preserves workspaces, docs, chat, citations; sources panel behavior clarified |
| **V. Privacy** | Pass | Appearance in `localStorage`; no new telemetry; gate existing Vercel Analytics when editing layout |
| **VI. Configurability** | Pass | Appearance light/dark/system user-configurable |
| **VII. Simplicity** | Pass | Moderate restyle of existing shell; no new service layer; client-side filter only |

**Post-design re-check**: Still Pass — contracts are UI behavioral contracts only;
data model is preference + view state; no unjustified new packages beyond test
runner required by constitution I.

**Analyze remediation (2026-08-01)**: tasks.md now red→green for
`ui-state` / `list-filter` / `appearance` before wiring; sources visibility MVP
in US1; canonical `sourcesVisible` formula in research + data-model + contract;
SC-003 covered via fake-timer tests in US3.

## Project Structure

### Documentation (this feature)

```text
specs/001-improve-app-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── ui-shell.md
└── tasks.md             # /speckit-tasks — not created here
```

### Source Code (repository root)

```text
app/
├── layout.tsx           # theme class bootstrap; privacy-safe analytics handling
├── page.tsx             # shell layout; panel open state; citation → sources visibility
└── globals.css          # design tokens (moderate restyle)

components/
├── sidebar.tsx          # nav, chat/docs views, filters, collapse toggle, empty/load/error
├── chat-panel.tsx       # empty/loading/error for chat; focus styles
├── sources-panel.tsx    # citations list; empty when manually opened with none
├── modal-id-name.tsx    # focus trap / a11y polish as needed
└── ui/
    ├── button.tsx
    └── …                # small additions only if needed (input, etc.)

lib/
├── api.ts               # unchanged contracts
├── utils.ts
└── appearance.ts        # get/set appearance preference (localStorage)

__tests__/ or colocated *.test.tsx
├── appearance.test.ts
├── list-filter.test.ts
├── empty-states.test.tsx
├── panel-visibility.test.tsx
└── …
```

**Structure Decision**: Keep the existing Next.js app-root layout (`app/`,
`components/`, `lib/`). Do not introduce a separate frontend package. Backend
remains untouched for this feature.

## Complexity Tracking

> No constitution violations requiring justification.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
