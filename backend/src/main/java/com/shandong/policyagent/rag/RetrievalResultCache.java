package com.shandong.policyagent.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 检索结果级缓存：缓存 query → 完整 Document 列表，命中后直接返回，跳过管线执行。
 * Redis 不可用或序列化失败时静默降级为不缓存，不影响检索主链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalResultCache {

    private static final String KEY_PREFIX = "rag:retrieval:";
    private static final int MAX_CACHED_DOCS = 50;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RagConfig ragConfig;

    /**
     * 构造缓存键。将检索上下文关键字段拼接后 SHA-256 哈希，确保不同参数组合产生不同键。
     */
    public String buildKey(String query,
                           String embeddingModelId,
                           String folderPath,
                           int topK,
                           double similarityThreshold) {
        RagConfig.Retrieval retrieval = ragConfig.getRetrieval();
        String composite = String.format(Locale.ROOT,
                "q=%s|e=%s|f=%s|k=%d|t=%.4f|ck=%d|re=%b|rp=%s|rm=%s|ree=%s",
                query != null ? query.trim() : "",
                embeddingModelId != null ? embeddingModelId : "",
                folderPath != null ? folderPath : "",
                topK,
                similarityThreshold,
                retrieval.getCandidateTopK(),
                retrieval.isRerankEnabled(),
                retrieval.getRerankProvider() != null ? retrieval.getRerankProvider() : "",
                retrieval.getRerankModel() != null ? retrieval.getRerankModel() : "",
                retrieval.getRerankEndpoint() != null ? retrieval.getRerankEndpoint() : ""
        );
        return KEY_PREFIX + sha256(composite);
    }

    /**
     * 从缓存读取检索结果。Redis 不可用或反序列化失败时返回 empty，调用方回退到实时检索。
     */
    public Optional<List<Document>> get(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<CachedDocument> cached = objectMapper.readValue(json,
                    new TypeReference<List<CachedDocument>>() {});
            if (cached == null || cached.isEmpty()) {
                return Optional.empty();
            }
            List<Document> documents = cached.stream()
                    .map(CachedDocument::toDocument)
                    .toList();
            log.debug("检索缓存命中 | key={} | docs={}", key, documents.size());
            return Optional.of(documents);
        } catch (Exception e) {
            log.debug("检索缓存读取失败，降级为实时检索 | key={} | error={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 将检索结果写入缓存。Redis 不可用或序列化失败时静默跳过，不影响主链路。
     * 仅缓存非空结果，且文档数不超过 {@link #MAX_CACHED_DOCS} 时缓存（避免大结果集占用内存）。
     */
    public void put(String key, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        if (documents.size() > MAX_CACHED_DOCS) {
            log.debug("检索结果过多，跳过缓存 | key={} | docs={}", key, documents.size());
            return;
        }
        try {
            List<CachedDocument> cached = documents.stream()
                    .map(CachedDocument::from)
                    .toList();
            String json = objectMapper.writeValueAsString(cached);
            int ttlMinutes = Math.max(1, ragConfig.getRetrieval().getCacheTtlMinutes());
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
            log.debug("检索结果已缓存 | key={} | docs={} | ttlMinutes={}", key, documents.size(), ttlMinutes);
        } catch (Exception e) {
            log.debug("检索缓存写入失败，不影响主链路 | key={} | error={}", key, e.getMessage());
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 缓存序列化专用记录，避免 Spring AI Document 序列化兼容性问题。
     */
    private record CachedDocument(String id, String text, Map<String, Object> metadata) {

        Map<String, Object> safeMetadata() {
            return metadata != null ? metadata : new HashMap<>();
        }

        static CachedDocument from(Document doc) {
            return new CachedDocument(
                    doc.getId(),
                    doc.getText(),
                    doc.getMetadata() != null ? new HashMap<>(doc.getMetadata()) : new HashMap<>()
            );
        }

        Document toDocument() {
            return new Document(id, text, safeMetadata());
        }
    }
}
