# Feature Specification: Improve Backend Reliability

**Feature Branch**: `002-improve-backend`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: "002-improve-backend"

## Clarifications

### Session 2026-08-01

- Q: When a user changes the embedding model (or embedding vector size) after documents were already indexed, what should the system do for search and grounded chat? → A: Fail grounded chat/retrieval clearly until user re-indexes (or restores prior model); no silent mixed-dimension search
- Q: When the user chats in a workspace while one or more documents are still ingesting, which passages may grounded answers use? → A: Ground only on successfully indexed documents; exclude in-progress and failed
- Q: If the user deletes a document (or its workspace) while that document’s ingestion job is still running, what should happen to the in-flight job and any partial index data? → A: Cancel/abandon job; delete document + all partial index artifacts; no residue in retrieval
- Q: What default maximum upload size should FR-003 enforce (unless the user overrides it in documented configuration)? → A: 80 MB default per file; expose clear progress/status outcomes so the UI can keep the user informed
- Q: What is the minimum identity a citation must include so a user can verify a grounded answer against the source? → A: Document id/name + verbatim snippet/passage text (page/offset optional when available)

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
   limit (default 80 MB), **When** the user uploads it, **Then** the system
   accepts the file, reports progress or pending state suitable for UI
   indicators, and eventually marks ingestion complete with the document
   available for retrieval.
2. **Given** a workspace and an unsupported, empty, or oversize file (above
   the configured limit, default 80 MB), **When** the user uploads it,
   **Then** the system rejects or fails the job with a user-visible reason and
   does not treat the file as successfully indexed knowledge.
3. **Given** an ingestion that fails mid-process (e.g. model or storage
   unavailable), **When** the user inspects the document or job, **Then** they
   see a failed state (not stuck “processing” indefinitely) and can retry or
   remove the document.
4. **Given** a document that completed ingestion, **When** the user asks a
   question whose answer is in that document, **Then** retrieval can return
   content from that document (subject to relevance).
5. **Given** a document whose ingestion is still in progress, **When** the user
   deletes that document (or its workspace), **Then** the job is cancelled or
   abandoned, partial index artifacts are removed, and later retrieval does not
   return passages from that document.

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
5. **Given** a workspace with at least one successfully indexed source and
   another document still ingesting, **When** the user asks a grounded
   question, **Then** retrieval and citations use only successfully indexed
   documents—not in-progress or failed ones.

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
- Chat while ingestion for a relevant document is still running: grounded
  answers use only successfully indexed documents; in-progress and failed
  documents contribute no passages or citations.
- Chat in a workspace with zero documents or zero successfully indexed
  documents.
- Embedding or chat model name misconfigured (model missing on the local
  backend).
- Vector / search subsystem unavailable while chat is requested.
- Very large valid document near the 80 MB default (or configured) size limit:
  accepted if within limit; progress/status outcomes remain visible until
  terminal success or failure.
- Deleting a document or workspace while an ingestion job is in flight: the
  job is cancelled or abandoned, the document and any partial chunks/vectors
  are removed, and retrieval MUST NOT return residue for that document.
- Provider returns an empty or malformed response.
- Embedding model or vector size changed after documents were indexed: grounded
  chat/retrieval fails clearly until re-index or restore of a compatible model;
  no mixed-dimension or cross-model vector search.

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
  indexed. The default maximum upload size MUST be 80 MB per file unless
  overridden via documented configuration. Oversized rejection and in-flight
  ingestion MUST expose clear, app-actionable status/progress (and failure
  reason) outcomes so the UI can keep the user informed—visual chrome for those
  indicators remains with the UI feature, but the outcomes MUST be sufficient.
- **FR-004**: System MUST keep a single consistent embedding size and identity
  model for stored vectors and queries. When the configured embedding model or
  vector size no longer matches vectors already stored for a workspace, the
  system MUST fail grounded chat/retrieval with a clear, user-visible reason
  until the user re-indexes affected documents or restores a compatible prior
  embedding configuration. The system MUST NOT silently search or mix vectors
  built under incompatible embedding identities.
