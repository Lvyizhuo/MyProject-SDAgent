-- V9__create_agent_instance_and_tool_definition.sql
-- 多智能体 + 工具定义的卡片化支持表
--
-- 注意：schema 由 Flyway 统一管理（V1-V9），Hibernate ddl-auto 已切换为 validate 仅做校验。

CREATE TABLE IF NOT EXISTS agent_instance (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(50),
    icon VARCHAR(50),
    status VARCHAR(20) DEFAULT 'draft',
    is_default BOOLEAN DEFAULT FALSE,
    version VARCHAR(50),
    daily_calls BIGINT DEFAULT 0,
    enabled_tools TEXT,
    system_prompt TEXT,
    greeting_message TEXT,
    skills_json TEXT,
    mcp_servers_config TEXT,
    knowledge_folder_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_agent_instance_status ON agent_instance(status);
CREATE INDEX IF NOT EXISTS idx_agent_instance_is_default ON agent_instance(is_default);

CREATE TABLE IF NOT EXISTS agent_model_binding (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent_instance(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    model_id BIGINT,
    tool_code VARCHAR(100),
    display_name VARCHAR(100),
    CONSTRAINT uq_agent_role UNIQUE (agent_id, role)
);

CREATE INDEX IF NOT EXISTS idx_agent_model_binding_agent ON agent_model_binding(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_model_binding_model ON agent_model_binding(model_id);

CREATE TABLE IF NOT EXISTS tool_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(30) NOT NULL,
    icon VARCHAR(50),
    is_enabled BOOLEAN DEFAULT TRUE,
    daily_calls BIGINT DEFAULT 0,
    used_by_agent_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_definition_category ON tool_definition(category);
CREATE INDEX IF NOT EXISTS idx_tool_definition_enabled ON tool_definition(is_enabled);
