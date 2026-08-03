# API Contract: Grounded Chat & Retrieval

**Feature**: `002-improve-backend` | **Date**: 2026-08-01

Base path: `/api/workspaces/{workspaceId}/conversations`

## Scope of retrieval

When answering in a workspace:

1. Query embedding uses the **configured** embedding provider (fail-closed).
2. Candidate chunks are restricted to `workspace_id` match **and** parent
   document `ingestion_status = COMPLETE`.
3. In-progress / failed documents contribute **no** passages or citations.
4. Cross-workspace leakage is forbidden (SC-004).

## Embedding identity

If configured embedding model/dimensions disagree with stored workspace (or
searchable document) identity:

- Chat/retrieval MUST fail with `EMBEDDING_CONFIG_MISMATCH` (or equivalent
  non-success), clear message instructing re-index or restore prior config.
- MUST NOT silently search mixed dimensions or pad/truncate incompatibly.

## Provider failure

If the configured LLM or embedding backend is unreachable / misconfigured:

- MUST fail within 15 seconds of user-visible non-success (SC-003).
- MUST NOT stream or return offline placeholder text as a normal grounded
  success (FR-007).
- Prefer `PROVIDER_UNAVAILABLE` / `PROVIDER_MISCONFIGURED`.

## Citations on success

Each citation in message responses MUST include at minimum:

| Field                   | Required                                          |
| ----------------------- | ------------------------------------------------- |
| documentId              | yes                                               |
| documentFilename        | yes (when known)                                  |
| snippet                 | yes (verbatim passage text)                       |
| sourceLocator / ordinal | optional                                          |
| similarity              | optional; if present MUST be real retrieval score |

100% of citations must map to real active-workspace passages (SC-002).

## No relevant evidence

When retrieval returns no usable chunks:

- Do not fabricate citations.
- Model may decline/qualify; do not invent high-confidence grounding from
  unrelated or fake-similarity chunks.

## Endpoints (existing)

| Method | Path                                                            | Notes                          |
| ------ | --------------------------------------------------------------- | ------------------------------ |
| POST   | `.../conversations/{id}/messages`                               | Sync chat + citations          |
| POST   | `.../conversations/{id}/messages/stream` (or existing SSE path) | Stream; same fail-closed rules |

Exact stream path follows current `ConversationController`; behavior contract
above applies regardless of transport.
