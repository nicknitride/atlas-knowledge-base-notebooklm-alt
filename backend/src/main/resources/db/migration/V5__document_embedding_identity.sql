ALTER TABLE documents
  ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(120),
  ADD COLUMN IF NOT EXISTS embedding_dimensions INTEGER;
