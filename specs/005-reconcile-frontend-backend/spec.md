# Feature Specification: Reconcile Frontend With New Backend

**Feature Branch**: `005-reconcile-frontend-backend`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: "reconcile new backend with frontend (fix broken functionality, deleting a workspace now does nothing but reload the page)"

## Context

The backend was recently reworked (feature `002-improve-backend`): a structured error
envelope was introduced, uploads became an asynchronous accepted-then-processed flow,
embedding identity checks were added, and provider failures now fail closed with
distinct outcomes. The web app was not updated alongside those changes.

The result is a set of user-visible breakages. The most severe: confirming a workspace
deletion reloads the page and the workspace is still there. Several other actions fail
silently — the user sees no change and no error, so the app appears frozen or ignoring
input.

This feature restores end-to-end correctness between the web app and the current
backend behavior. It is a reconciliation and repair effort, not a redesign: no new
product surface, no new backend endpoints.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deleting a workspace actually deletes it (Priority: P1)

A user has several workspaces and wants to remove one they no longer need. They open the
workspace's delete control, read the confirmation prompt naming the workspace, and
confirm. The workspace disappears from the workspace list, the confirmation prompt
closes, and the app moves them to a remaining workspace with its documents and
conversations loaded. The page never reloads, and nothing the user had in progress is
lost to a refresh.

**Why this priority**: This is the reported defect and the most damaging one. The
current behavior destroys user trust twice over — the destructive action appears to do
nothing, and the unexpected page reload discards in-flight UI state. Any user who tries
to clean up their workspaces concludes the app is broken.

**Independent Test**: Create two workspaces, delete one via the confirmation prompt, and
verify the deleted workspace is absent from the list, the prompt has closed, the
surviving workspace is selected with its content loaded, and no page reload occurred.
Reloading the page manually afterwards must still show the workspace as gone, proving
the deletion was persisted and not merely hidden in local state.

**Acceptance Scenarios**:

1. **Given** two or more workspaces exist and one is selected, **When** the user
   confirms deletion of the selected workspace, **Then** the workspace is removed from
   the list, the confirmation prompt closes, a remaining workspace becomes selected with
   its documents and conversations loaded, and the page does not reload.
2. **Given** two or more workspaces exist, **When** the user confirms deletion of a
   workspace that is **not** currently selected, **Then** that workspace is removed from
   the list and the user's current selection, open conversation, and citations are left
   untouched.
3. **Given** a workspace has been deleted, **When** the user reloads the page, **Then**
   the deleted workspace does not reappear.
4. **Given** the delete confirmation prompt is open, **When** the user cancels or
   dismisses it, **Then** no deletion occurs, the prompt closes, and the workspace list
   is unchanged.
5. **Given** only one workspace exists, **When** the user deletes it, **Then** it is
   removed and the app shows an empty state inviting the user to create a workspace,
   with document, conversation, and citation panels cleared.
6. **Given** the workspace was already removed by another means, **When** the user
   confirms deletion, **Then** the app treats the workspace as gone, removes it from the
   list, and does not present a failure to the user.
7. **Given** the deletion request fails because the backend is unreachable, **When** the
   user confirms deletion, **Then** the workspace remains in the list, an error message
   explains the deletion did not happen, and the user can retry.

---

### User Story 2 - Failed actions tell the user what went wrong (Priority: P2)

A user performs an action — creating or renaming a workspace, renaming or deleting a
conversation, deleting a document — and the backend rejects it or is unavailable. The
user sees a clear message describing what failed and, where the backend supplied a
reason, that reason. The affected list returns to a truthful state rather than showing an
optimistic change that never persisted.

**Why this priority**: The backend now distinguishes meaningfully between failure
outcomes: a request rejected for invalid input, a resource that no longer exists, an
embedding configuration conflict, and an unavailable AI provider all mean different
things and require different responses from the user. The web app currently discards all
of this and shows nothing, so every failure looks identical to an unresponsive app. This
is second only to P1 because it converts silent breakage into actionable feedback across
every remaining action.

**Independent Test**: With the backend returning each failure outcome in turn, trigger
each mutating action and verify a user-visible message appears that reflects the specific
outcome, and that no list shows a change which was not actually persisted.

**Acceptance Scenarios**:

1. **Given** the backend rejects a workspace name as invalid, **When** the user submits
   it, **Then** an error message identifying the problem with the name is shown next to
   the input and the prompt stays open with the user's text preserved.
