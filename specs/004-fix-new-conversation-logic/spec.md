# Feature Specification: Fix New Conversation Logic

**Feature Branch**: `004-fix-new-conversation-logic`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: "004-fix-new-conversation-logic (currently opening a new convo from the buttons and pressing enter doesn't direct us to the empty chat box and we're stuck seeing the 3 button page until the user clicks it himself"

## Clarifications

### Session 2026-08-01

- Q: After a conversation is selected (including a brand-new empty one), should the three suggestion prompts still appear in the main area? → A: No — suggestion prompts only when no conversation is selected (pre-start); empty selected conversation is compose-ready without those three prompts
- Q: Should the message input also receive keyboard focus when the user selects an existing conversation that has no messages yet (not only right after creating one)? → A: Focus compose only immediately after successfully creating a conversation
- Q: If the user creates a new conversation while the Documents navigation view is active, should the navigation switch to the Chat view? → A: Yes — switch navigation to Chat and select the new conversation

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Land in ready-to-type chat after starting a conversation (Priority: P1)

A user in a workspace starts a new conversation from any “start / new conversation”
control (main empty-state button, sidebar empty-state action, or footer “New
Conversation”). They confirm the name (including by pressing Enter in the name
dialog). Immediately afterward, the main area shows a ready-to-compose chat for
that conversation—not the pre-start page with the three suggestion buttons—and
the message input is focused so they can type without an extra click.

**Why this priority**: Starting a chat is a core path; being stuck on the
pre-start “three button” page breaks the flow and feels broken.

**Independent Test**: In a workspace with no open conversation, start a new
conversation via each entry point, confirm with Enter; verify the compose field
is active and the three-suggestion pre-start page is gone without clicking the
main area again.

**Acceptance Scenarios**:

1. **Given** a workspace is selected and no conversation is active, **When** the
   user activates “Start conversation” (or equivalent) in the main area, names
   the conversation, and confirms (including via Enter), **Then** the main area
   shows the chat compose surface for that new conversation and keyboard focus
   is in the message input.
2. **Given** a workspace is selected, **When** the user activates “New
   Conversation” from navigation/footer, names it, and confirms with Enter,
   **Then** the same ready-to-compose outcome occurs (selected conversation,
   compose focused, no pre-start three-button page).
3. **Given** the user just created a conversation with no messages yet,
   **When** they view the main area, **Then** they are not shown the
   “start conversation” call-to-action page that appears when no conversation
   has been chosen yet.
4. **Given** the Documents navigation view is active, **When** the user creates
   a new conversation and confirms, **Then** navigation switches to Chat with
   the new conversation selected and compose focused.

---

### User Story 2 - Distinguish “no conversation chosen” from “empty new conversation” (Priority: P2)

A user who has not selected or created a conversation still sees clear guidance
to start one (including optional suggestion prompts). Once a conversation exists
and is selected—even with zero messages—that guidance is replaced by the empty
thread + compose experience.

**Why this priority**: Prevents regressing the helpful first-run empty state
while fixing the stuck post-create flow.

**Independent Test**: With a workspace selected and no conversation selected,
confirm pre-start guidance is visible; create/select a conversation with no
messages and confirm pre-start guidance is gone and compose is available.

**Acceptance Scenarios**:

1. **Given** a workspace is selected and no conversation is selected, **When**
   the user views the main area, **Then** they see guidance to start a
   conversation (pre-start experience may include suggestion prompts).
2. **Given** a conversation is selected and has no messages, **When** the user
   views the main area, **Then** the pre-start “start conversation” guidance and
   the three suggestion prompts are not shown, and the message input is
   available.

---

### Edge Cases

- Confirming the new-conversation dialog with an empty name must not create a
  conversation or change the main area (existing validation).
- Canceling the dialog must leave the user on the prior main-area state.
- Creating a conversation while Documents navigation view is active must switch
  navigation to Chat, select the new conversation, and land the user in
  ready-to-compose chat in the main area (document management remains nav-only;
  main surface stays chat).
- If conversation creation fails, the user stays on a recoverable error path and
  is not left in an ambiguous half-selected state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: After a user successfully creates a new conversation from any
  supported entry point, the system MUST select that conversation, switch
  navigation to the Chat view if it was on Documents, and present the main chat
  compose surface without requiring an additional click on the main area.
- **FR-002**: After successful creation (including confirm via Enter in the name
  dialog), keyboard focus MUST move to the message compose field so the user can
  type immediately. Selecting an existing empty conversation from the list MUST
  show the empty-thread compose surface but MUST NOT require auto-focus (user
  may click the input).
- **FR-003**: The pre-start main experience (start-conversation call-to-action
  and the three suggestion prompts) MUST appear only when a workspace is
  selected and no conversation is selected. When any conversation is selected
  (including one with zero messages), those three suggestion prompts MUST NOT
  appear.
- **FR-004**: Selecting an existing conversation with no messages MUST use the
  same empty-thread + compose visual experience as a newly created conversation
  (not the pre-start CTA page), without mandatory auto-focus (see FR-002).
- **FR-005**: Failed conversation creation MUST leave the user able to dismiss
  or retry without appearing to be inside a selected empty conversation.
- **FR-006**: Existing conversation list selection and message send behavior for
  conversations that already have messages MUST remain unchanged.

### Key Entities

- **Workspace**: Container in which conversations are created.
- **Conversation**: Chat thread; may have zero messages when newly created.
- **Pre-start chat experience**: Guidance shown when a workspace is active but
  no conversation is selected.
- **Empty-thread chat experience**: Compose-ready main area for a selected
  conversation that has no messages yet.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of scripted trials of “New/Start conversation → confirm
  (including Enter)”, the message compose field is focused within 1 second and
  the pre-start three-suggestion page is not visible. Selecting an existing
  empty conversation from the list need not auto-focus compose.
- **SC-002**: Users can type and send the first message after creating a
  conversation without an extra click on the main pane in 100% of verification
  runs.
- **SC-003**: With no conversation selected, the pre-start guidance remains
  available in 100% of checks (no regression of the “choose/start conversation”
  empty state).
- **SC-004**: Canceling the create dialog never changes the selected
  conversation in 100% of trials.

## Non-Functional Constraints *(mandatory for Atlas)*

- **Local-first**: Feature MUST work with a local stack and Ollama (or compatible
  local endpoint) without requiring a cloud model provider.
- **Privacy**: Feature MUST NOT send user content to third parties unless the
  user explicitly opts in via configuration.
- **Performance**: Transition from confirmed create to focused compose MUST feel
  immediate (target under 100ms for pure UI state update after create succeeds).
- **UX**: Loading, error, and empty states remain clear for create failure vs
  empty thread vs no conversation selected.
- **Configurability**: No new required settings; reuse existing conversation
  create flow.
- **Simplicity**: Fix conversation selection / empty-state branching and focus
  only—no new product areas or redesign.
- **TDD**: Acceptance scenarios above MUST be automatable as tests before
  implementation.

## Assumptions

- “Buttons” means the main “Start conversation” action, sidebar empty-state
  start action, and footer “New Conversation” control (same create dialog).
- “3 button page” means the pre-start main experience with three suggestion
  prompts shown before a conversation is selected.
- “Empty chat box” means the message compose field in the main chat surface for
  the selected conversation.
- Creating a conversation still uses the existing name dialog; this feature does
  not remove naming.
- Suggestion prompts appear only on the pre-start experience (no conversation
  selected); they are not shown on a selected empty conversation.
