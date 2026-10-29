-- ============================================================
-- V10: 规范历史迁移中的驼峰列名，与 Hibernate 默认物理命名策略对齐
--
-- 背景
--   V1/V2/V3 建表时按实体字段名书写列名（fileName、apiKey、topP ...），
--   而 PostgreSQL 对未加引号的标识符会折叠为小写（filename、apikey、topp），
--   AgentConfig 等实体又没有显式声明 @Column(name=...)，Hibernate 6 默认
--   使用 CamelCaseToUnderscoresNamingStrategy，期望的是 file_name / api_key。
--
--   历史上 ddl-auto=update 会自动补建 snake_case 列，从而掩盖了这个不一致；
--   改为 ddl-auto=validate 后，全新数据库启动会直接失败：
--     Schema-validation: missing column [api_key] in table [agent_config]
--
-- 兼容策略（逐列处理，可重复执行）
--   1. 仅旧列存在        -> 直接重命名，保留原有约束与数据
--   2. 新旧列同时存在    -> 先把旧列的值合并到新列（仅当新列为空），再删除旧列
--   3. 仅新列存在        -> 跳过
-- ============================================================

DO $$
DECLARE
    pair RECORD;
    has_old BOOLEAN;
    has_new BOOLEAN;
BEGIN
    FOR pair IN
        SELECT * FROM (VALUES
            -- agent_config（V2 建表，7 列）
            ('agent_config',     'modelprovider',    'model_provider'),
            ('agent_config',     'apikey',           'api_key'),
            ('agent_config',     'apiurl',           'api_url'),
            ('agent_config',     'modelname',        'model_name'),
            ('agent_config',     'systemprompt',     'system_prompt'),
            ('agent_config',     'greetingmessage',  'greeting_message'),
            ('agent_config',     'mcpserversconfig', 'mcp_servers_config'),
            -- knowledge_documents（V1 建表，3 列）
            ('knowledge_documents', 'filename',      'file_name'),
            ('knowledge_documents', 'filesize',      'file_size'),
            ('knowledge_documents', 'filetype',      'file_type')
            -- 注意：model_provider.topP 经命名策略处理后落库即是 topp（V3 已正确），无需改名
        ) AS t(tbl, old_name, new_name)
    LOOP
        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = pair.tbl
              AND column_name = pair.old_name
        ) INTO has_old;

        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = pair.tbl
              AND column_name = pair.new_name
        ) INTO has_new;

        IF has_old AND NOT has_new THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN %I TO %I',
                           pair.tbl, pair.old_name, pair.new_name);
            RAISE NOTICE 'renamed %.% -> %', pair.tbl, pair.old_name, pair.new_name;

        ELSIF has_old AND has_new THEN
            EXECUTE format('UPDATE %I SET %I = %I WHERE %I IS NULL',
                           pair.tbl, pair.new_name, pair.old_name, pair.new_name);
            EXECUTE format('ALTER TABLE %I DROP COLUMN %I', pair.tbl, pair.old_name);
            RAISE NOTICE 'merged and dropped duplicate column %.%', pair.tbl, pair.old_name;
        END IF;
    END LOOP;
END $$;