- **FR-005**: System MUST retrieve candidate passages only from the active
  workspace when answering grounded questions, and only from documents whose
  ingestion has reached success. In-progress and failed documents MUST NOT
  contribute passages or citations.
- **FR-006**: System MUST attach citations to grounded answers that correspond
  to real retrieved passages. Each citation MUST include at minimum a document
  id or stable document name and a verbatim snippet (or passage text) from the
  retrieved chunk so the user can verify the answer. Page number or
  character-offset location MAY be included when available but is not required
  for all formats.
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
  Deleting a document (or its workspace) while ingestion is in flight MUST
  cancel or abandon the job, remove the document and any partial index
  artifacts, and leave no retrievable residue for that document.
- **FR-013**: Out of scope for this feature: multi-user authentication/accounts,
  sharing between users, cloud-only required features, and UI visual redesign
  (covered separately by the UI improvement feature).

### Key Entities

- **Workspace**: Isolation boundary for documents, conversations, and
  retrieval.
- **Document (source)**: User-uploaded file with metadata, storage reference,
  and ingestion outcome. Deletion while ingesting removes the source and any
  partial index artifacts for that document.
- **Ingestion job**: Lifecycle record for indexing a document (progress /
  success / failure + reason). May be cancelled or abandoned when the document
  or workspace is deleted mid-flight.
- **Chunk / passage**: Indexed unit of document text used for retrieval and
  citation.
- **Conversation / message**: User–assistant exchange within a workspace;
  assistant messages may include citations.
- **Citation**: Provenance link from an answer to a retrieved passage /
  document; minimum fields are document id or stable name plus verbatim
  snippet/passage text (page/offset optional when available).
- **AI provider configuration**: User-selected local (default) or optional
  remote endpoint/model settings for chat and embeddings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a local-only setup with a healthy local AI backend, a supported
  sample document (≤ 5 pages or equivalent) reaches “ingestion successful” and
  is usable in grounded chat within 2 minutes on typical developer hardware.
- **SC-002**: 100% of grounded answers that include citations map each citation
  to a real passage from the active workspace, and each citation includes
  document id/name plus verbatim snippet/passage text (verified in acceptance
  tests with fixtures).
- **SC-003**: When the configured AI backend is stopped, chat requests fail in a
  user-visible way within 15 seconds and never return a success payload that
  looks like a normal grounded answer generated from placeholder text.
- **SC-004**: Cross-workspace retrieval tests show 0 passages leaked from other
  workspaces across a fixed fixture suite.
- **SC-005**: Invalid uploads (empty, oversize including above the 80 MB
  default unless config raised, unsupported) produce a clear failure outcome
  in 100% of cases in the acceptance suite—never a silent “success” indexed
  state.
- **SC-007**: After an embedding model or vector-size change that mismatches
  stored vectors, grounded chat/retrieval returns a clear non-success outcome
  (not silent empty grounding) until re-index or restore of a compatible
  configuration, verified in the acceptance suite.
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
  error (failed ingest, provider down, validation including oversize), and empty
  (no documents / no citations) states via outcomes the app can render—especially
  for large uploads up to the 80 MB default. UI chrome changes are out of scope
  here but MUST have sufficient backend outcomes for indicators.
- **Configurability**: Local AI base URL, chat model, and embedding model MUST
  remain user-configurable; size limits MUST default to 80 MB per file,
  be documented, overridable, and applied consistently at the upload boundary.
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
- Embedding-model changes do not auto-reindex; the user must re-index (or
  restore a compatible model) before grounded retrieval works again.
- Chat during concurrent ingestion is allowed; grounding is restricted to
  documents that have already completed ingestion successfully.
- In-flight ingestion cancelled by delete MUST clean up partial artifacts so
  they cannot appear in later grounded retrieval.
- Default upload size limit is 80 MB per file; large accepted files rely on
  clear ingestion progress/status outcomes (for UI indicators), not silent
  long-running jobs.
- Citation minimum for verification is document id/name plus verbatim
  snippet/passage text; richer location metadata is optional.
- README and operator-facing docs may be updated as part of making
  configuration and failure modes discoverable; that is in scope as
  documentation of behavior, not as a separate product feature.
