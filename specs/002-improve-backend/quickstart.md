# Quickstart Validation: Improve Backend Reliability

**Feature**: `002-improve-backend` | **Date**: 2026-08-01

Runnable checks that the hardened ingest → retrieve → grounded chat path works
locally. Implementation details live in `tasks.md` (not this file). Contracts:
[api-errors.md](./contracts/api-errors.md), [ingestion.md](./contracts/ingestion.md),
[grounded-chat.md](./contracts/grounded-chat.md). Data model:
[data-model.md](./data-model.md).

## Prerequisites

- Docker + Docker Compose
- Ollama running on the host with a chat model (e.g. `llama3` / configured
  `ATLAS_OLLAMA_MODEL`) and an embedding model matching DB dimensions
  (e.g. `nomic-embed-text` for `vector(768)`)
- `.env` from `.env.example` with **local** provider (no cloud keys required):

```bash
ATLAS_PROVIDER_TYPE=local
ATLAS_OLLAMA_URL=http://host.docker.internal:11434
ATLAS_OLLAMA_MODEL=<your-chat-model>
# After implementation: embedding model + max upload envs as documented in README
```

## Setup

```bash
# from repo root
cp -n .env.example .env   # if needed; set local provider as above
docker compose up --build
# API health
curl -s http://localhost:8080/actuator/health
```

Backend unit/integration tests (preferred regression gate):

```bash
cd backend && ./mvnw test
# or: mvn test
```

## Scenario A — Happy path (SC-001)

1. Create a workspace via API or UI.
2. Upload a small supported sample (≤5 pages PDF or short Markdown) under 80 MB.
3. Poll `GET /api/workspaces/{id}/documents` until `status=COMPLETE` (target:
   within 2 minutes on typical hardware).
4. Create a conversation; ask a question answerable only from that document.
5. **Expect**: Answer grounded with citations that include document id/name +
   snippet; passages only from that workspace.

## Scenario B — Invalid uploads (SC-005)

| Input | Expect |
|-------|--------|
| Empty file | `400` + `UPLOAD_EMPTY` (or equivalent); not COMPLETE |
| File > 80 MB (default) | `400` + `UPLOAD_TOO_LARGE` |
| Unsupported type (e.g. `.exe` renamed) | `400` + `UPLOAD_UNSUPPORTED_TYPE` |

## Scenario C — Provider down (SC-003)

1. With a COMPLETE document, stop Ollama (or point `ATLAS_OLLAMA_URL` at a dead
   port) and restart API if config is bind-on-startup only.
2. Send a chat message.
3. **Expect**: User-visible failure within 15 seconds; **no** synthetic
   “Based on the workspace document sources…” success payload.

## Scenario D — Chat during ingest (FR-005)

1. Upload a larger valid file so status stays `PROCESSING` briefly.
2. Ensure another document is already `COMPLETE` (or accept empty grounding).
3. Chat while the new file is still `PROCESSING`.
4. **Expect**: Citations/passages only from `COMPLETE` docs—not the in-flight one.

## Scenario E — Delete mid-ingest (FR-012)

1. Start upload of a non-trivial file (`PROCESSING`).
2. `DELETE` that document.
3. **Expect**: `204`; job abandoned; later chat/retrieval returns no chunks from
   that document id.

## Scenario F — Embedding mismatch (SC-007)

1. Index documents under embedding model A.
2. Change config to incompatible model/dimensions without re-index.
3. Chat/retrieve.
4. **Expect**: Clear non-success (`EMBEDDING_CONFIG_MISMATCH`); no silent empty
   or mixed-dimension search.

## Scenario G — Workspace isolation (SC-004)

1. Two workspaces with different documents.
2. Chat in A.
3. **Expect**: Zero passages that exist only in B (automated fixture test
   preferred).

## Pass criteria

- Scenarios A–G behave as above.
- `mvn test` covers core journeys and fails on regression of silent fallbacks,
  COMPLETE-only retrieval, upload limits, and mismatch errors (SC-006).