2. **Given** the backend is unreachable, **When** the user performs any create, rename,
   or delete action, **Then** an error message states the action failed and the list
   still reflects the last known-good state.
3. **Given** a conversation or document was already deleted, **When** the user deletes it
   again, **Then** it is removed from the list without presenting an error.
4. **Given** the backend reports that a workspace's stored embeddings were produced with
   a different embedding configuration than the one now in use, **When** the user asks a
   question in that workspace, **Then** the user is told the workspace needs its
   documents re-processed before answers can be grounded, rather than seeing a generic
   failure.
5. **Given** the AI provider is unavailable or misconfigured, **When** the user asks a
   question, **Then** the user is told the AI service could not be reached and that no
   answer was produced — never a silent empty response or a fabricated answer.

---

### User Story 3 - Chat failures surface instead of vanishing (Priority: P2)

A user asks a question about their documents. If the backend fails partway through
answering, the user sees an error explaining the answer could not be completed. If the
answer succeeds, the answer and its citations appear. In neither case is the user left
looking at a spinner that stops with nothing to show.

**Why this priority**: Grounded Q&A is the core value of the product, and mid-answer
failures currently resolve as if the answer completed successfully — the user gets an
empty result with no explanation. Same priority as Story 2 because it is the same class
of defect on the most important path.

**Independent Test**: Trigger a mid-answer backend failure and verify an error is shown
and the loading state ends; then trigger a successful answer and verify the answer and
citations appear.

**Acceptance Scenarios**:

1. **Given** the user has asked a question, **When** the backend reports a failure
   partway through producing the answer, **Then** the loading state ends and an error
   message explains the answer could not be completed, including the backend's reason
   where one was given.
2. **Given** the user has asked a question, **When** the answer completes successfully,
   **Then** the answer appears with its citations and the loading state ends.
3. **Given** the workspace contains no successfully processed documents, **When** the
   user asks a question, **Then** the user is told there is nothing to search yet and is
   pointed toward uploading documents.
4. **Given** an answer is in progress, **When** the user navigates to a different
   conversation or workspace, **Then** the in-progress request stops and its result does
   not appear in the newly opened conversation.

---

### User Story 4 - Upload progress reflects asynchronous processing (Priority: P3)

A user uploads a document. The app confirms the file was accepted, shows the document as
queued, then shows it progress through processing to ready — or to failed with the
reason. The user knows when the document is actually usable for questions.

**Why this priority**: Upload acceptance and document readiness are now separate events
in the backend. The behavior is not broken so much as imprecise: a user may ask questions
about a document that is still being processed and get no results, with no indication
why. Lower priority because the existing polling already converges on the right state.

**Independent Test**: Upload a document and verify the displayed state moves from queued
through processing to ready, and that an oversized or unsupported file is rejected with a
message naming the reason.

**Acceptance Scenarios**:

1. **Given** the user selects a valid document, **When** the upload is accepted, **Then**
   the document appears immediately in a queued state and progresses to ready without
   requiring a manual refresh.
2. **Given** a document fails processing, **When** its state updates, **Then** it is
   shown as failed with the reason the backend reported.
3. **Given** the user selects a file that is too large or of an unsupported type,
   **When** they attempt to upload it, **Then** the upload is rejected with a message
   naming the specific reason, and the file does not appear in the document list.
4. **Given** documents are still processing, **When** the user opens the workspace's
   document list, **Then** in-progress documents are visually distinguishable from ready
   ones.

---

### Edge Cases

- **Deleting the workspace currently being used**: If a workspace is deleted while a
  question is being answered or a document is uploading in it, those in-flight
  operations must stop and must not write results into the UI for a workspace that no
  longer exists.
- **Deleting the last workspace**: Every panel that assumes a selected workspace
  (documents, conversations, chat, citations) must clear to an empty state rather than
  showing content belonging to the deleted workspace.
- **Confirmation prompt reuse**: If a user opens the delete prompt for one workspace,
  cancels, then opens it for another, the prompt must name and act on the second
  workspace only.
- **Rapid repeated confirmation**: Double-clicking confirm must not issue duplicate
  deletions or produce a spurious "not found" error on the second attempt.
- **Backend rejects a request with a body the app cannot interpret**: Failures that do
  not carry the standard reason (for example, a request rejected before reaching the
  application, such as an oversized upload blocked at the transport limit) must still
  produce a sensible user-facing message rather than an unhandled failure.
- **Stale selection after external change**: If the selected workspace no longer exists
  when its content is requested, the app must recover by selecting an existing workspace
  or showing the empty state, not by repeatedly failing.
