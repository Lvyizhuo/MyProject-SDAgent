package com.shandong.policyagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiVectorStoreService {

    private final DataSource dataSource;
    private final EmbeddingModelConfig embeddingModelConfig;
    private final EmbeddingService embeddingService;

    private final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    public VectorStore getVectorStore(String modelId) {
        return vectorStoreCache.computeIfAbsent(modelId, id -> {
            EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(id);
            initializeVectorTable(modelConfig.getVectorTable(), modelConfig.getDimensions());
            return createVectorStore(modelConfig);
        });
    }

    public void addDocuments(String modelId, List<Document> documents) {
        VectorStore vectorStore = getVectorStore(modelId);
        vectorStore.add(documents);
        log.info("Added {} documents to vector store for model: {}", documents.size(), modelId);
    }

    public void deleteDocuments(String modelId, List<String> documentIds) {
        VectorStore vectorStore = getVectorStore(modelId);
        vectorStore.delete(documentIds);
        log.info("Deleted {} documents from vector store for model: {}", documentIds.size(), modelId);
    }

    public List<Document> similaritySearch(String modelId, String query, int topK) {
        VectorStore vectorStore = getVectorStore(modelId);
        return vectorStore.similaritySearch(query, topK);
    }

    private void initializeVectorTable(String tableName, int dimensions) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String checkTableSql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)";
        Boolean tableExists = jdbcTemplate.queryForObject(checkTableSql, Boolean.class, tableName);

        if (Boolean.FALSE.equals(tableExists)) {
            log.info("Creating vector table: {} with dimensions: {}", tableName, dimensions);
            String createTableSql = String.format("""
                CREATE TABLE %s (
                    id UUID PRIMARY KEY,
                    content TEXT,
                    metadata JSONB,
                    embedding vector(%d)
                )
                """, tableName, dimensions);
            jdbcTemplate.execute(createTableSql);

            String createIndexSql = String.format("""
                CREATE INDEX ON %s USING hnsw (embedding vector_cosine_ops)
                """, tableName);
            jdbcTemplate.execute(createIndexSql);

            log.info("Vector table created: {}", tableName);
        }
    }

    private VectorStore createVectorStore(EmbeddingModelConfig.EmbeddingModel modelConfig) {
        org.springframework.ai.embedding.EmbeddingModel springAiModel;
        if ("dashscope".equalsIgnoreCase(modelConfig.getProvider())) {
            springAiModel = embeddingService.getSpringAiEmbeddingModel(modelConfig.getId());
        } else {
            springAiModel = new org.springframework.ai.embedding.EmbeddingModel() {
                @Override
                public float[] embed(String text) {
                    return embeddingService.embedTexts(modelConfig.getId(), List.of(text)).get(0);
                }

                @Override
                public int dimensions() {
                    return modelConfig.getDimensions();
                }
            };
        }

        return PgVectorStore.builder(dataSource)
                .embeddingModel(springAiModel)
                .dimensions(modelConfig.getDimensions())
                .tableName(modelConfig.getVectorTable())
                .initializeSchema(false)
                .build();
    }
}
