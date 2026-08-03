# REST API Contract: Workspace Index Health & Rebuild

**Feature**: `003-improve-vector-embeddings`  
**Date**: 2026-08-02

---

## 1. Get Workspace Index Health

Retrieves the workspace-level and document-level embedding health status relative to current system configuration.

- **HTTP Method**: `GET`
- **Path**: `/api/workspaces/{workspaceId}/index-health`

### Headers

| Header   | Value              | Description     |
| -------- | ------------------ | --------------- |
| `Accept` | `application/json` | Required format |

### Path Parameters

| Parameter     | Type   | Description            |
| ------------- | ------ | ---------------------- |
| `workspaceId` | `UUID` | ID of target workspace |

### Response 200 OK

```json
{
  "workspace_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "active_embedding_identity": {
    "model": "nomic-embed-text",
    "dimensions": 768
  },
  "indexed_embedding_identity": {
    "model": "nomic-embed-text",
    "dimensions": 768
  },
  "status": "READY",
  "total_documents": 3,
  "ready_documents": 3,
  "stale_documents": 0,
  "pending_documents": 0,
  "failed_documents": 0,
  "documents": [
    {
      "id": "e81d7f62-3e2a-46f9-81a9-3d1f1f2a3456",
      "filename": "e81d7f62-3e2a-46f9-81a9-3d1f1f2a3456.pdf",
      "original_filename": "architecture-guide.pdf",
      "ingestion_status": "COMPLETE",
      "health_status": "READY",
      "embedding_model": "nomic-embed-text",
      "embedding_dimensions": 768,
      "error_message": null
    }
  ]
}
```

### Stale State Example (200 OK)

When system embedding model changes to e.g. `mxbai-embed-large`:

```json
{
  "workspace_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "active_embedding_identity": {
    "model": "mxbai-embed-large",
    "dimensions": 768
  },
  "indexed_embedding_identity": {
    "model": "nomic-embed-text",
    "dimensions": 768
  },
  "status": "STALE",
  "total_documents": 3,
  "ready_documents": 0,
  "stale_documents": 3,
  "pending_documents": 0,
  "failed_documents": 0,
  "documents": [
    {
      "id": "e81d7f62-3e2a-46f9-81a9-3d1f1f2a3456",
      "original_filename": "architecture-guide.pdf",
      "ingestion_status": "COMPLETE",
      "health_status": "STALE",
      "embedding_model": "nomic-embed-text",
      "embedding_dimensions": 768,
      "error_message": null
    }
  ]
}
```

---

## 2. Trigger Workspace Index Rebuild

Re-indexes all existing documents in the workspace using the current active embedding configuration.

- **HTTP Method**: `POST`
- **Path**: `/api/workspaces/{workspaceId}/rebuild`

### Headers

| Header         | Value              | Description                |
| -------------- | ------------------ | -------------------------- |
| `Content-Type` | `application/json` | Optional payload parameter |

### Response 200 OK (Rebuild Completed)

```json
{
  "workspace_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "status": "COMPLETED",
  "total_processed": 3,
  "rebuilt_count": 3,
  "failed_count": 0,
  "active_embedding_identity": {
    "model": "mxbai-embed-large",
    "dimensions": 768
  },
  "errors": []
}
```

### Response 200 OK (Partial Failure)

```json
{
  "workspace_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "status": "PARTIAL_FAILURE",
  "total_processed": 3,
  "rebuilt_count": 2,
  "failed_count": 1,
  "active_embedding_identity": {
    "model": "mxbai-embed-large",
    "dimensions": 768
  },
  "errors": [
    {
      "document_id": "f92e8a71-4b3c-58da-92b0-4e2a2f3b4567",
      "filename": "corrupted-file.pdf",
      "error_message": "Failed to read original document stream from storage"
    }
  ]
}
```

### Response 503 Service Unavailable (Embedding Backend Down)

```json
{
  "status": 503,
  "code": "PROVIDER_UNAVAILABLE",
  "message": "Embedding backend is unavailable. Check the local AI endpoint and embedding model.",
  "timestamp": "2026-08-02T12:00:00Z"
}
```
