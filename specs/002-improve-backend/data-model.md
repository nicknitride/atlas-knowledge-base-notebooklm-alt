# Data Model: Improve Backend Reliability

**Feature**: `002-improve-backend` | **Date**: 2026-08-01

Entities map to existing PostgreSQL / JPA types unless noted as **additive**.

## Workspace

Isolation boundary for documents, conversations, and retrieval.

| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| name | string | Existing |
| created_at / updated_at | timestamptz | Existing |
| embedding_model | string | **Additive** — last successful ingest embedding model for this workspace (or null if never indexed) |
| embedding_dimensions | int | **Additive** — vector length matching pgvector column / provider output |

**Validation**: When set, grounded retrieval requires configured embedding
model/dimensions to match these values (FR-004).

## Document (KnowledgeDocument)

| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| workspace_id | UUID | FK → workspace, CASCADE |
| original_filename | string | Required |
| content_type | string | PDF / Markdown / plain text supported |
| storage_key | string | Local filesystem key |
| ingestion_status | enum | `PENDING` \| `PROCESSING` \| `COMPLETE` \| `FAILED` |
| failure_reason | string? | User-safe reason when FAILED |
| created_at / updated_at | timestamptz | Existing |
| embedding_model | string? | **Additive (optional)** — stamp at COMPLETE; aids per-doc mismatch diagnostics |

### Ingestion status transitions

```text
PENDING → PROCESSING → COMPLETE
PENDING → PROCESSING → FAILED
PENDING → FAILED          (validation / missing file)
PROCESSING → FAILED       (timeout, provider error, cancel/delete)
any → (row deleted)       (user delete / workspace delete)
```

**Rules**:
- Only `COMPLETE` documents contribute chunks to grounded retrieval (FR-005).
- FAILED never presented as indexed knowledge (FR-003).
- Timeout: PROCESSING longer than configured wall clock (default 10 min) → FAILED.

## Ingestion job

| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| document_id | UUID | FK → documents ON DELETE CASCADE |
| status | string | `PENDING` \| `PROCESSING` \| `COMPLETED` \| `FAILED` (existing job table) |
| failure_reason | string? | Existing |
| created_at / updated_at | timestamptz | Existing |
| started_at | timestamptz? | **Additive (optional)** — for timeout detection |

**Rules**:
- Delete document cancels/abandons job via CASCADE + safe abort in worker (FR-012).
- Progress for UI: document `ingestion_status` (+ optional future percent); MVP
  is status + `failure_reason` polling via list/get document.

## Chunk / passage

| Field | Type | Notes |
|-------|------|--------|
| id | UUID | PK |
| document_id | UUID | FK CASCADE |
| ordinal | int | Order within document |
| content | text | Passage text (citation snippet source) |
| source_locator | jsonb | Optional location metadata |
| embedding | vector(N) | N must match configured embedding dimensions (currently 768 in V3) |

**Rules**: Partial chunks written mid-ingest are removed when document deleted
(CASCADE). Prefer transactional cleanup on FAILED if partial rows exist (delete
chunks for that document on failure path if any were inserted before fail).

## Conversation / message

Unchanged structurally. Assistant messages may link citations.

## Citation

Persisted link `message_citations (message_id, chunk_id, ordinal)` plus API DTO:

| Field | Required | Notes |
|-------|----------|--------|
| documentId | yes | Stable document id |
| documentFilename | yes* | Stable name (*required if id alone insufficient for UX; API should send both when available) |
| snippet | yes | Verbatim chunk content |
| sourceLocator / ordinal / similarity | optional | Page/offset when available; similarity must be real when present—never fabricated |

## AI provider configuration (runtime, not DB)

| Setting | Source | Notes |
|---------|--------|--------|
| provider type | `ATLAS_PROVIDER_TYPE` | `local` → Ollama path; `ollama`/`openai`/`gemini` |
| ollama URL | `ATLAS_OLLAMA_URL` | Default localhost / host.docker.internal |
| chat model | `ATLAS_OLLAMA_MODEL` (+ cloud model envs) | Configurable |
| embedding model | config (e.g. `ATLAS_OLLAMA_EMBEDDING_MODEL`) | Must align with vector dimensions |
| max upload | `atlas.max-upload-bytes` (default 80 MiB) | Applied at multipart + controller |
| ingest timeout | `atlas.ingestion.processing-timeout` (default 10m) | FR-002 |

## Relationships

```text
Workspace 1──* Document 1──* Chunk
Workspace 1──* Conversation 1──* Message 1──* Citation → Chunk
Document 1──* IngestionJob
```

Workspace isolation: all retrieval queries constrain `workspace_id`; cross-workspace
leakage forbidden (FR-011 / SC-004).
