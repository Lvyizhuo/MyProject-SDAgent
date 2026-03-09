CREATE TABLE IF NOT EXISTS model_provider (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    api_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    max_tokens INTEGER,
    top_p DECIMAL(5,4),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_model_provider_type ON model_provider(type);
CREATE INDEX IF NOT EXISTS idx_model_provider_provider ON model_provider(provider);
CREATE INDEX IF NOT EXISTS idx_model_provider_type_default ON model_provider(type, is_default);

ALTER TABLE agent_config
    ADD COLUMN IF NOT EXISTS llm_model_id BIGINT,
    ADD COLUMN IF NOT EXISTS vision_model_id BIGINT,
    ADD COLUMN IF NOT EXISTS audio_model_id BIGINT,
    ADD COLUMN IF NOT EXISTS embedding_model_id BIGINT;
