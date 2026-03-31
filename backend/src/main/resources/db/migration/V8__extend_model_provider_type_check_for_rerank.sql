DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'model_provider'
          AND constraint_name = 'model_provider_type_check'
    ) THEN
        ALTER TABLE model_provider
            DROP CONSTRAINT model_provider_type_check;
    END IF;

    ALTER TABLE model_provider
        ADD CONSTRAINT model_provider_type_check
            CHECK (type IN ('LLM', 'VISION', 'AUDIO', 'EMBEDDING', 'RERANK'));
END
$$;
