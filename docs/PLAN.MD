# Atlas Implementation Plan

Atlas is a self-hosted AI knowledge workspace. It lets users organize private
source material into isolated workspaces, ask grounded questions, and receive
streamed answers with traceable citations. This plan prioritizes a reliable,
deployable vertical slice before broader provider, ingestion, and collaboration
capabilities.

## Priority definitions

- **P0 — Must ship:** Required for a secure, usable v1 release.
- **P1 — Should ship:** High-value improvements that complete the core product.
- **P2 — Planned:** Important expansion after the core is stable.
- **P3 — Future:** Exploratory or longer-term product work.

## P0 — Foundation and usable v1

- [x] Define the monorepo layout, module boundaries, and local development workflow for the TypeScript frontend and Java 21 / Spring Boot API.
- [x] Establish configuration conventions: environment variables, typed configuration validation, secrets handling, and example `.env` files.
- [x] Provision PostgreSQL with pgvector and create versioned database migrations for users, workspaces, documents, chunks, conversations, messages, and citations.
- [x] Implement workspace CRUD with strict workspace scoping for documents, chunks, conversations, searches, and generated answers.
- [x] Build the responsive workspace UI: workspace switcher, empty states, document list, chat view, source panel, loading states, and keyboard-accessible controls.
- [x] Implement document upload with file-size/type validation and persistent object/file storage mounted through Docker volumes.
- [x] Implement robust PDF, Markdown, and plain-text extraction; retain source names, page/section boundaries, and extraction failures for user review.
- [x] Define a deterministic chunking strategy that preserves document location metadata needed for citations.
- [x] Add an asynchronous ingestion workflow that tracks each document through upload, parsing, chunking, embedding, indexing, completion, and failure.
- [x] Implement embeddings through an initial provider adapter and store chunk vectors in pgvector.
- [x] Implement workspace-constrained vector retrieval with relevance thresholds and source-location metadata.
- [x] Implement a grounded chat pipeline: retrieve context, construct a constrained prompt, call the model, and decline or qualify answers unsupported by the retrieved sources.
- [x] Stream chat responses from the API to the UI, including cancel/retry behavior and recoverable provider errors.
- [x] Persist conversations and messages per workspace; support starting a chat, reopening a chat, renaming it, and deleting it.
- [x] Generate answer citations from retrieved chunks and render links to the relevant source document, page, section, or paragraph.
- [x] Add an LLM provider interface and ship one cloud provider plus Ollama as the first local-provider implementation without coupling provider details to chat services.
- [x] Provide Docker Compose for the frontend, API, PostgreSQL/pgvector, and persistent volumes; verify `docker compose up -d` yields a working local instance.
- [x] Add structured logs, request/correlation IDs, health/readiness endpoints, and safe user-facing error responses.
- [x] Write setup, configuration, architecture, and basic troubleshooting documentation.
- [x] Add automated tests for workspace isolation, ingestion state transitions, chunk citation mapping, retrieval constraints, and the grounded-chat service.

## P1 — Core product completion

- [ ] Add `.docx` and HTML ingestion with normalized metadata and citation anchors.
- [ ] Add keyword indexing and BM25/full-text retrieval in PostgreSQL alongside semantic search.
- [ ] Combine semantic, keyword, metadata, recency, and workspace signals into hybrid retrieval; make ranking weights configurable.
- [ ] Add a reranking stage with clear fallbacks when a reranker is unavailable.
- [ ] Add document metadata filters, source selection, date filters, and search-result previews.
- [ ] Support OpenAI, Anthropic, Google Gemini, and OpenRouter through the provider abstraction, including model capability metadata and streaming support.
- [ ] Support BGE, Nomic, and Ollama embedding models, with re-indexing workflows when the selected embedding model changes.
- [ ] Add authentication, user accounts, secure session handling, and authorization checks at every API boundary.
- [ ] Add workspace sharing with owner/editor/viewer roles and audit-friendly ownership rules.
- [ ] Add conversation-history search and richer conversation management.
- [ ] Add retry controls and an admin-facing view for failed ingestion jobs.
- [ ] Add optional Redis-backed job coordination, caching, and rate limiting without making Redis mandatory for a single-node deployment.
- [ ] Add accessibility verification, responsive-device testing, and a consistent design system for all loading, empty, and error states.
- [ ] Add CI checks for linting, unit/integration tests, container builds, dependency/security scanning, and database migration validation.

## P2 — Ingestion, retrieval, and integration expansion

- [ ] Add OCR ingestion for scanned PDFs and image-heavy documents, with confidence reporting and a review path for failed extraction.
- [ ] Add GitHub repository ingestion with repository selection, branch/ref tracking, incremental sync, and source links back to files and lines.
- [ ] Add Obsidian vault import while preserving folders, links, frontmatter, and note relationships.
- [ ] Add scheduled and incremental re-indexing for changed documents and external sources.
- [ ] Add document hierarchy browsing, tags, custom metadata schemas, and bulk document operations.
- [ ] Add retrieval evaluation datasets, relevance metrics, citation-accuracy checks, and regression tests for common knowledge-base queries.
- [ ] Add observability dashboards for ingestion duration, retrieval quality signals, provider latency, token use, and error rates.
- [ ] Add backup/restore documentation and tooling for PostgreSQL, vector data, and document volumes.
- [ ] Publish versioned API documentation and an API for third-party integrations.

## P3 — Advanced knowledge workflows

- [ ] Explore GraphRAG and a knowledge-graph extraction pipeline with explicit provenance to source chunks.
- [ ] Build an interactive knowledge-graph visualization for documents, entities, and relationships.
- [ ] Add bounded, auditable agent workflows that operate only on workspace-authorized sources and tools.
- [ ] Add MCP integration with explicit permission controls and workspace isolation.
- [ ] Add audio summaries and image understanding with citations and model/provider capability checks.
- [ ] Design and implement a plugin system with stable extension points, permission declarations, and compatibility versioning.
- [ ] Add multi-node deployment guidance, horizontal worker scaling, and production operational runbooks.

## Release milestones

| Milestone | Exit criteria |
| --- | --- |
| **M0: Development foundation** | Local frontend/API/database environment, migrations, configuration, and health checks work reliably. |
| **M1: Source-to-answer vertical slice** | A user can create a workspace, upload PDF/Markdown/text, wait for ingestion, ask a question, and see a streamed cited answer. |
| **M2: Self-hosted v1** | M1 runs through Docker Compose with persistence, test coverage for critical paths, logs, documentation, and graceful errors. |
| **M3: Collaborative, hybrid knowledge workspace** | Authentication, sharing, hybrid retrieval, additional formats/providers, and CI/CD quality gates are complete. |

## Architecture guardrails

- Keep provider adapters, parsers, embedding models, storage, and retrieval strategies behind interfaces so they can evolve independently.
- Treat workspace ID and authorization context as mandatory inputs to every data access and retrieval operation.
- Store citation provenance at ingestion time; do not attempt to reconstruct source locations only after an answer is generated.
- Keep long-running ingestion and re-indexing off synchronous request paths.
- Prefer a single-node Docker Compose deployment as the baseline, while avoiding design choices that prevent future horizontal scaling.
- Measure retrieval and citation quality independently from model fluency; a polished answer is not sufficient when its sources are weak.