- **Concurrent list changes**: Removing an item from a list must not resurrect items
  removed by a concurrent action.

## Requirements *(mandatory)*

### Functional Requirements

**Workspace deletion (P1)**

- **FR-001**: Confirming a workspace deletion MUST NOT cause a page reload or navigation.
- **FR-002**: Confirming a workspace deletion MUST issue exactly one deletion request for
  the workspace named in the confirmation prompt.
- **FR-003**: On successful deletion, the system MUST remove the workspace from the
  workspace list, close the confirmation prompt, and clear the confirmation prompt's
  retained workspace identity.
- **FR-004**: On successful deletion of the currently selected workspace, the system MUST
  select a remaining workspace and load that workspace's documents and conversations, or
  show the no-workspace empty state if none remain.
- **FR-005**: On successful deletion of a workspace that is not selected, the system MUST
  leave the current selection, open conversation, and citations unchanged.
- **FR-006**: Deletion MUST be available for every workspace, including when only one
  workspace exists.
- **FR-007**: A deletion that fails MUST leave the workspace present in the list and MUST
  present a user-visible error, except where the failure indicates the workspace no
  longer exists — which MUST be treated as success.
- **FR-008**: While a deletion is in progress, the system MUST prevent the confirmation
  from being submitted again.

**Error feedback (P2)**

- **FR-009**: Every failed create, rename, delete, upload, and question action MUST
  present a user-visible message; no failure may be reported only to developer logs.
- **FR-010**: When the backend supplies a reason for a failure, the system MUST present
  that reason to the user rather than a generic substitute.
- **FR-011**: The system MUST distinguish these failure outcomes to the user with
  appropriate messaging: invalid input, resource no longer exists, embedding
  configuration conflict for the workspace, and AI provider unavailable or
  misconfigured.
- **FR-012**: A delete action whose target no longer exists MUST resolve as success from
  the user's perspective and remove the item from the list.
- **FR-013**: The system MUST NOT display an optimistic change that was not persisted;
  on failure, affected lists MUST return to the last known-good state.
- **FR-014**: Error messages MUST NOT expose internal system details such as stack
  traces, internal hostnames, or storage schema information.

**Chat (P2)**

- **FR-015**: When the backend reports a failure while producing an answer, the system
  MUST end the loading state and present an error describing the failure.
- **FR-016**: The system MUST NOT treat a failed answer as a completed answer.
- **FR-017**: When the user leaves a conversation or workspace, the system MUST stop the
  in-progress answer request and MUST NOT apply its result to a different conversation.
- **FR-018**: The system MUST NOT present an answer as grounded when no citations were
  returned for it.

**Documents (P3)**

- **FR-019**: The system MUST show an accepted upload as queued immediately and reflect
  its progression to ready or failed without requiring a manual refresh.
- **FR-020**: The system MUST show the backend-reported reason for a document that failed
  processing.
- **FR-021**: The system MUST visually distinguish documents that are not yet ready for
  questions from those that are.
- **FR-022**: A rejected upload MUST NOT appear in the document list, and its rejection
  reason MUST be shown to the user.

**Contract consistency (cross-cutting)**

- **FR-023**: Every action the web app offers MUST correspond to a request the current
  backend accepts, using the field names, request shape, and success outcomes the backend
  defines.
- **FR-024**: The system MUST correctly handle successful responses that carry no
  content, without treating the absence of a response body as a failure.
- **FR-025**: The web app MUST NOT represent states or values the backend never produces.
- **FR-026**: Developer diagnostic output MUST NOT be emitted from user-facing action
  paths in a production build.

### Key Entities

- **Workspace**: A named container the user creates and deletes; owns documents and
  conversations. Deleting it removes its documents, conversations, and derived search
  data. Identified by a stable identifier and displayed by name.
- **Document**: A file the user uploaded into a workspace. Carries a processing state
  (queued, processing, ready, failed) and, when failed, a reason. Only ready documents
  can ground answers.
- **Conversation**: An ordered exchange of user questions and grounded answers within a
  workspace. Has a title the user can change.
- **Citation**: A reference from an answer back to the specific location in a document
  that supports it. Answers without citations are not grounded.
- **Failure outcome**: A machine-readable classification plus a human-readable reason and
  correlation identifier accompanying a rejected request; drives which message the user
  sees.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of workspace deletions confirmed by the user result in the workspace
  being gone after a manual page reload, with zero page reloads triggered by the
  confirmation itself.
