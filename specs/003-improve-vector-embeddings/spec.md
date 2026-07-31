# Feature Specification: Improve Vector Embeddings

**Feature Branch**: `003-improve-vector-embeddings`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: "003-improve vector embeddings"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Indexed sources are actually findable (Priority: P1)

A user uploads documents into a workspace and later asks questions about that
material. Search finds the right passages using real semantic similarity from
the configured embedding setup—not stand-in or hash-based placeholders that
only pretend the knowledge is searchable.

**Why this priority**: Atlas’s value is grounded retrieval. If indexes are not
semantically meaningful, citations and answers cannot be trusted even when the
rest of the pipeline “succeeds.”

**Independent Test**: With a healthy local embedding backend and a fixture
document containing distinctive facts, ask questions that should hit those
facts; verify the top retrieved passages are the relevant ones (not random
workspace text). Repeat with a question that should miss; verify the system
does not rank unrelated passages as strongly relevant.

**Acceptance Scenarios**:

1. **Given** a workspace with a successfully indexed document and a healthy
   embedding configuration, **When** the user asks a question whose answer is
   clearly present in that document, **Then** retrieval returns the relevant
   passage(s) among the top results used for grounding.
2. **Given** the same workspace, **When** the user asks a question with no
   related material in the workspace, **Then** retrieval does not treat
   unrelated passages as high-confidence matches for grounding.
3. **Given** indexing completed under the active embedding configuration,
   **When** the user later asks questions without changing that configuration,
   **Then** previously indexed content remains searchable (same embedding
   identity for index and query).

---

### User Story 2 - Honest embedding failures (Priority: P1)

When the embedding backend is missing, unreachable, or misconfigured, the user
learns that knowledge is not searchable. The system does not quietly substitute
non-semantic stand-in vectors or invent similarity scores that make weak
retrieval look confident.

**Why this priority**: Silent placeholder embeddings are worse than a clear
failure—they create false confidence in grounded answers.

**Independent Test**: Stop or misconfigure the embedding backend; attempt
ingest and/or chat retrieval; verify clear failure or “not searchable” outcome
with no fabricated similarity presented as success.

**Acceptance Scenarios**:

1. **Given** the user expects real embeddings (local embedding backend
   configured as the active path), **When** that backend is unavailable during
   indexing, **Then** ingestion does not complete as a successful searchable
   index built from stand-in vectors.
2. **Given** indexed content exists but query-time embedding fails, **When** the
   user asks a grounded question, **Then** the outcome is a clear failure or
   non-grounded-success state—not unrelated chunks with fabricated similarity.
3. **Given** embedding model or size configuration is incompatible with stored
   indexes, **When** the user tries to search, **Then** the system surfaces a
   clear incompatibility (and points toward rebuild)—not silent wrong results.

---

### User Story 3 - Configure embeddings independently and rebuild (Priority: P2)

A user can choose and document the embedding model (and related settings)
separately from the chat model. After changing embedding settings, they can
rebuild indexes for existing workspace documents so older uploads become
findable again under the new identity.

**Why this priority**: Local setups differ by machine and model; without
rebuild, config changes permanently orphan prior knowledge.

**Independent Test**: Index a document under embedding config A; switch to
compatible config B; run rebuild for the workspace; verify questions find the
document under B. Without rebuild, verify the system does not silently claim
full searchability under the mismatched identity.

**Acceptance Scenarios**:

1. **Given** documented embedding configuration options (at least local
   endpoint and embedding model), **When** the user sets valid local values,
   **Then** subsequent indexing and queries use that embedding identity.
2. **Given** documents were indexed under a previous embedding identity,
   **When** the user changes embedding model/settings to a new supported
   identity, **Then** they can trigger a workspace rebuild that re-indexes
   existing documents under the new identity.
3. **Given** a rebuild is running or has failed for some documents, **When** the
   user inspects workspace/document indexing state, **Then** they can tell which
   sources are searchable under the current identity versus still pending or
   failed.
4. **Given** chat model settings change but embedding settings do not,
   **When** the user continues to ask questions, **Then** existing indexes
   remain valid (embedding identity unchanged).

---

### User Story 4 - Clear default: real local embeddings for knowledge search (Priority: P2)

A user following the default local-first setup understands that searchable
knowledge requires a real local embedding model. The product default path does
not present stand-in vectors as equivalent to semantic search.

**Why this priority**: Constitution requires local-first value; “works out of
the box” must not mean “indexes that cannot find anything meaningfully.”

**Independent Test**: Follow documented default local setup with the embedding
model available; complete upload → index → findable question. With embedding
model absent, verify the user is guided to install/configure it rather than
getting a false “fully indexed” experience.

**Acceptance Scenarios**:

1. **Given** the documented default local setup and a healthy embedding model,
   **When** the user indexes a sample document, **Then** that document is
   findable via semantic questions without enabling any cloud provider.
2. **Given** the embedding model required by the default path is not available,
   **When** the user attempts to create a searchable index, **Then** they receive
   clear guidance that embeddings are required—not a silent stand-in success.
3. **Given** optional remote embedding providers are not configured, **When**
   the user uses core knowledge search, **Then** cloud embeddings are not
   required.

---

### Edge Cases

- Embedding backend returns vectors of the wrong size for the active identity.
- User switches from model A to model B mid-workspace with partial rebuild.
- Rebuild while new uploads arrive concurrently.
- Document deleted during rebuild.
- Very short documents (few tokens) and very long documents (many chunks).
- Query embedding succeeds but no chunks exist yet (empty or still-indexing
  workspace).
- Mixed documents: some rebuilt under new identity, some still on old identity.
- Chat available but embeddings unavailable (or the reverse).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST index document passages with real embeddings from the
  active embedding configuration so that later questions can retrieve by
  semantic similarity within the workspace.
