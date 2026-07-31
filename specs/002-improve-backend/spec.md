# Feature Specification: Improve Backend Reliability

**Feature Branch**: `002-improve-backend`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: "002-improve-backend"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trustworthy document ingestion (Priority: P1)

A user adds a source (PDF, Markdown, or plain text) to a workspace. The system
reliably extracts, chunks, and indexes that source for later grounded answers.
The user can tell whether ingestion is in progress, completed, or failed, and
can retry or replace a failed upload without leaving the workspace in an
ambiguous half-indexed state.

**Why this priority**: Grounded chat is worthless if sources never become
searchable. Ingestion correctness is the foundation of the product.

**Independent Test**: Upload valid and invalid sources into an empty workspace;
verify terminal job status (success or failure with a clear reason), successful
sources become retrievable for chat, and failed sources do not silently appear
as fully indexed knowledge.

**Acceptance Scenarios**:

1. **Given** a workspace and a supported document under the configured size
   limit, **When** the user uploads it, **Then** the system accepts the file,
   reports progress or pending state, and eventually marks ingestion complete
   with the document available for retrieval.
2. **Given** a workspace and an unsupported, empty, or oversize file,
   **When** the user uploads it, **Then** the system rejects or fails the job
   with a user-visible reason and does not treat the file as successfully
   indexed knowledge.
3. **Given** an ingestion that fails mid-process (e.g. model or storage
   unavailable), **When** the user inspects the document or job, **Then** they
   see a failed state (not stuck “processing” indefinitely) and can retry or
   remove the document.
4. **Given** a document that completed ingestion, **When** the user asks a
   question whose answer is in that document, **Then** retrieval can return
   content from that document (subject to relevance).

---

### User Story 2 - Correct grounded answers with honest failure modes (Priority: P1)

A user chats in a workspace that has indexed sources. Answers that claim to be
grounded are based on real retrieved passages from that workspace only, with
citations the user can trust. When the configured local AI backend is
unavailable, or when no relevant sources exist, the system says so clearly
instead of inventing confidence or silent fake grounding.

**Why this priority**: Silent fallbacks and fake similarity scores destroy trust
in a citation-based knowledge product and violate Atlas’s grounded-answer
promise.

**Independent Test**: With known source content, ask in-scope and out-of-scope
questions; verify citations map to real workspace passages. Stop or misconfigure
the local AI backend and verify the user receives a clear failure (not a
synthetic “successful” answer presented as grounded).

**Acceptance Scenarios**:

1. **Given** a workspace with at least one successfully indexed source,
   **When** the user asks a question answerable from that source, **Then** the
   reply is grounded in retrieved passages from that workspace and includes
   citations that point to those passages.
2. **Given** a workspace with sources, **When** the user asks a question with no
   relevant material in the workspace, **Then** the system does not fabricate
   citations or imply high-confidence grounding from unrelated chunks.
3. **Given** the user has configured a local AI backend as the active provider,
   **When** that backend is unreachable during chat, **Then** the user receives
   a clear error (or non-success state) and the conversation is not silently
   filled with offline placeholder text presented as a normal grounded answer.
4. **Given** two workspaces with different documents, **When** the user chats
   in workspace A, **Then** retrieval never returns passages that belong only
   to workspace B.

---

### User Story 3 - Predictable errors the app can surface (Priority: P2)

When the Atlas app performs workspace, document, and chat operations, it
receives consistent, actionable error outcomes for validation failures, missing
resources, and dependency outages—so the UI can show loading, error, and empty
states without guessing.

**Why this priority**: Backend improvements only help users if failures are
legible at the boundary the app already depends on.

**Independent Test**: Exercise missing workspace/conversation/document
references, invalid uploads, and provider-unavailable chat; verify each yields
a non-success outcome with a stable, human-meaningful message suitable for UI
display (no stack traces or internal host details required for the user).

**Acceptance Scenarios**:

1. **Given** a request for a workspace, document, or conversation that does not
   exist (or is outside the given workspace), **When** the app requests that
   resource, **Then** the outcome is a clear not-found / not-allowed result—not
   an empty success or an opaque internal failure.
