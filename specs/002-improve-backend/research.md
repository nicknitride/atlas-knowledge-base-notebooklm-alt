# Research: Improve Backend Reliability

**Feature**: `002-improve-backend` | **Date**: 2026-08-01

## R1 — Fail-closed LLM and embedding providers

**Decision**: When a provider is configured (`ollama`, `openai`, `gemini`, or
local→Ollama), provider failures MUST propagate as errors. Remove (or hard-disable)
silent paths that call `generateFallback` / `streamFallback` /
`embedDeterministic` after a configured backend fails. Deterministic hash
embeddings and offline “Based on the workspace document sources…” synthesis MUST
NOT be returned as successful grounded chat when a real provider was required.

**Rationale**: Spec FR-007 / SC-003; current `DefaultLlmProvider` and
`DefaultEmbeddingProvider` log a warning then fabricate success, destroying
citation trust.

**Alternatives considered**:

- Keep fallback only for `ATLAS_PROVIDER_TYPE=local` without Ollama — rejected;
  constitution requires Ollama-native local path; “local” should mean local
  Ollama endpoint, not hash embeddings.
- Soft-degrade with a clearly labeled “offline mode” flag — deferred; not in
  this feature’s clarifications; would need explicit user opt-in.

## R2 — Map `local` provider type to Ollama-compatible endpoint

**Decision**: Treat `ATLAS_PROVIDER_TYPE=local` (default in `application.yml`) as
the Ollama-compatible path using `ATLAS_OLLAMA_URL` + chat/embedding model
settings. Do not use deterministic embeddings for default local operation.
Document that cloud types remain opt-in via `openai` / `gemini` + keys.

**Rationale**: FR-009/FR-010; Docker Compose already defaults
`ATLAS_PROVIDER_TYPE=local` and sets `ATLAS_OLLAMA_URL`.

**Alternatives considered**:

- Require renaming default to `ollama` only — acceptable alias, but keep
  `local` working for existing `.env` / compose.

## R3 — Upload size default 80 MB

**Decision**: Configurable max upload size default **80 MB**, applied at both
Spring multipart (`spring.servlet.multipart.max-file-size/max-request-size`) and
application validation (replace hard-coded `25 * 1024 * 1024` in
`DocumentController`). Env/config key e.g. `ATLAS_MAX_UPLOAD_BYTES` or
`atlas.max-upload-bytes`. Reject empty/unsupported/oversize with stable error
codes (see contracts).

**Rationale**: Clarification session; current code is 25 MB app-side and 20 MB
Spring multipart (inconsistent). Large files need status polling, not silent
jobs (FR-002/FR-003).

**Alternatives considered**:

- 25 MB keep — rejected by clarification.
- Multipart-only enforcement — rejected; need consistent app message + tests.

## R4 — Ground only on successfully ingested documents

**Decision**: `VectorSearchService` SQL MUST join documents and require
`ingestion_status = 'COMPLETE'` (and non-null embeddings). In-progress / failed
documents never contribute chunks. Chat may proceed if other COMPLETE docs
exist; empty retrieval → honest no-evidence behavior (no fake citations).

**Rationale**: Clarification A; FR-005. Today search only filters
`workspace_id` and `embedding IS NOT NULL`.

**Alternatives considered**:

- Block all chat while any job is PROCESSING — rejected (clarification chose A).

## R5 — Embedding identity mismatch → fail until re-index

**Decision**: Persist active embedding **model name + dimension** used when
vectors are written (workspace-level or per-document metadata—prefer workspace
`embedding_model` / `embedding_dimensions` updated on successful ingest, with
documents stamped at COMPLETE). On query, if configured model/dim ≠ stored
identity for the workspace (or for any COMPLETE doc being searched), fail
grounded chat/retrieval with a clear error (e.g. `EMBEDDING_CONFIG_MISMATCH`);
do not auto-reindex (clarification B). Truncating/padding vectors to a fixed
`DIMENSION` constant MUST stop; stored length must match DB `vector(N)`.

**Rationale**: FR-004 / SC-007. Code today hard-codes `DIMENSION = 1536` while
Flyway V3 uses `vector(768)` and Ollama `nomic-embed-text`—silent truncate in
`toFloatArray`.

