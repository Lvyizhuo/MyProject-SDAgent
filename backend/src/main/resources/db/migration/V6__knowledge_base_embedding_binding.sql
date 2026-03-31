ALTER TABLE knowledge_folders
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(200),
    ADD COLUMN IF NOT EXISTS vector_table_name VARCHAR(100);

WITH folder_model_from_docs AS (
    SELECT d.folder_id,
           MAX(d.embedding_model) AS embedding_model,
           MAX(d.vector_table_name) AS vector_table_name
    FROM knowledge_documents d
    WHERE d.folder_id IS NOT NULL
    GROUP BY d.folder_id
)
UPDATE knowledge_folders f
SET embedding_model = COALESCE(f.embedding_model, m.embedding_model),
    vector_table_name = COALESCE(f.vector_table_name, m.vector_table_name)
FROM folder_model_from_docs m
WHERE f.id = m.folder_id;

UPDATE knowledge_folders f
SET embedding_model = COALESCE(NULLIF(f.embedding_model, ''),
                               (SELECT COALESCE(default_embedding_model, 'ollama:nomic-embed-text')
                                FROM knowledge_config
                                WHERE id = 1),
                               'ollama:nomic-embed-text')
WHERE f.embedding_model IS NULL OR f.embedding_model = '';

UPDATE knowledge_folders f
SET vector_table_name = CASE
    WHEN f.embedding_model = 'dashscope:text-embedding-v3' THEN 'vector_store_dashscope_v3'
    ELSE 'vector_store_ollama_nomic_768'
END
WHERE f.vector_table_name IS NULL OR f.vector_table_name = '';

ALTER TABLE knowledge_folders
    ALTER COLUMN embedding_model SET NOT NULL,
    ALTER COLUMN vector_table_name SET NOT NULL;

UPDATE url_import_jobs job
SET embedding_model = folder.embedding_model
FROM knowledge_folders folder
WHERE job.target_folder_id = folder.id
  AND (job.embedding_model IS NULL OR job.embedding_model = '');