- **SC-002**: Every mutating action in the app produces either a visible success change
  or a visible error message — zero actions resolve with no observable outcome.
- **SC-003**: Users can delete a workspace and continue working in a remaining workspace
  in under 5 seconds, with no manual refresh required.
- **SC-004**: 100% of backend-reported failure outcomes are represented by a distinct,
  user-comprehensible message; zero failures present as a frozen or unresponsive UI.
- **SC-005**: Zero answers are presented to the user without either supporting citations
  or an explicit statement that the answer could not be grounded.
- **SC-006**: An uploaded document's displayed state reaches its final value (ready or
  failed) within 5 seconds of the backend reaching that state, without user action.
- **SC-007**: A first-time user who deletes their only workspace is presented with a
  clear path to create a new one, with no leftover content from the deleted workspace
  visible anywhere in the app.
- **SC-008**: Regression tests cover every acceptance scenario above and fail against the
  current behavior before the fix.

## Non-Functional Constraints *(mandatory for Atlas)*

- **Local-first**: All reconciled behavior MUST work against the local stack with a local
  Ollama-compatible endpoint. Provider-unavailable messaging MUST be reachable and
  testable without any cloud provider configured.
- **Privacy**: No user content, document text, question text, or failure detail may be
  sent anywhere other than the user's own backend. Failure diagnostics shown to the user
  MUST come from that backend and MUST NOT be forwarded to third parties.
- **Performance**: Workspace deletion reflects in the UI within 1 second of the backend
  confirming it. Workspace switch after deletion loads documents and conversations within
  2 seconds on local hardware. Document state updates converge within 5 seconds of the
  backend state change.
- **UX**: Each repaired path MUST define its loading, error, and empty states — deletion
  in progress, deletion failed, no workspaces, no documents, no conversations, answer in
  progress, answer failed, document processing, document failed. Destructive confirmation
  MUST name the target, be dismissable by keyboard, and return focus to a sensible
  element on close.
- **Configurability**: No new configuration is introduced. Existing settings — backend
  address, allowed web origin, upload size limit, provider selection, and embedding model
  and dimensions — MUST continue to work unchanged, and the app MUST behave correctly
  when the backend address points somewhere other than the default.
- **Simplicity**: This is a repair effort. No client-side data-fetching library, state
  management library, or abstraction layer may be introduced. Fixes stay within the
  existing component and API-client structure; shared failure-message handling may be
  centralized in the existing API client since every action needs it.
- **TDD**: Every acceptance scenario above MUST be written as a failing automated test
  before its fix. The P1 workspace-deletion scenarios MUST include a test proving no page
  reload or native form submission occurs on confirmation.

## Out of Scope

- Rendering answer text incrementally as it arrives; the app may continue to show a
  loading state and then display the completed answer.
- Adding confirmation prompts to document and conversation deletion, which are currently
  immediate.
- Any new backend endpoint, any change to backend request or response shapes, and any
  change to the OpenAPI-described contract.
- Visual redesign, theming, or layout changes beyond the loading, error, and empty states
  named above.
- Authentication, authorization, and multi-user concerns; the app remains single-user and
  local.
- Re-processing documents after an embedding configuration change; this feature only
  informs the user that re-processing is needed.

## Assumptions

- **Deleting the last workspace is permitted.** The app already has a no-workspace path
  that prompts the user to create one, so restricting deletion of the final workspace is
  treated as an unintended limitation rather than a deliberate safeguard.
- **The backend contract is authoritative and frozen.** All reconciliation happens on the
  web app side. The backend's paths, field names, success outcomes, and failure
  classifications are taken as correct as currently implemented.
- **The reported symptom has a single dominant cause.** Confirming deletion triggers a
  native form submission that reloads the page before the deletion request completes.
  Other affected paths already suppress that submission, which is why deletion alone
  exhibits the reload.
- **Deletion cascades on the backend.** Removing a workspace removes its documents,
  conversations, and derived search data; the app does not need to delete children
  individually.
- **Existing polling for document state is adequate.** Its interval and timeout are
  retained; only the states shown to the user are clarified.
- **Optimistic list updates remain acceptable** provided they are reverted on failure and
  an error is shown, consistent with the simplicity constraint.
- **The web app and backend run on origins already permitted by the backend's configured
  web origin.** Cross-origin misconfiguration is an environment concern, not part of this
  feature, though the resulting failure must still produce a user-visible message.
- **Single-user local operation.** No concurrent edits from other users are considered;
  "already deleted" cases arise from the user's own duplicate actions or direct backend
  changes.
