Here is a structural overview of your repository and a systematic roadmap to help you ramp up on the codebase efficiently.

---

## Codebase Organization Overview

This repository is a **full-stack RAG (Retrieval-Augmented Generation) application** named **Atlas**. It follows a monorepo structure with a Spring Boot Java backend and a Next.js React frontend.

```
.
├── backend/                  # Java / Spring Boot (REST API, Vector Search, LLM Integration)
│   ├── src/main/java/.../    # Application domain logic
│   ├── src/resources/        # Configurations and Flyway DB migrations
│   └── src/test/             # Integration and unit tests
├── app/                      # Next.js App Router (UI pages & layouts)
├── components/               # React UI components (Chat, Sources, Layout)
├── lib/                      # Client API utilities and helper functions
├── docs/                     # Architecture plans and implementation specs
├── docker-compose.yml        # Orchestration (DB, Services, Environment)
└── Dockerfile                # Frontend container configuration

```

### Key Domain Modules (`backend/src/main/java/dev/atlas/`)

| Package | Purpose |
| --- | --- |
| **`chat`** | Handles conversations, messages, streaming, and grounded chat responses using vector context. |
| **`documents`** | Manages file storage, document text extraction, ingestion jobs, and tracking status. |
| **`retrieval`** | Performs vector search queries and returns relevant text chunks to ground responses. |
| **`providers`** | Interfacing abstractions and DTOs for LLM/Embedding providers (Gemini, OpenAI, Ollama). |
| **`workspaces`** | Multi-tenancy / workspace isolation logic for organizing documents and chats. |
| **`support`** | Web configurations (CORS, security) and request tracing filters. |

---

## Suggested Approach to Understanding the Codebase

To get up to speed quickly without getting overwhelmed by compiled target files or `node_modules`, follow this 4-step approach:

### 1. Start with High-Level Context (15–20 mins)

1. **Read `docs/`:** Start with `docs/PLAN.MD` and `docs/P0_implementation.MD`. This gives you the core design decisions, features, and architecture goals before reading any code.
2. **Review `docker-compose.yml` & `application.yml`:** Look at the infrastructure layer to understand what databases, vector stores, or external models (e.g., PgVector, Ollama, OpenAI) the system relies on.

### 2. Follow the Core Data Flow (30 mins)

Trace the two primary workflows of a RAG pipeline:

* **Workflow A: Document Ingestion**
1. Frontend upload call in `components/sources-panel.tsx` $\rightarrow$ `lib/api.ts`
2. `DocumentController.java` $\rightarrow$ `IngestionService.java`
3. `DocumentExtractor.java` (Parsing text) $\rightarrow$ `DefaultEmbeddingProvider.java` (Generating embeddings) $\rightarrow$ Storage.


* **Workflow B: Grounded Chat Execution**
1. User prompt in `components/chat-panel.tsx` $\rightarrow$ `ConversationController.java`
2. `GroundedChatService.java` $\rightarrow$ `VectorSearchService.java` (Fetches context chunks)
3. Context + Prompt $\rightarrow$ `DefaultLlmProvider.java` (Gemini/OpenAI/Ollama call) $\rightarrow$ Streamed response to UI.



### 3. Check Database Schemas & Interfaces (15 mins)

* Inspect `backend/src/main/resources/db/migration/` (`V1__initial_schema.sql` and `V2__ingestion_jobs.sql`).
* Reviewing table schemas gives you an immediate understanding of how Workspaces, Documents, Chunks, Conversations, and Messages relate.

### 4. Leverage Tests as Executable Documentation (20 mins)

* Look at `backend/src/test/java/dev/atlas/`:
* `GroundedChatServiceTest.java` illustrates how grounded context is formatted and injected.
* `WorkspaceIsolationTest.java` shows how data isolation boundaries are enforced.
* `IngestionWorkflowTest.java` demonstrates end-to-end document processing expectations.

Here is a detailed backend breakdown for **Atlas**, focusing specifically on its Spring Boot Java implementation (`backend/src/main/java/dev/atlas/`).

---

## Architecture Overview

The backend uses a clean, domain-driven modular architecture built on **Spring Boot 3**. It follows a standard **Controller-Service-Repository** pattern with strict workspace-level data isolation.

```
                  ┌─────────────────────────────────┐
                  │      REST Controllers / Web     │
                  │ (Conversation, Document, WS)    │
                  └────────────────┬────────────────┘
                                   │
                  ┌────────────────▼────────────────┐
                  │        Domain Services          │
                  │ (GroundedChat, Ingestion, etc.) │
                  └───────┬─────────────────┬───────┘
                          │                 │
    ┌─────────────────────▼───┐         ┌───▼─────────────────────┐
    │     Provider Layer      │         │    Storage / Retrieval   │
    │  (LLM & Embeddings DTOs)│         │ (VectorSearch, DB Repos)│
    └─────────────────────────┘         └─────────────────────────┘

```

