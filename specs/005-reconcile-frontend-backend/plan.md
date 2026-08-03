# Implementation Plan: Reconcile Frontend With New Backend

**Branch**: `005-reconcile-frontend-backend` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-reconcile-frontend-backend/spec.md`

## Summary

Repair the Next.js web app so it correctly consumes the hardened backend from
`002-improve-backend`. Primary fix: workspace delete confirmation must not
trigger a native form submit/page reload, and must complete the DELETE, update
local state, and surface failures. Broader reconciliation: parse the flat
`{ code, message, requestId }` error envelope on all mutating calls; handle SSE
`error` events during chat instead of treating mid-stream failure as success;
map distinct failure outcomes (validation, not-found, embedding mismatch,
provider unavailable) to user-visible messages; allow deleting the last
workspace; clarify document queued/processing/ready/failed states. No backend
contract changes; no new client libraries.

## Technical Context

**Language/Version**: TypeScript 5.7, React 19.2, Next.js 16.2 (App Router,
client components under `app/` + `components/`)

**Primary Dependencies**: Existing Vitest + Testing Library + jsdom; `fetch` /
`AbortController` for HTTP and SSE; existing `ErrorBanner` / `EmptyState` /
`LoadingRegion` in `components/ui-state.tsx`. No new data-fetching or state
libraries (constitution simplicity).

**Storage**: N/A on the client — all persistence is via the Spring Boot API
(`NEXT_PUBLIC_ATLAS_API_URL`, default `http://localhost:8080`)

**Testing**: Vitest (`pnpm test`); extend `__tests__/` with TDD before each
user-story fix. Prefer component/integration tests that mock `lib/api.ts` or
`fetch` for mutation/error paths; unit tests for error-code → message mapping
and SSE parser behavior.

**Target Platform**: Local Docker Compose (`web` `:3000` → `api` `:8080`) +
browser; CORS origin `ATLAS_WEB_ORIGIN` (default `http://localhost:3000`)

**Project Type**: Web application — **frontend-only reconciliation**; backend
is source of truth and frozen for this feature

**Performance Goals**: Workspace delete UI reflects success within 1s of API
confirmation; post-delete workspace switch loads lists within 2s; document
status converges within 5s of backend state (SC / NFR from spec)

**Constraints**: Local-first; no third-party exfiltration; no new abstraction
layers; remove production `console.log` on user-facing paths; DELETEs return
204 (no body); upload returns 202; SSE events: `chunk` | `citations` | `done` |
`error`

**Scale/Scope**: Single-user local app; touch primarily `lib/api.ts`,
`components/sidebar.tsx`, `components/chat-panel.tsx`, and related Vitest
files; optional small shared message-map helper under `lib/`

## Constitution Check

_GATE: Must pass before Phase 0 research. Re-check after Phase 1 design._

| Gate                                      | Status | Notes                                                                                          |
| ----------------------------------------- | ------ | ---------------------------------------------------------------------------------------------- |
| **I. Test-First**                         | Pass   | Tasks will list failing Vitest cases before each story’s implementation                        |
| **II. Local-First / Ollama**              | Pass   | Provider-unavailable messaging exercised against local stack; no cloud required                |
| **III. Performance & UX**                 | Pass   | Spec NFRs for delete/switch/doc lag; loading/error/empty states required per repaired path     |
| **IV. Organization & Grounded Synthesis** | Pass   | Preserve workspaces/docs/conversations; FR-018 — no grounded presentation without citations    |
| **V. Privacy**                            | Pass   | Errors stay local; no telemetry; messages from own API only                                    |
| **VI. Configurability**                   | Pass   | No new knobs; existing `NEXT_PUBLIC_ATLAS_API_URL` / CORS / upload limits unchanged            |
| **VII. Simplicity**                       | Pass   | Repair existing API client + components; no React Query/SWR/Zustand; Complexity Tracking empty |

**Post-design re-check**: Still Pass — contracts document client-facing
consumption of existing backend shapes only; data model is UI/client view of
entities already owned by the API; no new services or frameworks.

## Project Structure

### Documentation (this feature)

```text
specs/005-reconcile-frontend-backend/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api-client-errors.md
│   ├── workspace-mutations.md
│   └── sse-chat-stream.md
└── tasks.md             # /speckit-tasks — not created here
```

### Source Code (repository root)

```text
app/
├── page.tsx                 # client shell; workspace/conversation selection
├── layout.tsx
└── globals.css

components/
├── sidebar.tsx              # workspaces, docs, conversations, delete modal (P1)
├── chat-panel.tsx           # stream send, error banners (P2)
├── sources-panel.tsx
├── ui-state.tsx             # ErrorBanner, EmptyState, LoadingRegion
└── ui/button.tsx

lib/
├── api.ts                   # fetch helpers + streamChatMessage (central error parse)
├── chat-main-mode.ts
├── list-filter.ts
└── (optional) api-error-messages.ts  # code → user copy if extracted

__tests__/                   # Vitest + Testing Library
├── (new) workspace-delete.test.tsx
├── (new) api-errors.test.ts
├── (new) stream-error-event.test.ts
└── existing UI/empty/async tests

docker-compose.yml
.env.example
README.md
```

**Structure Decision**: Stay in the existing monorepo layout (Next.js app at
repo root + Spring `backend/`). This feature changes only the frontend tree
above; backend remains read-only reference for contracts.

## Complexity Tracking

> No constitution violations requiring justification.
