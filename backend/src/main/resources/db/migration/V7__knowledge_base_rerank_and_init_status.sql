ALTER TABLE knowledge_folders
    ADD COLUMN IF NOT EXISTS rerank_model_id BIGINT,
    ADD COLUMN IF NOT EXISTS rerank_model_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS init_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS init_error TEXT,
    ADD COLUMN IF NOT EXISTS initialized_at TIMESTAMP;

UPDATE knowledge_folders
SET init_status = COALESCE(NULLIF(init_status, ''), 'READY')
WHERE init_status IS NULL OR init_status = '';
