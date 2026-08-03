# Error Envelope Contracts: Honest Embedding Failures

**Feature**: `003-improve-vector-embeddings`  
**Date**: 2026-08-02

## Overview

This contract guarantees that Atlas never returns stand-in vectors, dummy similarity scores, or silent ingest completions when embedding operations fail or configurations mismatch.

---

## Error Scenarios & Specifications

### 1. Ingestion / Rebuild with Unavailable Embedding Backend

- **Trigger**: Document ingestion or workspace rebuild initiated while Ollama / local embedding endpoint is stopped or unreachable.
- **HTTP Status**: `503 Service Unavailable`
- **Error Code**: `PROVIDER_UNAVAILABLE`
- **Behavior Requirements**:
  - Document status transitions to `FAILED`.
  - Document `error_message` records embedding connection failure.
  - Zero `document_chunks` written.
  - Workspace `embedding_model` remains unstamped or unchanged.

```json
{
  "status": 503,
  "code": "PROVIDER_UNAVAILABLE",
  "message": "Embedding backend is unavailable. Check the local AI endpoint and embedding model.",
  "timestamp": "2026-08-02T12:00:00Z"
}
```

---

### 2. Embedding Model Not Pulled / Misconfigured

- **Trigger**: Ollama endpoint is running, but the configured `ATLAS_OLLAMA_EMBEDDING_MODEL` is missing in Ollama or returns empty vector / wrong dimensions.
- **HTTP Status**: `503 Service Unavailable`
- **Error Code**: `PROVIDER_MISCONFIGURED`
- **Behavior Requirements**:
  - Document status transitions to `FAILED`.
  - Clear error message indicating missing model or dimension mismatch.
  - Zero stand-in vectors inserted.

```json
{
  "status": 503,
  "code": "PROVIDER_MISCONFIGURED",
  "message": "Embedding model returned 1024 dimensions but atlas.provider.ollama.embedding-dimensions is 768",
  "timestamp": "2026-08-02T12:00:00Z"
}
```

---

### 3. Query Time Search with Mismatched Embedding Model (STALE Workspace)

- **Trigger**: User sends grounded query to `/api/workspaces/{id}/search` or chat, but workspace was indexed under model `nomic-embed-text` while current system setting is `mxbai-embed-large`.
- **HTTP Status**: `409 Conflict`
- **Error Code**: `EMBEDDING_CONFIG_MISMATCH`
- **Behavior Requirements**:
  - Search fails immediately without invoking vector similarity calculation.
  - Response guides user to trigger index rebuild or restore previous embedding settings.

```json
{
  "status": 409,
  "code": "EMBEDDING_CONFIG_MISMATCH",
  "message": "Workspace vectors use embedding model 'nomic-embed-text' (768 dims). Re-index documents or restore that model before using 'mxbai-embed-large'.",
  "timestamp": "2026-08-02T12:00:00Z"
}
```

---

### 4. Grounded Retrieval Search When Embedding Backend Fails

- **Trigger**: Search query sent while embedding endpoint is unreachable.
- **HTTP Status**: `503 Service Unavailable`
- **Error Code**: `RETRIEVAL_UNAVAILABLE`
- **Behavior Requirements**:
  - Search returns clear HTTP 503 error envelope.
  - System MUST NOT return fallback unrelated document chunks with dummy similarity (e.g. `similarity: 1.0`).

```json
{
  "status": 503,
  "code": "RETRIEVAL_UNAVAILABLE",
  "message": "Search is unavailable. Check the vector database and embedding configuration.",
  "timestamp": "2026-08-02T12:00:00Z"
}
```
