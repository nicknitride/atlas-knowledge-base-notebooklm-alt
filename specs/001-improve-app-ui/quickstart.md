# Quickstart Validation: Improve Application UI

**Feature**: `001-improve-app-ui` | **Date**: 2026-08-01

Runnable checks to prove the UI shell work end-to-end. See
[contracts/ui-shell.md](./contracts/ui-shell.md) and
[data-model.md](./data-model.md) for behavioral details.

## Prerequisites

- Node / pnpm available; dependencies installed (`pnpm install`).
- Local stack per root `README.md` (`.env` from `.env.example`,
  `docker-compose up --build` if API/data needed for lists and chat).
- App at `http://localhost:3000`.
- Optional: Ollama running for chat send paths; empty/loading UI can be checked
  without a successful model response if failures are forced.

## Automated tests

```bash
pnpm test
```

Expected: all Vitest/RTL suites green for appearance preference, list filter,
empty-state rendering, and sources visibility rules (once implemented).

## Manual scenarios

### 1. First-run / empty states (US1)

1. Open the app with no workspace selected (clear selection if needed).
2. Confirm main guidance to create/select a workspace and obvious primary action.
3. Create/select an empty workspace.
4. Confirm chat empty state with start-conversation action; documents tab empty
   state with add-source action.
5. Confirm sources are hidden with zero citations; opening via toggle shows calm
   empty (not error). (US1 includes this sources lifecycle; nav narrow-collapse
   and filters are US2.)

### 2. Layout, filter, documents tab (US2)

1. Seed or create ≥5 workspaces and ≥10 conversations in one workspace.
2. Confirm moderate restyle still shows three regions (when expanded).
3. Switch Chat vs Documents in nav: lists change; **main stays chat**.
4. Filter workspaces and conversations; clear filter; confirm no-matches state.
5. Resize to ~1280×800: chat remains usable; collapse/restore nav and sources
   via toggles; no horizontal page-chrome scroll for select + send.

### 3. Feedback, keyboard, appearance (US3)

1. Trigger slow load or throttle network: region loading indicators appear
   within ~200ms of action start (SC-003; stopwatch or DevTools OK for manual).
2. Force a failed load/send: recoverable error (retry/dismiss), not silent.
3. Tab through nav, filter, compose, appearance, panel toggles: visible focus.
4. Set appearance to light, dark, and system; reload — preference persists.
5. Confirm no required third-party analytics request on load (privacy).

## Pass criteria

- Matches acceptance scenarios in [spec.md](./spec.md).
- SC-002–SC-006 verifiable via the steps above; SC-001 via structured
  walkthrough if no formal study.
