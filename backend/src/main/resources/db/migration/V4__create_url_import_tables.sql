CREATE TABLE IF NOT EXISTS url_import_jobs (
    id BIGSERIAL PRIMARY KEY,
    source_url VARCHAR(1000) NOT NULL,
    source_site VARCHAR(200) NOT NULL,
    target_folder_id BIGINT REFERENCES knowledge_folders(id) ON DELETE SET NULL,
    embedding_model VARCHAR(200),
    title_override VARCHAR(500),
    remark TEXT,
    status VARCHAR(50) NOT NULL,
    discovered_count INTEGER NOT NULL DEFAULT 0,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    imported_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_url_import_jobs_status ON url_import_jobs(status);
CREATE INDEX IF NOT EXISTS idx_url_import_jobs_created_at ON url_import_jobs(created_at DESC);

CREATE TABLE IF NOT EXISTS url_import_items (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES url_import_jobs(id) ON DELETE CASCADE,
    source_url VARCHAR(1000) NOT NULL,
    source_page VARCHAR(1000),
    source_site VARCHAR(200) NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    source_title VARCHAR(500),
    publish_date DATE,
    raw_object_path VARCHAR(1000),
    cleaned_text_object_path VARCHAR(1000),
    cleaned_text TEXT,
    content_hash VARCHAR(128),
    quality_score INTEGER NOT NULL DEFAULT 0,
    parse_status VARCHAR(50) NOT NULL,
    review_status VARCHAR(50) NOT NULL,
    review_comment TEXT,
    suspected_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(200),
    tags VARCHAR(500)[],
    summary TEXT,
    error_message TEXT,
    knowledge_document_id BIGINT REFERENCES knowledge_documents(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_url_import_items_job_id ON url_import_items(job_id);
CREATE INDEX IF NOT EXISTS idx_url_import_items_review_status ON url_import_items(review_status);
CREATE INDEX IF NOT EXISTS idx_url_import_items_quality_score ON url_import_items(quality_score DESC);
CREATE INDEX IF NOT EXISTS idx_url_import_items_source_url ON url_import_items(source_url);
CREATE INDEX IF NOT EXISTS idx_url_import_items_content_hash ON url_import_items(content_hash);

CREATE TABLE IF NOT EXISTS url_import_attachments (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES url_import_items(id) ON DELETE CASCADE,
    attachment_url VARCHAR(1000) NOT NULL,
    file_name VARCHAR(500),
    file_type VARCHAR(100),
    storage_path VARCHAR(1000),
    parsed_text_path VARCHAR(1000),
    parse_status VARCHAR(50) NOT NULL,
    ocr_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_url_import_attachments_item_id ON url_import_attachments(item_id);

CREATE TABLE IF NOT EXISTS knowledge_document_sources (
    id BIGSERIAL PRIMARY KEY,
    knowledge_document_id BIGINT NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    import_item_id BIGINT NOT NULL REFERENCES url_import_items(id) ON DELETE CASCADE,
    source_site VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    source_page VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kds_document_id ON knowledge_document_sources(knowledge_document_id);
CREATE INDEX IF NOT EXISTS idx_kds_import_item_id ON knowledge_document_sources(import_item_id);
CREATE INDEX IF NOT EXISTS idx_kds_source_url ON knowledge_document_sources(source_url);