package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
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
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return vectorStore.similaritySearch(searchRequest);
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
        EmbeddingModel springAiModel = new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                return embeddingService.embedTexts(modelConfig.getId(), List.of(text)).get(0);
            }

            @Override
            public float[] embed(Document document) {
                return embed(document.getText());
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> embeddings = new ArrayList<>();
                int index = 0;
                for (String text : request.getInstructions()) {
                    float[] vector = embed(text);
                    embeddings.add(new Embedding(vector, index++));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public int dimensions() {
                return modelConfig.getDimensions();
            }
        };

        return new SimplePgVectorStore(dataSource, springAiModel, modelConfig.getVectorTable(), modelConfig.getDimensions());
    }

    private static class SimplePgVectorStore implements VectorStore {
        private final DataSource dataSource;
        private final EmbeddingModel embeddingModel;
        private final String tableName;
        private final int dimensions;

        public SimplePgVectorStore(DataSource dataSource,
                                   EmbeddingModel embeddingModel,
                                   String tableName,
                                   int dimensions) {
            this.dataSource = dataSource;
            this.embeddingModel = embeddingModel;
            this.tableName = tableName;
            this.dimensions = dimensions;
        }

        @Override
        public void add(List<Document> documents) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            for (Document doc : documents) {
                float[] embedding = embeddingModel.embed(doc);
                String embeddingStr = "[" + arrayToString(embedding) + "]";

                String metadataJson;
                try {
                    metadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(doc.getMetadata());
                } catch (Exception e) {
                    metadataJson = "{}";
                }

                String sql = String.format("""
                    INSERT INTO %s (id, content, metadata, embedding)
                    VALUES (?::uuid, ?, ?::jsonb, ?::vector)
                    ON CONFLICT (id) DO UPDATE SET
                        content = EXCLUDED.content,
                        metadata = EXCLUDED.metadata,
                        embedding = EXCLUDED.embedding
                    """, tableName);

                jdbcTemplate.update(sql, doc.getId(), doc.getText(), metadataJson, embeddingStr);
            }
        }

        @Override
        public void delete(List<String> idList) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String sql = String.format("DELETE FROM %s WHERE id = ?::uuid", tableName);
            for (String id : idList) {
                jdbcTemplate.update(sql, id);
            }
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            // Filter expression delete not implemented in this simple version
            log.warn("delete(Filter.Expression) not implemented for SimplePgVectorStore");
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            float[] queryEmbedding = embeddingModel.embed(request.getQuery());
            String embeddingStr = "[" + arrayToString(queryEmbedding) + "]";

            String sql = String.format("""
                SELECT id, content, metadata
                FROM %s
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, tableName);

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata");

                Map<String, Object> metadata = new java.util.HashMap<>();
                if (metadataJson != null) {
                    try {
                        metadata = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                                metadataJson,
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                        );
                    } catch (Exception e) {
                        // ignore
                    }
                }

                return new Document(id, content, metadata);
            }, embeddingStr, request.getTopK());
        }

        private String arrayToString(float[] array) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(array[i]);
            }
            return sb.toString();
        }
    }
}
