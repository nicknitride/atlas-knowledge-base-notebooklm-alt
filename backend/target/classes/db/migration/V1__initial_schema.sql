CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE workspaces (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  original_filename VARCHAR(512) NOT NULL,
  content_type VARCHAR(255) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  ingestion_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE document_chunks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  ordinal INTEGER NOT NULL,
  content TEXT NOT NULL,
  source_locator JSONB NOT NULL DEFAULT '{}'::jsonb,
  embedding vector(1536),
  UNIQUE (document_id, ordinal)
);

CREATE INDEX document_chunks_embedding_idx ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX documents_workspace_idx ON documents (workspace_id);

CREATE TABLE conversations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  title VARCHAR(240) NOT NULL DEFAULT 'New conversation',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE message_citations (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  chunk_id UUID NOT NULL REFERENCES document_chunks(id) ON DELETE RESTRICT,
  ordinal INTEGER NOT NULL,
  PRIMARY KEY (message_id, ordinal)
);
