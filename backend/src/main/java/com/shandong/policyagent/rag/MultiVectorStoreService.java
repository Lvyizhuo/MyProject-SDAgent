package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.model.dto.DocumentChunkResponse;
import com.shandong.policyagent.model.dto.DocumentChunksPageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiVectorStoreService {
    private static final int HNSW_MAX_DIMENSIONS = 2000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DataSource dataSource;
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

    public int deleteDocumentChunks(String tableName, Long documentId) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String safeTableName = sanitizeTableName(tableName);

        if (!tableExists(jdbcTemplate, safeTableName)) {
            log.info("Skip deleting chunks for document {} because vector table {} does not exist", documentId, safeTableName);
            return 0;
        }

        String sql = String.format("""
                DELETE FROM %s
                WHERE metadata->>'knowledgeDocumentId' = ?
                """, safeTableName);
        int deleted = jdbcTemplate.update(sql, String.valueOf(documentId));
        log.info("Deleted {} chunks for knowledge document {} from table {}", deleted, documentId, safeTableName);
        return deleted;
    }

    public List<Document> similaritySearch(String modelId, String query, int topK) {
        VectorStore vectorStore = getVectorStore(modelId);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    public List<Document> similaritySearchInFolderScope(String modelId, String query, int topK, String folderPath) {
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(modelId);
        initializeVectorTable(modelConfig.getVectorTable(), modelConfig.getDimensions());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String safeTableName = sanitizeTableName(modelConfig.getVectorTable());
        float[] queryEmbedding = embeddingService.embedTexts(modelId, List.of(query)).get(0);
        String embeddingStr = "[" + arrayToString(queryEmbedding) + "]";
        String normalizedFolderPath = normalizeFolderPath(folderPath);

        String sql = String.format("""
            SELECT v.id, v.content, v.metadata
            FROM %s v
            JOIN knowledge_documents d
              ON d.id = CAST(v.metadata->>'knowledgeDocumentId' AS BIGINT)
            LEFT JOIN knowledge_folders f
              ON f.id = d.folder_id
            WHERE COALESCE(f.path, '/') = ?
               OR COALESCE(f.path, '/') LIKE ?
            ORDER BY v.embedding <=> ?::vector
            LIMIT ?
            """, safeTableName);

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> mapDocument(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                normalizedFolderPath,
                buildFolderDescendantPattern(normalizedFolderPath),
                embeddingStr,
                topK);
    }

    public List<Document> similaritySearch(String modelId,
                                           String query,
                                           int topK,
                                           String folderPath,
                                           String chunkLevel) {
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(modelId);
        initializeVectorTable(modelConfig.getVectorTable(), modelConfig.getDimensions());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String safeTableName = sanitizeTableName(modelConfig.getVectorTable());
        float[] queryEmbedding = embeddingService.embedTexts(modelId, List.of(query)).get(0);
        String embeddingStr = "[" + arrayToString(queryEmbedding) + "]";

        String normalizedFolderPath = normalizeFolderPath(folderPath);
        boolean hasFolderScope = normalizedFolderPath != null && !normalizedFolderPath.isBlank() && !"/".equals(normalizedFolderPath);
        boolean hasChunkLevel = chunkLevel != null && !chunkLevel.isBlank();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT v.id, v.content, v.metadata FROM ")
                .append(safeTableName)
                .append(" v ");

        List<Object> params = new ArrayList<>();

        if (hasFolderScope) {
            sql.append("JOIN knowledge_documents d ON d.id = CAST(v.metadata->>'knowledgeDocumentId' AS BIGINT) ")
                    .append("LEFT JOIN knowledge_folders f ON f.id = d.folder_id ");
        }

        sql.append("WHERE 1=1 ");

        if (hasFolderScope) {
            sql.append("AND (COALESCE(f.path, '/') = ? OR COALESCE(f.path, '/') LIKE ?) ");
            params.add(normalizedFolderPath);
            params.add(buildFolderDescendantPattern(normalizedFolderPath));
        }

        if (hasChunkLevel) {
            sql.append("AND COALESCE(v.metadata->>'chunkLevel', '') = ? ");
            params.add(chunkLevel);
        }

        sql.append("ORDER BY v.embedding <=> ?::vector LIMIT ?");
        params.add(embeddingStr);
        params.add(Math.max(1, topK));

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> mapDocument(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                params.toArray()
        );
    }

    public List<Document> keywordSearch(String modelId,
                                        String query,
                                        int topK,
                                        String folderPath,
                                        String chunkLevel) {
        String normalizedQuery = normalizeKeywordQuery(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(modelId);
        initializeVectorTable(modelConfig.getVectorTable(), modelConfig.getDimensions());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String safeTableName = sanitizeTableName(modelConfig.getVectorTable());
        String normalizedFolderPath = normalizeFolderPath(folderPath);
        boolean hasFolderScope = normalizedFolderPath != null && !normalizedFolderPath.isBlank() && !"/".equals(normalizedFolderPath);
        boolean hasChunkLevel = chunkLevel != null && !chunkLevel.isBlank();
        String likePattern = "%" + normalizedQuery + "%";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT v.id, v.content, v.metadata, ")
                .append("ts_rank_cd(to_tsvector('simple', COALESCE(v.content, '')), plainto_tsquery('simple', ?)) AS bm25_score ")
                .append("FROM ")
                .append(safeTableName)
                .append(" v ");

        List<Object> params = new ArrayList<>();
        params.add(normalizedQuery);

        if (hasFolderScope) {
            sql.append("JOIN knowledge_documents d ON d.id = CAST(v.metadata->>'knowledgeDocumentId' AS BIGINT) ")
                    .append("LEFT JOIN knowledge_folders f ON f.id = d.folder_id ");
        }

        sql.append("WHERE 1=1 ");

        if (hasFolderScope) {
            sql.append("AND (COALESCE(f.path, '/') = ? OR COALESCE(f.path, '/') LIKE ?) ");
            params.add(normalizedFolderPath);
            params.add(buildFolderDescendantPattern(normalizedFolderPath));
        }

        if (hasChunkLevel) {
            sql.append("AND COALESCE(v.metadata->>'chunkLevel', '') = ? ");
            params.add(chunkLevel);
        }

        sql.append("AND (")
                .append("to_tsvector('simple', COALESCE(v.content, '')) @@ plainto_tsquery('simple', ?) ")
                .append("OR v.content ILIKE ?")
                .append(") ")
                .append("ORDER BY bm25_score DESC NULLS LAST, LENGTH(COALESCE(v.content, '')) ASC ")
                .append("LIMIT ?");

        params.add(normalizedQuery);
        params.add(likePattern);
        params.add(Math.max(1, topK));

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> mapDocument(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                params.toArray()
        );
    }

    public Map<String, Document> loadDocumentsByIds(String tableName, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Map.of();
        }

        String safeTableName = sanitizeTableName(tableName);
        List<String> validIds = chunkIds.stream()
                .filter(id -> {
                    try {
                        UUID.fromString(id);
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .distinct()
                .toList();

        if (validIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(", ", java.util.Collections.nCopies(validIds.size(), "?::uuid"));
        String sql = String.format("SELECT id, content, metadata FROM %s WHERE id IN (%s)", safeTableName, placeholders);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<Document> documents = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapDocument(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                validIds.toArray()
        );

        Map<String, Document> result = new LinkedHashMap<>();
        for (Document document : documents) {
            result.put(document.getId(), document);
        }
        return result;
    }

    public DocumentChunksPageResponse listDocumentChunks(String tableName,
                                                         Long documentId,
                                                         int page,
                                                         int size,
                                                         String chunkLevel) {
        String safeTableName = sanitizeTableName(tableName);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, size);
        int offset = safePage * safeSize;
        String normalizedChunkLevel = normalizeChunkLevel(chunkLevel);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String countSql = normalizedChunkLevel == null
                ? String.format("""
                SELECT COUNT(*)
                FROM %s
                WHERE metadata->>'knowledgeDocumentId' = ?
                """, safeTableName)
                : String.format("""
                SELECT COUNT(*)
                FROM %s
                WHERE metadata->>'knowledgeDocumentId' = ?
                  AND COALESCE(metadata->>'chunkLevel', '') = ?
                """, safeTableName);

        Long totalElements = normalizedChunkLevel == null
                ? jdbcTemplate.queryForObject(countSql, Long.class, String.valueOf(documentId))
                : jdbcTemplate.queryForObject(countSql, Long.class, String.valueOf(documentId), normalizedChunkLevel);
        if (totalElements == null) {
            totalElements = 0L;
        }

        String querySql = normalizedChunkLevel == null
                ? String.format("""
                SELECT id, content, metadata
                FROM %s
                WHERE metadata->>'knowledgeDocumentId' = ?
                ORDER BY COALESCE((metadata->>'chunkIndex')::int, 2147483647), id
                LIMIT ? OFFSET ?
                """, safeTableName)
                : String.format("""
                SELECT id, content, metadata
                FROM %s
                WHERE metadata->>'knowledgeDocumentId' = ?
                  AND COALESCE(metadata->>'chunkLevel', '') = ?
                ORDER BY COALESCE((metadata->>'chunkIndex')::int, 2147483647), id
                LIMIT ? OFFSET ?
                """, safeTableName);

        List<DocumentChunkResponse> chunks = normalizedChunkLevel == null
                ? jdbcTemplate.query(querySql, (rs, rowNum) -> toChunkResponse(rs.getString("id"),
                rs.getString("content"), rs.getString("metadata")), String.valueOf(documentId), safeSize, offset)
                : jdbcTemplate.query(querySql, (rs, rowNum) -> toChunkResponse(rs.getString("id"),
                rs.getString("content"), rs.getString("metadata")),
                String.valueOf(documentId), normalizedChunkLevel, safeSize, offset);

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);

        return DocumentChunksPageResponse.builder()
                .page(safePage)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .content(chunks)
                .build();
    }

    private String normalizeChunkLevel(String chunkLevel) {
        if (chunkLevel == null || chunkLevel.isBlank()) {
            return "parent";
        }
        String normalized = chunkLevel.trim().toLowerCase(Locale.ROOT);
        if ("parent".equals(normalized) || "child".equals(normalized)) {
            return normalized;
        }
        if ("all".equals(normalized)) {
            return null;
        }
        return "parent";
    }

    private DocumentChunkResponse toChunkResponse(String id, String content, String metadataJson) {
        Map<String, Object> metadata = parseMetadata(metadataJson);
        Integer chunkIndex = getIntValue(metadata.get("chunkIndex"));
        Integer chunkChars = getIntValue(metadata.get("chunkChars"));
        if (chunkChars == null) {
            chunkChars = content == null ? 0 : content.length();
        }

        return DocumentChunkResponse.builder()
                .chunkId(id)
                .chunkIndex(chunkIndex)
                .chunkChars(chunkChars)
                .splitStrategy(stringValue(metadata.get("splitStrategy")))
                .content(content)
                .build();
    }

    public String loadChunkExcerpt(String tableName, Long documentId, Integer chunkIndex, int radius) {
        if (tableName == null || documentId == null || chunkIndex == null || chunkIndex < 1) {
            return null;
        }

        String safeTableName = sanitizeTableName(tableName);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        if (!tableExists(jdbcTemplate, safeTableName)) {
            return null;
        }

        int safeRadius = Math.max(radius, 0);
        int startIndex = Math.max(1, chunkIndex - safeRadius);
        int endIndex = chunkIndex + safeRadius;

        String querySql = String.format("""
                SELECT content
                FROM %s
                WHERE metadata->>'knowledgeDocumentId' = ?
                  AND COALESCE((metadata->>'chunkIndex')::int, 2147483647) BETWEEN ? AND ?
                ORDER BY COALESCE((metadata->>'chunkIndex')::int, 2147483647), id
                """, safeTableName);

        List<String> contents = jdbcTemplate.query(
                querySql,
                (rs, rowNum) -> rs.getString("content"),
                String.valueOf(documentId),
                startIndex,
                endIndex
        );
        if (contents.isEmpty()) {
            return null;
        }
        return contents.stream()
                .filter(content -> content != null && !content.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(null);
    }

    public List<Document> loadParentChunkWindow(String tableName,
                                                Long documentId,
                                                Integer parentChunkIndex,
                                                int radius) {
        if (tableName == null || documentId == null || parentChunkIndex == null || parentChunkIndex < 1) {
            return List.of();
        }

        String safeTableName = sanitizeTableName(tableName);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        if (!tableExists(jdbcTemplate, safeTableName)) {
            return List.of();
        }

        int safeRadius = Math.max(0, radius);
        int startIndex = Math.max(1, parentChunkIndex - safeRadius);
        int endIndex = parentChunkIndex + safeRadius;

        String querySql = String.format("""
                SELECT id, content, metadata
                FROM %s
                WHERE metadata->>'knowledgeDocumentId' = ?
                  AND COALESCE(metadata->>'chunkLevel', '') = 'parent'
                  AND COALESCE((metadata->>'parentChunkIndex')::int, 2147483647) BETWEEN ? AND ?
                ORDER BY COALESCE((metadata->>'parentChunkIndex')::int, 2147483647), id
                """, safeTableName);

        return jdbcTemplate.query(
                querySql,
                (rs, rowNum) -> mapDocument(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                String.valueOf(documentId),
                startIndex,
                endIndex
        );
    }

    private void initializeVectorTable(String tableName, int dimensions) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Boolean tableExists = tableExists(jdbcTemplate, tableName);

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

            if (dimensions <= HNSW_MAX_DIMENSIONS) {
                String createIndexSql = String.format("""
                    CREATE INDEX ON %s USING hnsw (embedding vector_cosine_ops)
                    """, tableName);
                jdbcTemplate.execute(createIndexSql);
            } else {
                log.warn("Skip HNSW index for table {}: dimensions {} exceed pgvector HNSW limit {}",
                        tableName, dimensions, HNSW_MAX_DIMENSIONS);
            }

            log.info("Vector table created: {}", tableName);
            return;
        }

        Integer existingDimensions = getExistingVectorDimensions(jdbcTemplate, tableName);
        if (existingDimensions == null) {
            throw new IllegalStateException("Vector table exists but embedding column is missing: " + tableName);
        }
        if (!existingDimensions.equals(dimensions)) {
            throw new IllegalStateException(String.format(
                    "Vector table dimension mismatch: table=%s, expected=%d, actual=%d",
                    tableName, dimensions, existingDimensions
            ));
        }
    }

    private Boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        String checkTableSql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)";
        return jdbcTemplate.queryForObject(checkTableSql, Boolean.class, tableName);
    }

    private Integer getExistingVectorDimensions(JdbcTemplate jdbcTemplate, String tableName) {
        String sql = """
                SELECT CAST(
                    NULLIF(
                        REGEXP_REPLACE(format_type(a.atttypid, a.atttypmod), '^vector\\((\\d+)\\)$', '\\1'),
                        format_type(a.atttypid, a.atttypmod)
                    ) AS INTEGER
                )
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relname = ?
                  AND a.attname = 'embedding'
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                """;
        List<Integer> rows = jdbcTemplate.query(sql, (rs, rowNum) -> (Integer) rs.getObject(1), tableName);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
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

    private String sanitizeTableName(String tableName) {
        if (tableName == null || !tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid vector table name");
        }
        return tableName;
    }

    private String normalizeFolderPath(String folderPath) {
        if (folderPath == null || folderPath.isBlank() || "/".equals(folderPath.trim())) {
            return "/";
        }
        String normalized = folderPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String buildFolderDescendantPattern(String folderPath) {
        return "/".equals(folderPath) ? "/%" : folderPath + "/%";
    }

    private String normalizeKeywordQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim()
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Document mapDocument(String id, String content, String metadataJson) {
        return new Document(id, content, parseMetadata(metadataJson));
    }

    private static String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(array[i]);
        }
        return sb.toString();
    }

    private static Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new java.util.HashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    private Integer getIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
                if (embedding.length != dimensions) {
                    throw new IllegalStateException(String.format(
                            "Embedding dimension mismatch before insert: table=%s, expected=%d, actual=%d",
                            tableName, dimensions, embedding.length
                    ));
                }
                String embeddingStr = "[" + MultiVectorStoreService.arrayToString(embedding) + "]";

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
            String embeddingStr = "[" + MultiVectorStoreService.arrayToString(queryEmbedding) + "]";

            String sql = String.format("""
                SELECT id, content, metadata
                FROM %s
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, tableName);

            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> mapDocument(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                    embeddingStr,
                    request.getTopK());
        }
    }
}
