ALTER TABLE workspaces
  ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(120),
  ADD COLUMN IF NOT EXISTS embedding_dimensions INTEGER;

ALTER TABLE ingestion_jobs
  ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
