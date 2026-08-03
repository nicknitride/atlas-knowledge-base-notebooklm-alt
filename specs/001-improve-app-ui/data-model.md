# Data Model: Improve Application UI

**Feature**: `001-improve-app-ui` | **Date**: 2026-08-01

UI-centric model. Domain entities (workspace, conversation, document, citation)
remain owned by the existing API; this feature only consumes them and adds
client preference / view state.

## Entities (domain — read-only for this feature)

### Workspace

| Field | Notes                                        |
| ----- | -------------------------------------------- |
| id    | Stable identifier                            |
| name  | Display label; filter target; truncate in UI |

**Relationships**: Has many Conversations, Documents.

### Conversation

| Field        | Notes                                        |
| ------------ | -------------------------------------------- |
| id           | Stable identifier                            |
| workspaceId  | Parent workspace                             |
| name / title | Display label; filter target; truncate in UI |

**Relationships**: Belongs to Workspace; drives ChatPanel selection.

### Document

| Field       | Notes                         |
| ----------- | ----------------------------- |
| id          | Stable identifier             |
| workspaceId | Parent workspace              |
| name        | Display label; truncate in UI |

**Relationships**: Belongs to Workspace; listed only in navigation documents view.

### Citation

| Field                         | Notes                  |
| ----------------------------- | ---------------------- |
| id / index                    | Per-response reference |
| title / snippet / source refs | Shown in sources panel |

**Relationships**: Associated with active conversation turn/response. Empty
citation list drives sources auto-hide (FR-013).

## Entities (client — new for this feature)

### AppearancePreference

| Field      | Type                          | Rules                      |
| ---------- | ----------------------------- | -------------------------- |
| mode       | `light` \| `dark` \| `system` | Required; default `system` |
| storageKey | constant `atlas.appearance`   | Local only                 |

**Lifecycle**:

1. On load: read storage → if missing, `system` → apply to document root.
2. On change: write storage → re-apply class/`color-scheme`.
3. On `system`: listen to `prefers-color-scheme` changes while mode is `system`.

### ShellViewState

| Field                 | Type                  | Rules                                                            |
| --------------------- | --------------------- | ---------------------------------------------------------------- |
| navOpen               | boolean               | Default true on wide viewports; user toggle; in-memory OK for v1 |
| sourcesForcedOpen     | boolean               | User opened sources while empty / forced visible                 |
| sourcesUserCollapsed  | boolean               | User collapsed sources while citations exist                     |
| activeNavTab          | `chat` \| `documents` | Documents must not replace main chat surface                     |
| workspaceFilter       | string                | Client filter query for workspaces                               |
| conversationFilter    | string                | Client filter query for conversations                            |
| currentWorkspaceId    | string \| null        | Existing                                                         |
| currentConversationId | string \| null        | Existing                                                         |
| citations             | Citation[]            | Existing; length gates sources visibility                        |

**Derived** (canonical — research R3 + contracts/ui-shell.md):

- `sourcesAutoEligible = citations.length > 0`
- `sourcesVisible = sourcesForcedOpen || (sourcesAutoEligible && !sourcesUserCollapsed)`

### ListFilterResult\<T\>

| Field       | Type                                | Rules                                     |
| ----------- | ----------------------------------- | ----------------------------------------- |
| query       | string                              | Trimmed; case-insensitive match on `name` |
| items       | T[]                                 | Subset of source list                     |
| emptyReason | `none` \| `no-data` \| `no-matches` | Drives empty vs no-matches copy           |

## Validation rules

- Appearance mode MUST be one of the three enum values; invalid stored values
  fall back to `system`.
- Filter MUST NOT mutate server data; clearing query restores full list.
- Truncated labels MUST remain identifiable (tooltip or `title` attribute).

## State transitions (sources panel)

```text
[no citations] --auto--> Hidden (toggle available)
[Hidden] --user opens toggle--> Open empty (calm empty, not error)
[Hidden/Open] --citations arrive--> Available (prefer visible unless userCollapsed)
[Available visible] --user collapses--> Hidden (userCollapsed=true)
[userCollapsed] --user expands--> Visible
```
