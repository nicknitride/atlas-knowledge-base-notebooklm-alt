# Research: Fix New Conversation Logic

**Feature**: `004-fix-new-conversation-logic` | **Date**: 2026-08-01

## R1 — Root cause of “stuck on three-button page”

**Decision**: Treat the bug as incorrect empty-state branching in
`components/chat-panel.tsx`, not a failed API create or missing selection.

**Findings**:
- `Sidebar.handleNewConversation` already calls `createConversation`, prepends
  the conversation, `onSelectConversation(conv.id)`, and `onSelectTab("chat")`.
- `ChatPanel` renders the “Start a conversation” CTA **and** the three
  suggestion prompts whenever `messages.length === 0`, even if
  `conversationId` is set.
- After modal close, `ModalIdName` restores focus to the previously focused
  control (often the Start / New Conversation button), which reinforces the
  stuck pre-start feel.

**Rationale**: Selection already works; the main pane still shows the pre-start
experience for any empty message list.

**Alternatives considered**:
- Backend change / auto-seed a system message — rejected (YAGNI; empty thread
  is valid).
- Force-navigate via URL route — rejected (app is single-page shell state).

## R2 — Pre-start vs empty-thread branching

**Decision**: Branch main chat content on selection, not only message count:

| Condition | Main experience |
|-----------|-----------------|
| `!workspaceId` | No-workspace empty (unchanged) |
| `workspaceId && !conversationId` | **Pre-start**: Start CTA + three suggestion prompts |
| `workspaceId && conversationId && messages.length === 0` | **Empty-thread**: calm empty thread; compose available; **no** Start CTA; **no** three suggestions |
| `conversationId && messages.length > 0` | Message list (unchanged) |

**Rationale**: Matches clarifications and FR-003/FR-004; preserves first-run
guidance when nothing is selected.

**Alternatives considered**:
- Hide suggestions only, keep Start CTA on empty selected thread — rejected
  (clarification: no three prompts; empty selected = compose-ready).
- Show suggestions on empty selected thread as shortcuts — rejected (explicit
  clarification).

## R3 — Compose autofocus policy

**Decision**: Autofocus the message textarea **only** immediately after a
successful create from a supported entry point. Selecting an existing empty
conversation from the list shows empty-thread UI but does **not** autofocus.

**Implementation approach** (smallest):
- Introduce a one-shot focus request signal from create success to `ChatPanel`
  (e.g. incrementing `requestFocusCompose` counter on `page.tsx`, or a boolean
  prop/`focusComposeToken` passed with the new id).
- `ChatPanel` focuses its textarea ref when the token changes and
  `conversationId` is set; then clears/consumes the request.
- Do **not** focus on ordinary `conversationId` changes from list selection.

**Rationale**: Clarification Q2; avoids stealing focus when browsing history.

**Alternatives considered**:
- Always focus compose when `conversationId` set and messages empty — rejected
  (clarification).
- `autoFocus` on textarea whenever empty-thread — rejected (same reason).
- Rely only on modal focus restore — rejected (restores to trigger, not compose).

## R4 — Modal Enter confirm and focus restore

**Decision**: Keep existing `ModalIdName` form submit (Enter confirms when name
non-empty / Confirm enabled). After successful create, consume focus restore so
compose receives focus instead of the trigger button.

**Approach options** (pick one in implementation; prefer smallest):
1. Pass an optional `restoreFocus={false}` (or `onCloseFocus`) when closing after
   successful create, then let ChatPanel focus compose.
2. After create, set focus request token; ChatPanel focus runs after modal
   cleanup (microtask/`requestAnimationFrame`/short `useEffect`) so it wins
   over restore.

**Rationale**: Enter already submits the form; the gap is post-close focus
target + main-pane branching.

**Alternatives considered**:
- Custom Enter handler outside the form — rejected (already works via submit).
- Remove focus trap restore entirely — rejected (hurts cancel/a11y).

## R5 — Documents tab during create

**Decision**: On successful create, always `onSelectTab("chat")` (already present
in `handleNewConversation`) and select the new conversation. Main column remains
`ChatPanel` (documents stay nav-only per 001).

**Rationale**: Clarification Q3 / FR-001.

**Alternatives considered**: Leave Documents tab active — rejected (spec).

## R6 — Failure and cancel paths

**Decision**:
- Cancel / empty name: no selection change (Confirm disabled when blank; cancel
  clears modal state only).
- Create API failure: do not call `onSelectConversation` with a new id; show a
  recoverable error (sidebar or modal-adjacent) so the user is not in an
  ambiguous half-selected empty conversation (FR-005). Today failures only
  `console.error` — add minimal user-visible error.

**Rationale**: Spec edge cases + FR-005.

**Alternatives considered**: Optimistic select then rollback — rejected (more
complex; risk of half-selected state).

## R7 — Testing strategy

**Decision**: Extend/replace `chat-empty.test.tsx` expectations and add a
focused create-flow suite:

1. Pre-start when `workspaceId` set and `conversationId` null → Start CTA +
   suggestions present.
2. Empty-thread when `conversationId` set and messages `[]` → no Start CTA, no
   suggestion buttons; compose enabled.
3. After mocked successful create from sidebar/page wiring →
   `currentConversationId` set, Chat tab active, compose focused (token path).
4. Selecting existing empty conversation → empty-thread UI, compose **not**
   autofocused.

**Rationale**: Constitution I; SC-001/SC-003 automatable.

**Alternatives considered**: Playwright-only — rejected for unit-level branching;
manual quickstart covers Enter timing.
