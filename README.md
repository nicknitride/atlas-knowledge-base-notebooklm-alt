# Atlas

![Atlas Demo](assets/atlas_demo_gif.gif)

> 💡 **Demo Note**: The demonstration above was captured running 100% locally using a local **Qwen** model (`qwen3.5:2B`) for text generation and **`nomic-embed-text`** (768 dimensions) for vector embeddings via Ollama.

Atlas is a privacy-first, self-hosted AI knowledge workspace and RAG engine. It enables users to upload documents, build vector indexes, and hold grounded conversations with precise citations—all running locally or with optional cloud LLMs.

---

## Features (Everyday User)

- 🔒 **100% Private & Self-Hosted**: Keep all your sensitive notes, research, and documents completely private on your local machine with zero subscription fees or third-party data tracking.
- 📚 **Workspace Organization**: Separate your files into distinct workspaces (e.g., Work, Research, Personal Projects) to keep context and search boundaries clean.
- 📄 **Large File Uploads**: Upload documents and large text files (up to **80 MB** per file) with real-time status indicators (`Pending`, `Processing`, `Complete`, `Failed`).
- 💬 **Grounded AI Conversations**: Chat naturally with an AI assistant that answers questions using your actual uploaded documents as context, eliminating hallucinated answers.
- 🔍 **Verifiable Source Citations**: Every answer includes explicit citations linking back to exact document passages so you can immediately double-check facts.
- ⚡ **Flexible LLM Options**: Works seamlessly with local open-weight models via Ollama (Qwen, Llama 3, etc.) or optional cloud providers (Google Gemini, OpenAI).

---

## Features (Engineering & Maintainability)

### 🧠 Production-Grade RAG & Vector Embedding Pipeline
- **`pgvector` Store with HNSW Indexing**: Uses PostgreSQL with the `pgvector` extension and **HNSW** (Hierarchical Navigable Small World) indexing using cosine distance (`vector_cosine_ops`) for high-performance vector search.
- **Embedding Identity & Strict Fail-Closed Safeguards**: Tracks embedding model identity and dimensions (e.g., `nomic-embed-text` at 768 dimensions) at the workspace level. Grounded retrieval fails closed on model mismatch to prevent silent corruptions or mixing incompatible vector spaces.
- **Workspace Index Rebuilding**: Includes capability to trigger re-indexing across workspace documents when switching embedding models or vector dimensions.
- **Chunking Engine & Metadata**: Intelligently chunks incoming documents and attaches structured `source_locator` JSON metadata (ordinal, offsets, file mappings) to support exact citation tracing.

### 🏗️ Robust Backend Architecture (Spring Boot 3 + Java)
- **Asynchronous Ingestion Pipeline**: Asynchronous background jobs decouple document ingestion from HTTP request threads. Job states (`PENDING`, `PROCESSING`, `COMPLETE`, `FAILED`) are tracked with configurable processing timeouts (`ATLAS_INGESTION_TIMEOUT`).
- **Flyway Database Migrations**: Relational schema and vector table evolutions are fully managed via Flyway versioned SQL scripts (`V1` through `V4`). JPA/Hibernate DDL updates are restricted to strict schema validation.
- **Observability & Request Tracing**: Unified JSON error envelope (`{ "code", "message", "requestId" }`) and MDC logging with correlation IDs across HTTP endpoints and Spring Boot Actuator probes (`/actuator/health`).

### 🎨 Next.js Web Interface
- **NotebookLM-Inspired UX**: Modern React dashboard with side-by-side workspace management, document status list, grounded chat interface, and citation inspector.
- **Type-Safe Integration**: Resilient state management with automatic status polling during ingestion.

### 🐳 Containerized Deployment
- **Docker Compose Orchestration**: Single-command startup combining PostgreSQL (`pgvector/pgvector:pg16`), Spring Boot API, and Next.js frontend with host gateway routing (`host.docker.internal`) for local Ollama connectivity.

---

## How to Run

### Run Locally

1. Copy `.env.example` to `.env` and configure your settings:
   ```bash
   cp .env.example .env
   ```
2. Pull the Ollama models configured in your setup (default recommendations: chat model `qwen3.5` or `llama3`, and embedding model `nomic-embed-text` at 768 dimensions):
   ```bash
   ollama pull qwen3.5
   ollama pull nomic-embed-text
   ```
3. Launch the container stack:
   ```bash
   docker-compose up --build
   ```
4. Open the web app at [http://localhost:3000](http://localhost:3000). The API health endpoint is available at [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

By default, `ATLAS_PROVIDER_TYPE=local` targets the Ollama-compatible endpoint at `ATLAS_OLLAMA_URL`. Cloud providers (Gemini/OpenAI) are optional and never required for core document upload, vector indexing, or grounded chat.

### Backend Reliability & Ingestion Settings

- **Upload Limit**: Document upload defaults to an **80 MB** max (`ATLAS_MAX_UPLOAD_BYTES`).
- **Ingestion States**: Ingestion exposes `PENDING` / `PROCESSING` / `COMPLETE` / `FAILED` for UI indicators.
- **Embedding Guardrails**: Changing the embedding model after indexing requires a re-index (or restoring the prior model)—grounded chat fails closed on mismatch instead of mixing vector sizes.
- **Validation Checklist**: See [`specs/002-improve-backend/quickstart.md`](specs/002-improve-backend/quickstart.md).

### API Foundation

- **Workspace CRUD**: `/api/workspaces`
- **Document Management**: `/api/workspaces/{id}/documents`
- **Conversations & Grounded Chat**: `/api/workspaces/{id}/conversations`
- **Error Format**: API errors return standardized JSON: `{ "code": "...", "message": "...", "requestId": "..." }`.
- **Database Control**: Flyway owns the database schema migrations; do not use Hibernate DDL updates.
