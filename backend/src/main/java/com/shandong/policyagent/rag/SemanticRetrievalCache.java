package com.shandong.policyagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 语义化检索缓存索引。
 *
 * <p><b>要解决的问题</b>：检索结果缓存原本以「查询原文」为键，
 * 而政策咨询类问题的措辞差异极大（"…补贴政策是什么？" / "…补贴标准" / "山东汽车以旧换新补贴"），
 * 纯文本键的命中率接近 0，等于每轮都要付一次完整的 RAG 管线成本（实测 rerank 5.8s）。
 *
 * <p><b>做法</b>：把最近 N 个问法及其查询向量登记在 Redis 里；
 * 新问法到来时用同一份查询向量做余弦相似度匹配，命中阈值即复用当时那条问法的缓存结果。
 *
 * <p><b>成本</b>：查询向量由 {@code HybridRetrievalPipeline} 统一计算一次后复用
 * （向量召回与缓存匹配共用），因此语义匹配本身不产生额外的嵌入调用。
 *
 * <p>Redis 不可用时全部静默降级为「不匹配」，不影响检索主链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticRetrievalCache {

    /** 索引条目（问法 → 缓存键 + 查询向量）。 */
    private static final String INDEX_KEY = "rag:retrieval:semantic:index";
    /** 最多保留的最近问法数量。 */
    private static final int MAX_ENTRIES = 128;

    private final StringRedisTemplate redisTemplate;

    @Value("${app.rag.retrieval.semantic-cache-enabled:false}")
    private boolean enabled;

    @Value("${app.rag.retrieval.semantic-cache-threshold:0.92}")
    private double threshold;

    /**
     * 一条索引记录。
     *
     * @param query    原始问法（仅用于日志排查）
     * @param cacheKey 该问法对应的精确缓存键
     * @param vector   查询向量（float32 小端字节的 Base64）
     */
    record Entry(String query, String cacheKey, String vector) {
    }

    /**
     * 在索引中查找与给定向量最相似、且相似度达到阈值的问法。
     *
     * @return 命中的精确缓存键；未命中返回 empty
     */
    public Optional<String> findSimilarKey(float[] queryEmbedding) {
        if (!enabled || queryEmbedding == null || queryEmbedding.length == 0) {
            return Optional.empty();
        }
        try {
            List<String> rawEntries = redisTemplate.opsForList().range(INDEX_KEY, 0, MAX_ENTRIES - 1);
            if (rawEntries == null || rawEntries.isEmpty()) {
                return Optional.empty();
            }
            String bestKey = null;
            double bestScore = 0;
            for (String raw : rawEntries) {
                Entry entry = parse(raw);
                if (entry == null) {
                    continue;
                }
                float[] candidate = decodeVector(entry.vector());
                if (candidate == null || candidate.length != queryEmbedding.length) {
                    continue;
                }
                double score = cosineSimilarity(queryEmbedding, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestKey = entry.cacheKey();
                }
            }
            if (bestKey != null && bestScore >= threshold) {
                log.debug("语义化检索缓存命中 | similarity={} | key={}", String.format("%.4f", bestScore), bestKey);
                return Optional.of(bestKey);
            }
            log.debug("语义化检索缓存未命中 | bestSimilarity={} | threshold={}",
                    String.format("%.4f", bestScore), threshold);
            return Optional.empty();
        } catch (Exception e) {
            log.debug("语义化检索缓存读取失败，降级为不匹配 | error={}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 登记一条问法及其缓存键。
     */
    public void register(String query, float[] queryEmbedding, String cacheKey) {
        if (!enabled || query == null || query.isBlank() || cacheKey == null
                || queryEmbedding == null || queryEmbedding.length == 0) {
            return;
        }
        try {
            Entry entry = new Entry(query, cacheKey, encodeVector(queryEmbedding));
            redisTemplate.opsForList().rightPush(INDEX_KEY, serialize(entry));
            // 修剪：只保留最近 MAX_ENTRIES 条
            redisTemplate.opsForList().trim(INDEX_KEY, 0, MAX_ENTRIES - 1);
        } catch (Exception e) {
            log.debug("语义化检索缓存登记失败，不影响主链路 | error={}", e.getMessage());
        }
    }

    /**
     * 清理索引（管理台改动检索配置后可调用，避免旧配置下的条目污染匹配）。
     */
    public void clear() {
        try {
            redisTemplate.delete(INDEX_KEY);
        } catch (Exception e) {
            log.debug("语义化检索缓存清理失败 | error={}", e.getMessage());
        }
    }

    // ==================== 序列化 ====================

    private String serialize(Entry entry) {
        return entry.query() + "\t" + entry.cacheKey() + "\t" + entry.vector();
    }

    private Entry parse(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\t", 3);
        if (parts.length < 3) {
            return null;
        }
        return new Entry(parts[0], parts[1], parts[2]);
    }

    private String encodeVector(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private float[] decodeVector(String encoded) {
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            float[] vector = new float[raw.length / 4];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = buffer.getFloat();
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 余弦相似度。向量已由嵌入模型归一化，这里仍然做一次归一化以保证数值稳定。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA <= 0 || normB <= 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 仅供测试与排查使用。 */
    public List<String> recentQueries() {
        try {
            List<String> rawEntries = redisTemplate.opsForList().range(INDEX_KEY, 0, MAX_ENTRIES - 1);
            List<String> queries = new ArrayList<>();
            if (rawEntries != null) {
                for (String raw : rawEntries) {
                    Entry entry = parse(raw);
                    if (entry != null) {
                        queries.add(entry.query());
                    }
                }
            }
            return queries;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 便于排查的字节长度换算。 */
    static int estimatedBytes(float[] vector) {
        return vector.length * 4;
    }

    static String utf8(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