---

## Domain Packages & Components

### 1. Ingestion & Document Management (`/documents`)

Responsible for accepting files, extracting text, splitting content into indexable chunks, and managing async jobs.

* **`DocumentController`**: Exposes REST endpoints to upload files, query document metadata, and track status.
* **`IngestionService`**: Coordinates the pipeline:
1. Accepts uploaded files via `FileStorage`.
2. Extracts raw content using `DocumentExtractor`.
3. Splits content into chunked text segments.
4. Calls `EmbeddingProvider` to create vector embeddings.
5. Stores chunks and vectors in the database.


* **`IngestionJob` & `IngestionJobRepository**`: Manages asynchronous processing jobs, state tracking, and recovery/retry handling (`IngestionRecoveryTest`).
* **`DocumentExtractor`**: Extracts clean text and structural sections (`FilesText`, `ExtractedSection`) from incoming documents.

---

### 2. Retrieval & Vector Search (`/retrieval`)

Acts as the bridge between vector storage and chat generation.

* **`VectorSearchService`**: Performs similarity searches against stored chunk vectors based on incoming query embeddings.
* **`RetrievedChunk`**: Data structure containing the matched text snippet, document ID, similarity score, and metadata needed for context injection and citations.

---

### 3. Grounded Chat Execution (`/chat`)

Executes the Retrieval-Augmented Generation (RAG) loop to serve contextualized responses.

* **`ConversationController`**: Handles chat creation, renaming, message history fetching, and SSE streaming (`ConversationController$StreamMessageRequest`).
* **`GroundedChatService`**: Orchestrates the main RAG flow:
1. Takes user input and workspace context.
2. Requests vector embeddings for the query via `EmbeddingProvider`.
3. Queries `VectorSearchService` for relevant context chunks.
4. Formulates a grounded prompt containing retrieved contexts.
5. Sends the combined prompt to `LlmProvider` and builds `CitationResponse` metadata mapping back to source documents.


* **`Conversation` & `Message**`: JPA entities mapping chat history to workspace entities.

---

### 4. Provider Abstractions (`/providers`)

Encapsulates external AI services to support swapping or multi-model provider strategies.

* **`EmbeddingProvider` / `DefaultEmbeddingProvider**`: Generates vector representations of chunks and search queries.
* **`LlmProvider` / `DefaultLlmProvider**`: Manages communication with backend LLM services (supports text generation and token streaming).
* **`dto/` Package**: Concrete request/response structures for various provider specs:
* **`GeminiDtos`**: Handles Google Gemini API formats (`Candidate`, `Content`, `Part`, `GeminiChatResponse`).
* **`OpenAiDtos`**: Handles OpenAI-compatible payload structures (`OpenAiChatResponse`, `OpenAiChoice`, `OpenAiDelta`).
* **`OllamaDtos`**: Native structures for self-hosted local model runtimes using Ollama.



---

### 5. Multi-Tenancy & Workspace Scope (`/workspaces`)

Ensures data isolation across different teams, projects, or users.

* **`WorkspaceController`**: CRUD operations for workspaces.
* **`WorkspaceLookup`**: Helper class used to resolve and enforce current workspace scope on incoming requests.
* **`WorkspaceRepository`**: Manages persistence boundaries—all document uploads, vector searches, and chat sessions are strictly filtered by workspace context.

---

### 6. Infrastructure & Support (`/support` & `/resources`)

* **`RequestCorrelationFilter`**: Injects correlation IDs into request logs for end-to-end tracing across async jobs.
* **`WebConfiguration`**: Configures CORS policies, serialization settings, and controller interceptors.
* **Flyway DB Migrations (`resources/db/migration/`)**:
* `V1__initial_schema.sql`: Core schema setup for Workspaces, Documents, Vector Chunks, and Conversations.
* `V2__ingestion_jobs.sql`: Tables dedicated to asynchronous background ingestion status tracking.



---

## Core Backend Request-Response Cycle

```
User Question
    │
    ▼
ConversationController  ──►  GroundedChatService
                                 │
                                 ├──► DefaultEmbeddingProvider (Embed query)
                                 │
                                 ├──► VectorSearchService (Query PgVector / DB)
                                 │        └─ Returns: List<RetrievedChunk>
                                 │
                                 ├──► DefaultLlmProvider (Prompt + Context Chunks)
                                 │        └─ Returns: Streamed Tokens
                                 │
                                 └──► Saves Message & Citation details to DB

```
