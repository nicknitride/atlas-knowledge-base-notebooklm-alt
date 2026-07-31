# Data Model: Fix New Conversation Logic

**Feature**: `004-fix-new-conversation-logic` | **Date**: 2026-08-01

UI view-state model. Domain entities remain owned by the existing API; this
feature consumes them and clarifies client selection / empty-state rules.

## Entities (domain — read-only for this feature)

### Workspace

| Field | Notes |
|-------|--------|
| id | Stable identifier; required before create |
| name | Display label |

**Relationships**: Has many Conversations.

### Conversation

| Field | Notes |
|-------|--------|
| id | Stable identifier |
| workspaceId | Parent workspace |
| title / name | From create dialog; may be user-supplied |
| messages | May be empty (`[]`) when newly created |

**Relationships**: Belongs to Workspace; selection drives ChatPanel mode.

**Validation (create)**: Non-blank name required in UI (Confirm disabled /
submit blocked when blank). Failed API create does not select a conversation.

### Message

| Field | Notes |
|-------|--------|
| id, role, content, … | Existing; unchanged send/stream behavior when present |

## Entities (client — view state for this feature)

### ChatMainMode (derived)

Not stored; derived in `ChatPanel` from props + loaded messages:

| Mode | When | Main UI |
|------|------|---------|
| `no-workspace` | `workspaceId == null` | Create-workspace empty state |
| `pre-start` | workspace selected, `conversationId == null` | Start CTA + three suggestion prompts |
| `empty-thread` | `conversationId` set, `messages.length === 0` | Compose-ready empty thread (no CTA, no suggestions) |
| `active-thread` | `conversationId` set, `messages.length > 0` | Message list + compose |

### ShellSelectionState

| Field | Type | Rules |
|-------|------|--------|
| currentWorkspaceId | string \| null | Existing |
| currentConversationId | string \| null | Set to new id only after successful create |
| activeTab | `chat` \| `documents` | MUST become `chat` after successful create |

### ComposeFocusRequest

| Field | Type | Rules |
|-------|------|--------|
| focusComposeToken | number (or equivalent one-shot) | Incremented only on successful create from supported entry points |
| consumption | — | ChatPanel focuses compose once per token; list selection MUST NOT increment |

**Lifecycle**:
1. User confirms create (click or Enter) with non-blank name.
2. API succeeds → append/select conversation → `activeTab = chat` → bump
   `focusComposeToken`.
3. ChatPanel in `empty-thread` focuses textarea; token consumed.
4. Cancel or API failure → no selection change, no token bump.

## State transitions

```text
[pre-start]
    │ create success (+ Chat tab, compose focus)
    ▼
[empty-thread] ── send first message ──► [active-thread]
    ▲
    │ select existing empty conversation (no autofocus)
[conversation list]

[pre-start] ◄── deselect / switch workspace (clears conversation)
```

| Event | From | To | Side effects |
|-------|------|-----|--------------|
| Confirm create (success) | pre-start or Documents tab | empty-thread + chat tab | select id; focus compose |
| Confirm create (fail) | prior | prior | error UI; no select |
| Cancel / blank name | prior | prior | modal closed; no select |
| Select empty conversation | any | empty-thread | no autofocus |
| Select conversation with messages | any | active-thread | unchanged load |

## Validation rules

- Blank name: no create, no selection change (SC-004 / edge case).
- Create failure: not presented as selected empty conversation (FR-005).
- Suggestion prompts only in `pre-start` (FR-003).
