# Contract: Workspace Mutations (UI)

**Feature**: `005-reconcile-frontend-backend` | **Date**: 2026-08-01

UI/behavior contract for workspace create, rename, and delete in the sidebar.
Aligns with FR-001–FR-008 and Story 1 acceptance scenarios.

## Delete confirmation

| Requirement             | Behavior                                                     |
| ----------------------- | ------------------------------------------------------------ |
| Open                    | Trash control sets retained `id` + `name` and opens modal    |
| Label                   | Prompt names the workspace (`Delete "{name}"?`)              |
| Cancel                  | Closes modal; clears retained id/name; no HTTP call          |
| Confirm                 | **MUST NOT** cause document navigation or full page reload   |
| Confirm                 | Issues exactly one `DELETE /api/workspaces/{id}`             |
| In progress             | Confirm control disabled until settle (no double submit)     |
| Success (204)           | Close modal; remove from list; clear retained state          |
| Success (404 NOT_FOUND) | Same as success                                              |
| Failure (other)         | Keep workspace in list; show error; allow retry              |
| Availability            | Delete control available even when only one workspace exists |

## Selection after delete

| Case                                | Behavior                                                  |
| ----------------------------------- | --------------------------------------------------------- |
| Deleted was selected; others remain | Select a remaining workspace; load its docs/conversations |
| Deleted was selected; none remain   | Empty state; clear docs, conversations, citations         |
| Deleted was not selected            | Selection, open conversation, citations unchanged         |

## Create / rename

| Action | HTTP                                        | UI on failure                                       |
| ------ | ------------------------------------------- | --------------------------------------------------- |
| Create | `POST /api/workspaces` `{ name }` → 201     | Error visible; modal stays open with text preserved |
| Rename | `PUT /api/workspaces/{id}` `{ name }` → 200 | Same                                                |

Success paths keep existing refresh/select behavior; failures MUST NOT only
log to the console.

## Observability

Production user paths MUST NOT emit diagnostic `console.log` for delete
submit (or equivalent). Errors may use `console.error` in development but
MUST also show UI feedback.
