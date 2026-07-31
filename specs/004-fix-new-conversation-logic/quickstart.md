# Quickstart Validation: Fix New Conversation Logic

**Feature**: `004-fix-new-conversation-logic` | **Date**: 2026-08-01

Runnable checks to prove create → empty-thread → focused compose. See
[contracts/conversation-create-flow.md](./contracts/conversation-create-flow.md)
and [data-model.md](./data-model.md).

## Prerequisites

- Dependencies installed (`pnpm install`).
- Local stack per root `README.md` (`.env` from `.env.example`,
  `docker compose up --build` if API needed for create).
- App at `http://localhost:3000`.
- At least one workspace selected (create one if needed).
- Ollama optional for this feature (create/select/focus do not require a model
  response).

## Automated tests

```bash
pnpm test
```

Expected (once implemented): Vitest/RTL green for:

- Pre-start UI when workspace selected and no conversation selected.
- Empty-thread UI when conversation selected with zero messages (no Start CTA,
  no three suggestions).
- Create-success path selects conversation, switches to Chat tab, focuses
  compose.
- Selecting an existing empty conversation does not autofocus compose.

## Manual scenarios

### 1. Main CTA → Enter → compose (US1)

1. Select a workspace; ensure no conversation is selected (pre-start visible:
   Start CTA + three suggestion buttons).
2. Click **Start conversation** in the main area.
3. Type a name; press **Enter** (do not click Confirm).
4. Confirm: pre-start gone; empty thread; caret in message compose within ~1s;
   type a character without clicking the main pane.

Repeat once via sidebar empty-state **Start conversation** and once via footer
**New Conversation**.

### 2. Documents tab create (US1)

1. With a workspace selected, open **Documents** in navigation.
2. Create a new conversation and confirm.
3. Confirm: nav switches to **Chat**, new conversation selected, compose
   focused, no three-suggestion page.

### 3. Pre-start vs empty-thread (US2)

1. Workspace selected, no conversation selected → pre-start guidance +
   suggestions visible.
2. Create or select a conversation with no messages → suggestions and Start CTA
   gone; compose available.
3. If selecting an existing empty conversation from the list: empty-thread UI
   without mandatory autofocus (click compose to type is OK).

### 4. Cancel and failure (edge)

1. Open create dialog → Cancel → selection and main mode unchanged.
2. Leave name blank → Confirm disabled / no create.
3. Force create failure (stop API or invalid env) → recoverable error; not
   stuck as a selected empty conversation.

## Pass criteria

- Matches acceptance scenarios in [spec.md](./spec.md).
- SC-001–SC-004 verifiable via the steps above.
- Pure UI transition after successful create feels immediate (&lt;100ms target
  for state update; focus within 1s).