2. **Given** an invalid upload or chat request, **When** the app submits it,
   **Then** the outcome indicates a user-correctable error with a reason the UI
   can show.
3. **Given** a dependency outage (storage or configured AI backend),
   **When** the affected operation runs, **Then** the app receives a clear
   failure outcome rather than a misleading success result.

---

### User Story 4 - Configurable local AI path stays first-class (Priority: P2)

A user running Atlas entirely on their machine can configure the local AI
endpoint and model choices used for chat and embeddings. Core ingest and chat
flows work with the local path without requiring a cloud provider. Optional
remote providers remain opt-in only.

**Why this priority**: Constitution requires local-first / Ollama-native as the
default product path; backend “improvements” must not make cloud mandatory.

**Independent Test**: Configure only a local AI endpoint (no cloud keys);
complete upload → index → grounded chat for a sample document end-to-end.

**Acceptance Scenarios**:

1. **Given** a local-only configuration (local AI endpoint, no cloud keys),
   **When** the user uploads a document and chats, **Then** ingest and grounded
   chat complete successfully when the local backend and models are available.
2. **Given** user-configurable endpoint / model settings for chat and
   embeddings, **When** the user changes those settings to valid local values
   and restarts or reloads configuration as documented, **Then** subsequent
   ingest and chat use the new settings.
3. **Given** cloud providers are not configured, **When** the user uses core
   flows, **Then** the system does not require or silently depend on a remote
   model provider.

---

### Edge Cases

- Upload of an empty file, truncated/corrupt PDF, or renamed unsupported type.
- Concurrent uploads into the same workspace.
- Chat while ingestion for a relevant document is still running.
- Chat in a workspace with zero documents or zero successfully indexed
  documents.
- Embedding or chat model name misconfigured (model missing on the local
  backend).
- Vector / search subsystem unavailable while chat is requested.
- Very large valid document near the configured size limit.
- Deleting a document or workspace while an ingestion job is in flight.
- Provider returns an empty or malformed response.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST complete document ingestion for supported file types
  (at minimum PDF, Markdown, and plain text) into a retrievable form scoped to
  the target workspace.
- **FR-002**: System MUST expose a clear terminal status for each ingestion
  attempt: success, failure (with reason), or an actively progressing state that
  does not remain stuck without a timeout/failure path.
- **FR-003**: System MUST reject or fail uploads that exceed the configured size
  limit, are empty, or are unsupported, without marking them as successfully
  indexed.
- **FR-004**: System MUST keep a single consistent embedding size and identity
  model for stored vectors and queries so indexed content remains searchable
  after configuration that the product documents as supported.
- **FR-005**: System MUST retrieve candidate passages only from the active
  workspace when answering grounded questions.
- **FR-006**: System MUST attach citations to grounded answers that correspond
  to real retrieved passages (document identity and location/snippet sufficient
  for the user to verify).
- **FR-007**: System MUST NOT present synthetic offline placeholder answers or
  fabricated similarity as successful grounded results when the configured AI
  backend was required and failed, or when retrieval did not produce usable
  evidence.
- **FR-008**: System MUST return consistent, app-actionable failure outcomes for
  missing resources, validation errors, and dependency outages on workspace,
  document, and chat operations.
- **FR-009**: System MUST support a fully local configuration path (local AI
  endpoint) for core ingest and grounded chat without requiring cloud
  credentials.
- **FR-010**: System MUST allow users to configure local AI endpoint and model
  settings used for chat and embeddings via documented configuration.
- **FR-011**: System MUST preserve workspace isolation for documents,
  conversations, retrieval, and citations (no cross-workspace leakage).
- **FR-012**: System MUST allow users to remove a failed or unwanted document
  and retry ingestion of a replacement without requiring a full workspace reset.
- **FR-013**: Out of scope for this feature: multi-user authentication/accounts,
  sharing between users, cloud-only required features, and UI visual redesign
  (covered separately by the UI improvement feature).