**Alternatives considered**:

- Auto-reindex on config change — rejected (clarification).
- Dual-index — rejected (YAGNI).

## R6 — Delete during ingest

**Decision**: On document (or workspace) delete, rely on FK `ON DELETE CASCADE`
for `document_chunks` and `ingestion_jobs`, and make `executeJob` **abort
safely** if the document/job row disappears mid-run (no re-insert after delete).
Optionally set a cancel flag before delete; minimum is idempotent no-residue.
Do not leave orphan chunks (CASCADE already covers DB; ensure in-memory job
exits without recreating the document).

**Rationale**: Clarification A; FR-012. Current delete removes storage + document
row but does not explicitly cancel the async job.

**Alternatives considered**:

- Wait for job completion then delete — rejected (worse UX).
- Soft-delete with later GC — rejected (YAGNI for local single-user).

## R7 — Remove fake similarity / ILIKE success path for grounded chat

**Decision**: When vector search fails (extension error, dimension mismatch,
embed failure), return a hard error to chat—not an ILIKE query that fabricates
`similarity = 0.75`. ILIKE may remain only behind an explicit test profile if
needed; production local path fails closed.

**Rationale**: FR-007; `VectorSearchService` catch block currently invents
scores.

**Alternatives considered**:

- Keep ILIKE as degraded mode with `similarity` null and UI badge — deferred;
  not clarified; risks silent trust issues.

## R8 — Stuck PROCESSING timeout

**Decision**: Document and implement a **10-minute** wall-clock timeout for
ingestion `PROCESSING` (configurable). On timeout or startup recovery, mark
document/job `FAILED` with a clear reason (existing orphan recovery already
resets PROCESSING→PENDING on startup—extend so long-running jobs cannot stick
forever without terminal state). Aligns with FR-002; keeps SC-001 (2 min sample)
unaffected.

**Rationale**: Spec deferred exact timeout to planning.

**Alternatives considered**:

- 2-minute timeout — too aggressive for 80 MB PDFs on slow hardware.
- No timeout, only startup recovery — insufficient for long-lived process.

## R9 — App-actionable API errors

**Decision**: Introduce a small consistent JSON error body for API failures
(validation, not-found, provider down, embedding mismatch) with stable `code` +
user-safe `message` (+ optional `requestId` from existing correlation filter).
Ensure multipart/size rejections and chat SSE/error paths surface the same
codes. Adjust `server.error.include-message: never` behavior by returning
explicit `@ControllerAdvice` / `ResponseStatusException` bodies the app can
parse (see `contracts/api-errors.md`).

**Rationale**: FR-008; UI feature depends on outcomes without stack traces.

**Alternatives considered**:

- RFC 7807 only — acceptable if mapped; prefer simple `{ code, message,
requestId }` matching existing Atlas envelope lean style.
- Change frontend only — rejected; backend must emit the shape.

## R10 — Boundary with `003-improve-vector-embeddings`

**Decision**: This feature owns reliability of the pipeline (fail-closed,
ingest lifecycle, upload limits, COMPLETE-only retrieval, citation minimum
fields already largely present, error contracts). Feature 003 may further
improve embedding quality, model selection UX, and rebuild tooling. Avoid
duplicating large embedding redesigns in 002; do the minimum identity +
fail-closed work here.

**Rationale**: Two specs; constitution VII.

**Alternatives considered**:

- Merge 002 and 003 — out of scope for this plan command.

## R11 — Citation minimum fields

**Decision**: Keep/enforce API citation payload: `documentId` (and/or
`documentFilename`) + `snippet` (chunk content). `sourceLocator` / page optional.
Already largely matches `CitationResponse`; tests must assert presence.

**Rationale**: Clarification B; SC-002.

## R12 — Concurrent uploads

**Decision**: Allow existing `@Async` parallel ingest jobs per workspace; no new
queue. Correctness guaranteed by COMPLETE-only retrieval + per-document job
status + delete cleanup. Document that heavy concurrent 80 MB uploads may
contend for CPU/Ollama—operator concern, not a new product surface.

**Rationale**: Edge case listed; YAGNI for a job broker.