- **FR-002**: System MUST use the same embedding identity (model and vector
  size contract) for indexing and for query-time embedding under a given
  configuration.
- **FR-003**: System MUST NOT complete a “successfully searchable” index using
  non-semantic stand-in or hash-based placeholder vectors when the user has
  configured (or the product default requires) a real embedding backend.
- **FR-004**: System MUST NOT invent or substitute fixed/fake similarity scores
  for unrelated passages when vector search fails or embeddings are
  unavailable.
- **FR-005**: System MUST fail clearly (ingest and/or retrieval) when the
  embedding backend is unreachable, returns incompatible vectors, or is
  misconfigured for the active identity.
- **FR-006**: Users MUST be able to configure embedding settings (local
  endpoint and embedding model at minimum) independently from chat model
  settings via documented configuration.
- **FR-007**: Users MUST be able to rebuild indexes for existing documents in a
  workspace after an embedding identity change so prior uploads become
  searchable under the new identity.
- **FR-008**: System MUST expose enough indexing state for the app to show which
  documents are searchable under the current embedding identity versus
  pending, stale (wrong identity), or failed.
- **FR-009**: Default local-first path MUST document and support real local
  embeddings for knowledge search without requiring cloud credentials.
- **FR-010**: Changing chat model alone MUST NOT invalidate embedding indexes.
- **FR-011**: Retrieval for grounded answers MUST continue to respect workspace
  isolation (no cross-workspace passages).
- **FR-012**: Out of scope for this feature: UI visual redesign; multi-user
  auth/sharing; training custom embedding models; multi-modal (image) embeddings;
  cloud embeddings as a required dependency; general ingest/upload validation and
  chat error envelopes already owned by the backend reliability feature except
  where embedding-specific honesty and rebuild apply.

### Key Entities

- **Embedding identity**: The active combination of embedding model and vector
  size contract used for both indexing and queries.
- **Indexed passage**: A chunk of document text stored with an embedding under a
  specific embedding identity, scoped to a workspace.
- **Index status**: Whether a document’s passages are searchable under the
  current identity (ready, pending, stale, failed).
- **Rebuild request**: User-triggered re-indexing of existing workspace
  documents under the current embedding identity.
- **Embedding configuration**: User-documented settings for embedding endpoint
  and model (distinct from chat model configuration).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a documented local setup with a healthy embedding model, for a
  fixture document containing at least three distinctive facts, questions aimed
  at each fact retrieve a relevant passage in the top results used for grounding
  in 100% of those fixture questions in the acceptance suite.
- **SC-002**: With the embedding backend stopped, 100% of indexing attempts that
  require embeddings end in a clear non-success searchable state (no stand-in
  “success” index) in the acceptance suite.
- **SC-003**: With the embedding backend stopped at query time, 100% of grounded
  retrieval attempts fail honestly—no fabricated similarity on unrelated
  passages—in the acceptance suite.
- **SC-004**: After changing embedding identity and completing workspace
  rebuild, previously uploaded fixture documents are again findable under the
  new identity for the same fact-based questions (SC-001 style) without
  re-uploading files.
- **SC-005**: Chat-model-only configuration changes leave existing indexes
  searchable without rebuild in 100% of the acceptance cases that cover this
  scenario.
- **SC-006**: Acceptance tests for the above journeys exist and fail if
  stand-in vectors, fake similarity, or missing rebuild behavior regress.

## Non-Functional Constraints *(mandatory for Atlas)*

- **Local-first**: Feature MUST work with a local stack and Ollama (or
  compatible local endpoint) for embeddings without requiring a cloud embedding
  provider.
- **Privacy**: Feature MUST NOT send document text to third parties for
  embedding unless the user explicitly opts in to a remote embedding provider.
  Logs MUST avoid passage text and secrets.
- **Performance**: For a short sample document (≤ 5 pages or equivalent),
  indexing under a healthy local embedding model SHOULD complete within the same
  2-minute local target used by the backend reliability feature; rebuild of a
  small workspace (≤ 5 short documents) SHOULD complete within 5 minutes on
  typical developer hardware.
- **UX**: Indexing, stale-index, rebuild progress/failure, and embedding-down
  states MUST be expressible as outcomes the app can show (loading, error,
  empty/stale). Visual redesign of the UI is out of scope.
- **Configurability**: Embedding endpoint and embedding model MUST be
  user-configurable and documented separately from chat model settings.
- **Simplicity**: Prefer one clear embedding identity plus rebuild over multiple
  parallel index spaces or complex hybrid rankers in this feature. Do not add
  services beyond what correctness requires.
- **TDD**: Acceptance scenarios above MUST be automatable as tests before
  implementation.

## Assumptions

- This feature deepens embedding correctness, search quality, and rebuild; the
  backend reliability feature remains the owner of general upload validation,
  ingestion job UX for non-embedding failures, chat streaming, and broad error
  envelopes. Where both mention embedding consistency, this feature is the
  detailed source of truth for embedding identity and rebuild.
- “Real embeddings” means vectors produced by a configured embedding model
  suitable for semantic similarity—not deterministic hash/stand-in vectors.
- Workspace-level rebuild is sufficient for v1; per-document rebuild MAY be
  offered but is not required if workspace rebuild covers all documents.
- Optional remote embedding providers may exist as opt-in later or alongside,
  but success criteria are validated on the local path.
- Supported document types remain those of the current product (PDF, Markdown,
  plain text) unless changed elsewhere.
- Exact default embedding model name is chosen in planning to match a commonly
  available local model and a single size contract; the spec requires that the
  choice be documented and consistent, not a specific brand string in
  stakeholder language.
- Hybrid keyword + vector ranking is not required for v1 if honest vector
  retrieval meets SC-001–SC-003.
