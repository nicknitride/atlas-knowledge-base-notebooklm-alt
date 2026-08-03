# UI Contract: Conversation Create → Compose Flow

**Feature**: `004-fix-new-conversation-logic` | **Date**: 2026-08-01

Behavioral contract for starting a conversation and landing in compose.
No new HTTP endpoints. Existing `POST /api/workspaces/{id}/conversations`
unchanged (see `lib/api.ts` `createConversation`).

## Entry points (same create dialog)

| Entry                                    | Host                       | Action                                 |
| ---------------------------------------- | -------------------------- | -------------------------------------- |
| Main pre-start CTA                       | ChatPanel → page → Sidebar | Opens create modal; may force Chat tab |
| Sidebar empty-state “Start conversation” | Sidebar                    | Opens create modal                     |
| Footer “New Conversation”                | Sidebar                    | Opens create modal                     |

All entry points MUST share the same name dialog and success/failure rules.

## Success contract

After successful create (Confirm click **or** Enter with non-blank name):

| Step | Requirement                                                      |
| ---- | ---------------------------------------------------------------- |
| 1    | Conversation exists in list (prepended or refreshed)             |
| 2    | `currentConversationId` equals new conversation id               |
| 3    | Navigation tab is `chat` (switch if was `documents`)             |
| 4    | Main mode is **empty-thread** (not pre-start)                    |
| 5    | Message compose field receives keyboard focus within 1s (SC-001) |
| 6    | Three suggestion prompts are **not** visible                     |
| 7    | “Start conversation” pre-start CTA is **not** visible            |

User MUST be able to type and send the first message without an extra click on
the main pane (SC-002).

## Main-mode contract

| Condition                            | Mode            | Visible                                                             |
| ------------------------------------ | --------------- | ------------------------------------------------------------------- |
| No workspace                         | `no-workspace`  | Create workspace guidance                                           |
| Workspace, no conversation selected  | `pre-start`     | Start CTA + three suggestion prompts                                |
| Conversation selected, zero messages | `empty-thread`  | Empty thread; compose enabled; **no** Start CTA; **no** suggestions |
| Conversation selected, has messages  | `active-thread` | Message history + compose (unchanged)                               |

Canonical branching: selection (`conversationId`) separates pre-start from
empty-thread; message count alone MUST NOT show pre-start when a conversation
is selected. See [data-model.md](../data-model.md).

## Focus contract

| Event                                        | Compose autofocus                                |
| -------------------------------------------- | ------------------------------------------------ |
| Successful create (any entry point)          | **Required**                                     |
| Select existing empty conversation from list | **Forbidden** (compose available; user click OK) |
| Cancel create dialog                         | No change                                        |
| Failed create                                | No autofocus onto a fake selection               |

Modal focus restore MUST NOT leave the user on the pre-start trigger when
create succeeded; compose focus wins after close.

## Failure / cancel contract

| Event              | Selection                     | Main UI    | Feedback                          |
| ------------------ | ----------------------------- | ---------- | --------------------------------- |
| Cancel dialog      | Unchanged                     | Unchanged  | Modal closed                      |
| Blank name confirm | Unchanged                     | Unchanged  | No create (Confirm disabled)      |
| API create error   | Unchanged (not half-selected) | Prior mode | Recoverable error (retry/dismiss) |

## Non-goals

- No change to message send/stream for conversations that already have messages
  (FR-006).
- No new API fields, routes, or persistence.
- Documents content stays in navigation only; main remains chat.
