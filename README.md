# Atlas

![alt text](assets/atlas_demo_gif.gif)
Atlas is a self-hosted AI knowledge workspace. This repository contains the
Next.js interface (`/`) and Spring Boot API (`/backend`).

## Run locally

1. Copy `.env.example` to `.env` and replace the development password.
2. Pull Ollama models used by your config (defaults: chat `llama3`, embeddings
   `nomic-embed-text` at 768 dimensions).
3. Run `docker-compose up --build`.
4. Open `http://localhost:3000`; the API health endpoint is at
   `http://localhost:8080/actuator/health`.

Default `ATLAS_PROVIDER_TYPE=local` uses the Ollama-compatible endpoint at
`ATLAS_OLLAMA_URL`. Cloud providers are optional and never required for core
upload → index → grounded chat.

## Backend reliability

Document upload defaults to an **80 MB** max (`ATLAS_MAX_UPLOAD_BYTES`).
Ingestion exposes `PENDING` / `PROCESSING` / `COMPLETE` / `FAILED` for UI
indicators. Changing the embedding model after indexing requires re-index (or
restoring the prior model)—grounded chat fails closed on mismatch instead of
mixing vector sizes.

Validation checklist: [`specs/002-improve-backend/quickstart.md`](specs/002-improve-backend/quickstart.md).

## API foundation

Workspace CRUD: `/api/workspaces`. Documents:
`/api/workspaces/{id}/documents`. Conversations and grounded chat under
`/api/workspaces/{id}/conversations`. Errors return JSON
`{ "code", "message", "requestId?" }`. Flyway owns the database schema; do not
use Hibernate DDL updates.
