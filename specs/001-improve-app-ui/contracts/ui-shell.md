# UI Contract: Atlas Shell

**Feature**: `001-improve-app-ui` | **Date**: 2026-08-01

Behavioral contract for the main application shell. No new HTTP endpoints.

## Regions

| Region ID | Role                                                                                       | Host          |
| --------- | ------------------------------------------------------------------------------------------ | ------------- |
| `nav`     | Workspaces, chat/documents tabs, conversation or document lists, filters, appearance entry | Sidebar       |
| `main`    | Chat work surface (always)                                                                 | Chat panel    |
| `sources` | Citations / source references for active conversation                                      | Sources panel |

## Appearance API (client)

| Item        | Contract                                                                          |
| ----------- | --------------------------------------------------------------------------------- |
| Storage key | `atlas.appearance`                                                                |
| Values      | `light` \| `dark` \| `system`                                                     |
| Apply       | `document.documentElement.classList` contains `dark` iff effective scheme is dark |
| Default     | `system`                                                                          |
| Persistence | Survives full reload (SC-005)                                                     |

## Panel visibility

**Canonical sources formula** (see data-model.md):

```text
sourcesVisible = sourcesForcedOpen
  || (citations.length > 0 && !sourcesUserCollapsed)
```

| Condition                              | `nav`                           | `sources`                                                          |
| -------------------------------------- | ------------------------------- | ------------------------------------------------------------------ |
| Wide viewport, default                 | `navOpen` (default true)        | Visible iff formula true (hidden when no citations and not forced) |
| User collapsed nav                     | Hidden; restore control visible | Unchanged                                                          |
| User collapsed sources (has citations) | Unchanged                       | Hidden (`sourcesUserCollapsed`); restore control visible           |
| Citations empty, user opens sources    | —                               | `sourcesForcedOpen`; calm empty (not error)                        |
| Citations become non-empty             | —                               | Visible unless `sourcesUserCollapsed`                              |
| Documents tab active                   | Lists documents in nav          | Unchanged rules; **main remains chat**                             |

Restore controls MUST be keyboard-focusable and labeled (accessible name).

## Navigation views

| Tab         | Nav content                    | Main content     |
| ----------- | ------------------------------ | ---------------- |
| `chat`      | Conversation list (+ filter)   | Chat             |
| `documents` | Document list (+ upload entry) | Chat (unchanged) |

## Filter contract

| List          | Input          | Match                              | Empty                                              |
| ------------- | -------------- | ---------------------------------- | -------------------------------------------------- |
| Workspaces    | In-panel query | Case-insensitive substring on name | Calm “no matches” if query non-empty and zero hits |
| Conversations | In-panel query | Case-insensitive substring on name | Same                                               |

Clearing query restores unfiltered list. Document list filter not required.

## Empty states (frozen CTA intent)

| State                                   | Primary action                                             |
| --------------------------------------- | ---------------------------------------------------------- |
| No workspace selected                   | **Create workspace** (secondary: select if list non-empty) |
| Workspace, no conversations             | **Start conversation**                                     |
| Workspace, no documents (documents tab) | **Upload source**                                          |
| Sources opened, no citations            | Informative only (no error); close/hide via sources toggle |
| Filter no matches                       | **Clear filter**                                           |

## Loading & error

| Surface                | Loading          | Error                   |
| ---------------------- | ---------------- | ----------------------- |
| Workspace list         | Region indicator | Message + retry         |
| Conversation list      | Region indicator | Message + retry         |
| Document list / upload | Region indicator | Message + retry/dismiss |
| Chat send / response   | In-main progress | Message + retry/dismiss |

## Focus

- Primary controls expose visible `:focus-visible` styles.
- Dialogs/modals trap focus and restore on close.
- Filter inputs are in tab order with lists.
