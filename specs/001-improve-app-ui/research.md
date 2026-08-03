# Research: Improve Application UI

**Feature**: `001-improve-app-ui` | **Date**: 2026-08-01

## R1 — Design token restyle approach

**Decision**: Refresh CSS custom properties and Tailwind theme tokens in
`app/globals.css` (and minimal component class tweaks). Keep three-region
structure in `app/page.tsx`. No new component library.

**Rationale**: Spec requires moderate restyle (typography, spacing, color
tokens) without a full visual-identity redesign. Tokens already exist for light
and `.dark`; extending them is the smallest change.

**Alternatives considered**:

- New design system package — rejected (YAGNI, FR complexity).
- Pixel-perfect redesign of every control — rejected (out of scope).

## R2 — Appearance preference persistence

**Decision**: Store `atlas.appearance` in `localStorage` with values
`light` | `dark` | `system`. Apply by toggling `class="dark"` on `<html>` (and
respecting `prefers-color-scheme` when `system`). Small `lib/appearance.ts`
module + control in sidebar/settings area.

**Rationale**: Matches existing `.dark` variant in `globals.css`; no server
round-trip; constitution VI + privacy (local only).

**Alternatives considered**:

- Cookie/server preference — rejected (unnecessary for single-user local app).
- CSS-only `prefers-color-scheme` without user override — rejected (FR-008).

## R3 — Panel collapse and sources auto-hide

**Decision**: Shell state in `app/page.tsx` (canonical; matches data-model.md):

- `navOpen: boolean` — user toggle for navigation region (FR-012).
- `sourcesForcedOpen: boolean` — user opened sources while empty (or forced).
- `sourcesUserCollapsed: boolean` — user collapsed sources while citations exist.
- `sourcesAutoEligible = citations.length > 0`
- **`sourcesVisible = sourcesForcedOpen || (sourcesAutoEligible && !sourcesUserCollapsed)`**

Behavior: zero citations → hidden unless `sourcesForcedOpen`; opening toggle sets
`sourcesForcedOpen` and shows calm empty (FR-001/FR-013); citations arriving with
`!sourcesUserCollapsed` → visible (clear forced-open as appropriate); user
collapse with citations sets `sourcesUserCollapsed`. Nav restore toggle is
independent (FR-012).

**Rationale**: Clarifications require chat-primary, collapsible nav/sources, and
auto-hide sources with zero citations. Single boolean formula avoids drift.

**Alternatives considered**:

- Always three columns — rejected (clarification B).
- CSS-only media queries without toggles — rejected (must restore explicitly).
- `sourcesOpen && (citations || forced)` only — rejected (cannot express
  user-collapsed-while-cited cleanly).

## R4 — Documents view stays in navigation

**Decision**: Keep existing sidebar `activeTab` (`chat` | `documents`); do not
swap `ChatPanel` out of the main column when documents tab is active.

**Rationale**: Clarification A; current `page.tsx` already always mounts
`ChatPanel`.

**Alternatives considered**: Main-pane documents browser — rejected (scope).

## R5 — In-panel list filter

**Decision**: Client-side case-insensitive substring filter on workspace and
conversation display names inside `sidebar.tsx` (or tiny pure helper
`lib/list-filter.ts` for TDD). No API changes. Debounce ~150ms optional for
typing comfort; perceived “as you type” is enough.

**Rationale**: Spec FR-015; YAGNI vs full search index.

**Alternatives considered**:

- Server-side search — rejected (no backend change; overkill).
- Document filter — out of scope per assumptions.

## R6 — Test stack (constitution I)

**Decision**: Add Vitest + `@testing-library/react` + `@testing-library/user-event`

- `jsdom`. Colocate or place under `__tests__/`. Scripts: `pnpm test`. No
  Playwright required for this feature’s first delivery; keyboard paths covered
  with `user-event` where practical + quickstart manual pass for SC-006.

**Rationale**: Repo has no test runner today; TDD is non-negotiable. Vitest fits
Next/React without heavy E2E infra.

**Alternatives considered**:

- Jest — heavier migration vs Vitest for Vite-era tooling.
- Playwright-only — slower feedback for empty-state unit work.

## R7 — Privacy when touching layout

**Decision**: When editing `app/layout.tsx` for theme bootstrap, remove or hard-
disable `@vercel/analytics` so the default local path does not phone home
(constitution V, FR-011).

**Rationale**: Feature forbids required third-party services for UI; analytics
conflicts with privacy-by-default.

**Alternatives considered**: Leave analytics — rejected for this privacy-gated
UI work.

## R8 — Empty / loading / error presentation

**Decision**: Small reusable presentational patterns (inline components or
`components/ui-state.tsx`): title, short description, one primary action,
optional retry. Region-scoped spinners/skeletons for list and chat send.

**Rationale**: Spec FR-001–FR-006; avoid multiple one-off copies (DRY) without a
heavy abstraction layer.

**Alternatives considered**: Third-party empty-state kit — rejected (CDN/deps).
