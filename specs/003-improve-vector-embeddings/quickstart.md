# Quickstart Validation Guide: Vector Embeddings & Workspace Rebuild

**Feature**: `003-improve-vector-embeddings`  
**Date**: 2026-08-02

## Prerequisites

- Local environment running backend (`mvn spring-boot:run` or docker-compose).
- Postgres with `pgvector` extension enabled.
- Ollama service running locally with model `nomic-embed-text` pulled:
  ```bash
  ollama pull nomic-embed-text
  ```

---

## Scenario 1: Honest Failure When Embedding Backend Down (SC-002, SC-003)

1. Stop local Ollama service:
   ```bash
   ollama stop
   ```
2. Attempt document upload/indexing via API or UI:
   ```bash
   curl -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/documents \
     -F "file=@sample-facts.pdf"
   ```
3. **Expected Outcome**:
   - HTTP response code: `503 Service Unavailable`.
   - Response error code: `PROVIDER_UNAVAILABLE`.
   - Document status in database is `FAILED`.
   - `SELECT COUNT(*) FROM document_chunks WHERE document_id = '$DOC_ID'` returns `0`.
   - Zero stand-in or hash vectors inserted.

4. Attempt query retrieval:
   ```bash
   curl -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/search \
     -H "Content-Type: application/json" \
     -d '{"query": "What are the distinctive facts?"}'
   ```
5. **Expected Outcome**:
   - HTTP `503 Service Unavailable` with `RETRIEVAL_UNAVAILABLE`.
   - No dummy passages or fabricated similarity scores returned.

---

## Scenario 2: Grounded Retrieval with Real Embeddings (SC-001)

1. Start Ollama and verify model is available:
   ```bash
   ollama serve
   ollama list
   ```
2. Upload fixture document containing 3 distinct facts (e.g. "Alpha project code is 99482"):
   ```bash
   curl -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/documents \
     -F "file=@fixture-facts.txt"
   ```
3. Verify document health status via API:

   ```bash
   curl http://localhost:8080/api/workspaces/$WORKSPACE_ID/index-health
   ```

   **Expected Outcome**: Document health status is `READY`.

4. Ask question targeted at fact:
   ```bash
   curl -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/search \
     -H "Content-Type: application/json" \
     -d '{"query": "What is the Alpha project code?"}'
   ```
5. **Expected Outcome**: Top retrieved passage contains "99482" with high semantic similarity score.

---

## Scenario 3: Embedding Identity Change & Workspace Rebuild (SC-004)

1. Change embedding model configuration in `.env` or app properties to `mxbai-embed-large` (or alternative supported local model):
   ```bash
   export ATLAS_OLLAMA_EMBEDDING_MODEL=mxbai-embed-large
   ```
2. Check workspace index health:

   ```bash
   curl http://localhost:8080/api/workspaces/$WORKSPACE_ID/index-health
   ```

   **Expected Outcome**: Workspace status is `STALE`; document health status is `STALE`.

3. Attempt search before rebuild:

   ```bash
   curl -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/search \
     -H "Content-Type: application/json" \
     -d '{"query": "What is the Alpha project code?"}'
   ```

   **Expected Outcome**: HTTP `409 Conflict` with `EMBEDDING_CONFIG_MISMATCH`.

4. Trigger workspace index rebuild:

   ```bash
   curl -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/rebuild
   ```

   **Expected Outcome**: Response status is `COMPLETED`, `rebuilt_count: 1`.

5. Check workspace index health post-rebuild:

   ```bash
   curl http://localhost:8080/api/workspaces/$WORKSPACE_ID/index-health
   ```

   **Expected Outcome**: Workspace status is `READY`.

6. Search post-rebuild:
   ```bash
   curl -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/search \
     -H "Content-Type: application/json" \
     -d '{"query": "What is the Alpha project code?"}'
   ```
   **Expected Outcome**: Question successfully retrieves passage under new embedding identity.

---

## Scenario 4: Chat Model Change Independence (SC-005)

1. Update chat model from `llama3` to `mistral` without changing `ATLAS_OLLAMA_EMBEDDING_MODEL`:
   ```bash
   export ATLAS_OLLAMA_CHAT_MODEL=mistral
   ```
2. Check workspace index health:
   ```bash
   curl http://localhost:8080/api/workspaces/$WORKSPACE_ID/index-health
   ```
3. **Expected Outcome**: Workspace status remains `READY` (chat model changes do NOT mark index `STALE`). Search and retrieval continue working without requiring rebuild.
