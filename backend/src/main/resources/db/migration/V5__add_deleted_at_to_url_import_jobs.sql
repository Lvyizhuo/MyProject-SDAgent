ALTER TABLE url_import_jobs
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_url_import_jobs_deleted_at ON url_import_jobs(deleted_at);