### Key Entities

- **Workspace**: Isolation boundary for documents, conversations, and
  retrieval.
- **Document (source)**: User-uploaded file with metadata, storage reference,
  and ingestion outcome.
- **Ingestion job**: Lifecycle record for indexing a document (progress /
  success / failure + reason).
- **Chunk / passage**: Indexed unit of document text used for retrieval and
  citation.
- **Conversation / message**: User–assistant exchange within a workspace;
  assistant messages may include citations.
- **Citation**: Provenance link from an answer to a retrieved passage /
  document.
- **AI provider configuration**: User-selected local (default) or optional
  remote endpoint/model settings for chat and embeddings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a local-only setup with a healthy local AI backend, a supported
  sample document (≤ 5 pages or equivalent) reaches “ingestion successful” and
  is usable in grounded chat within 2 minutes on typical developer hardware.
- **SC-002**: 100% of grounded answers that include citations map each citation
  to a real passage from the active workspace (verified in acceptance tests with
  fixtures).
- **SC-003**: When the configured AI backend is stopped, chat requests fail in a
  user-visible way within 15 seconds and never return a success payload that
  looks like a normal grounded answer generated from placeholder text.
- **SC-004**: Cross-workspace retrieval tests show 0 passages leaked from other
  workspaces across a fixed fixture suite.
- **SC-005**: Invalid uploads (empty, oversize, unsupported) produce a clear
  failure outcome in 100% of cases in the acceptance suite—never a silent
  “success” indexed state.
- **SC-006**: Core journeys (upload → index → grounded chat; missing resource;
  provider down) are covered by automated tests that fail if regressions are
  introduced, before implementation merges.

## Non-Functional Constraints *(mandatory for Atlas)*

- **Local-first**: Feature MUST work with a local stack and Ollama (or
  compatible local endpoint) without requiring a cloud model provider.
- **Privacy**: Feature MUST NOT send user content to third parties unless the
  user explicitly opts in via configuration. Logs MUST avoid document body and
  secret material.
- **Performance**: Sample-document ingestion success within 2 minutes locally
  (SC-001); chat must surface provider-down failure within 15 seconds (SC-003).
  Streaming time-to-first-token targets remain best-effort on local hardware and
  MAY be refined in planning.
- **UX**: Primary paths MUST expose loading/progress (ingestion, chat pending),
  error (failed ingest, provider down, validation), and empty (no documents /
  no citations) states via outcomes the app can render. UI chrome changes are
  out of scope here.
- **Configurability**: Local AI base URL, chat model, and embedding model MUST
  remain user-configurable; size limits MUST be documented and applied
  consistently at the upload boundary.
- **Simplicity**: Prefer fixing correctness of the existing ingest → retrieve →
  grounded chat pipeline over adding new services or product surfaces. New
  abstraction layers only where required to remove incorrect shared behavior
  (e.g. inconsistent providers or dimensions).
- **TDD**: Acceptance scenarios above MUST be automatable as tests before
  implementation.

## Assumptions

- “Improve backend” means hardening and correcting the existing
  workspace → ingest → retrieve → grounded chat pipeline, not shipping new
  product domains (auth, sync, sharing, new content types beyond current
  supported uploads).
- Multi-user authentication remains out of scope; Atlas continues to assume a
  trusted local single-user (or trusted local network) deployment for this
  feature.
- Optional remote model providers may remain available as opt-in configuration
  but are not required for success criteria; local path is the acceptance
  baseline.
- The companion UI improvement feature may consume clearer API error/status
  shapes but is not responsible for backend correctness.
- Supported upload types for this feature remain PDF, Markdown, and plain text
  unless later clarified.
- “Stuck processing” is defined as no terminal state within a documented
  timeout or recovery pass after dependency failure; exact timeout values may be
  set in planning as long as SC-001/SC-003 remain met.
- README and operator-facing docs may be updated as part of making
  configuration and failure modes discoverable; that is in scope as
  documentation of behavior, not as a separate product feature.
