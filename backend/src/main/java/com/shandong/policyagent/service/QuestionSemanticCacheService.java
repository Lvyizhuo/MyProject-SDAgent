package com.shandong.policyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.rag.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionSemanticCacheService {

    private static final String HASH_KEY_PREFIX = "qa:q:hash:";
    private static final String BUCKET_KEY_PREFIX = "qa:q:bucket:";
    private static final String META_KEY_PREFIX = "qa:q:meta:";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;

    @Qualifier("chatAsyncTaskExecutor")
    private final Executor chatAsyncTaskExecutor;

    @Value("${app.qa-cache.enabled:true}")
    private boolean enabled;

    @Value("${app.qa-cache.ttl-minutes:1440}")
    private long ttlMinutes;

    @Value("${app.qa-cache.semantic-threshold:0.90}")
    private double semanticThreshold;

    @Value("${app.qa-cache.bucket-prefix-bits:14}")
    private int bucketPrefixBits;

    public Optional<CachedAnswer> lookup(String rawQuestion) {
        if (!enabled || rawQuestion == null || rawQuestion.isBlank()) {
            return Optional.empty();
        }

        String normalizedQuestion = normalizeQuestion(rawQuestion);
        String questionHash = sha256Hex(normalizedQuestion);

        QaCacheEntry exactEntry = getEntry(questionHash).orElse(null);
        if (exactEntry != null) {
            touchEntry(questionHash, exactEntry);
            return Optional.of(toCachedAnswer(exactEntry, 1.0));
        }

        float[] queryVector = embedQuestion(normalizedQuestion);
        if (queryVector == null || queryVector.length == 0) {
            return Optional.empty();
        }

        String bucketKey = BUCKET_KEY_PREFIX + buildSimhashPrefix(queryVector);
        Set<String> candidates = redisTemplate.opsForSet().members(bucketKey);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        QaCacheEntry bestEntry = null;
        String bestHash = null;
        double bestScore = 0.0;

        int inspected = 0;
        for (String candidateHash : candidates) {
            inspected++;
            if (inspected > 80) {
                break;
            }

            QaCacheEntry candidateEntry = getEntry(candidateHash).orElse(null);
            if (candidateEntry == null || candidateEntry.embeddingVector() == null || candidateEntry.embeddingVector().isBlank()) {
                continue;
            }

            float[] candidateVector = decodeVector(candidateEntry.embeddingVector());
            if (candidateVector.length == 0 || candidateVector.length != queryVector.length) {
                continue;
            }

            double score = cosineSimilarity(queryVector, candidateVector);
            if (score >= semanticThreshold && score > bestScore) {
                bestScore = score;
                bestEntry = candidateEntry;
                bestHash = candidateHash;
            }
        }

        if (bestEntry == null || bestHash == null) {
            return Optional.empty();
        }

        touchEntry(bestHash, bestEntry);
        return Optional.of(toCachedAnswer(bestEntry, bestScore));
    }

    public void saveAsync(String rawQuestion, ChatResponse response, String toolUsed) {
        if (!enabled || rawQuestion == null || rawQuestion.isBlank() || response == null) {
            return;
        }
        if (response.getContent() == null || response.getContent().isBlank()) {
            return;
        }
        if (shouldBypassCache(rawQuestion, response.getContent())) {
            return;
        }

        CompletableFuture.runAsync(() -> save(rawQuestion, response, toolUsed), chatAsyncTaskExecutor)
                .exceptionally(exception -> {
                    log.debug("问答缓存异步写入失败 | error={}", exception.getMessage());
                    return null;
                });
    }

    private void save(String rawQuestion, ChatResponse response, String toolUsed) {
        String normalizedQuestion = normalizeQuestion(rawQuestion);
        String questionHash = sha256Hex(normalizedQuestion);

        String embeddingModelId = null;
        String encodedVector = null;
        try {
            embeddingModelId = embeddingService.resolveDefaultModelId(null);
            List<float[]> vectors = embeddingService.embedTexts(embeddingModelId, List.of(normalizedQuestion));
            if (!vectors.isEmpty() && vectors.get(0) != null && vectors.get(0).length > 0) {
                encodedVector = encodeVector(vectors.get(0));
            }
        } catch (Exception exception) {
            log.debug("问答缓存向量生成失败，继续写入精确缓存 | error={}", exception.getMessage());
        }

        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        QaCacheEntry entry = new QaCacheEntry(
                questionHash,
                rawQuestion,
                normalizedQuestion,
                embeddingModelId,
                encodedVector,
                response.getContent(),
                response.getReferences() == null ? List.of() : response.getReferences(),
                toolUsed,
                now,
                now,
                0L
        );

        String hashKey = HASH_KEY_PREFIX + questionHash;
        try {
            redisTemplate.opsForValue().set(
                    hashKey,
                    objectMapper.writeValueAsString(entry),
                    Math.max(1L, ttlMinutes),
                    TimeUnit.MINUTES
            );

            if (encodedVector != null && !encodedVector.isBlank()) {
                float[] vector = decodeVector(encodedVector);
                String bucketKey = BUCKET_KEY_PREFIX + buildSimhashPrefix(vector);
                redisTemplate.opsForSet().add(bucketKey, questionHash);
                redisTemplate.expire(bucketKey, Math.max(1L, ttlMinutes), TimeUnit.MINUTES);
            }

            String metaKey = META_KEY_PREFIX + questionHash;
            redisTemplate.opsForHash().putAll(metaKey, Map.of(
                    "updatedAt", now,
                    "hitCount", "0",
                    "ttlMinutes", String.valueOf(Math.max(1L, ttlMinutes))
            ));
            redisTemplate.expire(metaKey, Math.max(1L, ttlMinutes), TimeUnit.MINUTES);
        } catch (Exception exception) {
            log.debug("问答缓存写入失败 | hash={} | error={}", questionHash, exception.getMessage());
        }
    }

    private void touchEntry(String questionHash, QaCacheEntry entry) {
        long nextHitCount = (entry.hitCount() == null ? 0L : entry.hitCount()) + 1;
        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        QaCacheEntry touched = new QaCacheEntry(
                entry.questionHash(),
                entry.rawQuestion(),
                entry.normalizedQuestion(),
                entry.embeddingModelId(),
                entry.embeddingVector(),
                entry.answer(),
                entry.references(),
                entry.toolUsed(),
                entry.createdAt(),
                now,
                nextHitCount
        );

        try {
            String hashKey = HASH_KEY_PREFIX + questionHash;
            redisTemplate.opsForValue().set(
                    hashKey,
                    objectMapper.writeValueAsString(touched),
                    Math.max(1L, ttlMinutes),
                    TimeUnit.MINUTES
            );

            String metaKey = META_KEY_PREFIX + questionHash;
            redisTemplate.opsForHash().put(metaKey, "updatedAt", now);
            redisTemplate.opsForHash().put(metaKey, "hitCount", String.valueOf(nextHitCount));
            redisTemplate.expire(metaKey, Math.max(1L, ttlMinutes), TimeUnit.MINUTES);
        } catch (Exception exception) {
            log.debug("更新问答缓存命中次数失败 | hash={} | error={}", questionHash, exception.getMessage());
        }
    }

    private Optional<QaCacheEntry> getEntry(String questionHash) {
        try {
            String raw = redisTemplate.opsForValue().get(HASH_KEY_PREFIX + questionHash);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            QaCacheEntry entry = objectMapper.readValue(raw, QaCacheEntry.class);
            if (entry.answer() == null || entry.answer().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(entry);
        } catch (Exception exception) {
            log.debug("读取问答缓存失败 | hash={} | error={}", questionHash, exception.getMessage());
            return Optional.empty();
        }
    }

    private float[] embedQuestion(String normalizedQuestion) {
        try {
            String embeddingModelId = embeddingService.resolveDefaultModelId(null);
            List<float[]> vectors = embeddingService.embedTexts(embeddingModelId, List.of(normalizedQuestion));
            if (vectors.isEmpty() || vectors.get(0) == null) {
                return new float[0];
            }
            return vectors.get(0);
        } catch (Exception exception) {
            log.debug("查询问题向量化失败 | error={}", exception.getMessage());
            return new float[0];
        }
    }

    private CachedAnswer toCachedAnswer(QaCacheEntry entry, double score) {
        return new CachedAnswer(
                entry.answer(),
                entry.references() == null ? List.of() : entry.references(),
                score,
                entry.questionHash()
        );
    }

    private String buildSimhashPrefix(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "0";
        }

        int bits = Math.max(8, Math.min(32, bucketPrefixBits));
        long hash = 1469598103934665603L;
        for (int index = 0; index < vector.length; index++) {
            int component = Float.floatToIntBits(vector[index]);
            long mixed = ((long) component << (index % 13)) ^ ((long) index * 1099511628211L);
            hash ^= mixed;
            hash *= 1099511628211L;
        }

        long mask = (1L << bits) - 1L;
        long prefix = hash & mask;
        return Long.toHexString(prefix);
    }

    private String encodeVector(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private float[] decodeVector(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new float[0];
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length % Float.BYTES != 0) {
                return new float[0];
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            float[] vector = new float[bytes.length / Float.BYTES];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = buffer.getFloat();
            }
            return vector;
        } catch (Exception exception) {
            return new float[0];
        }
    }

    private double cosineSimilarity(float[] left, float[] right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;

        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private boolean shouldBypassCache(String rawQuestion, String answer) {
        String normalizedQuestion = normalizeQuestion(rawQuestion);
        String normalizedAnswer = answer.toLowerCase(Locale.ROOT);

        if (normalizedAnswer.contains("暂时不可用") || normalizedAnswer.contains("稍后重试")) {
            return true;
        }

        return normalizedQuestion.contains("最新")
                || normalizedQuestion.contains("实时")
                || normalizedQuestion.contains("今日")
                || normalizedQuestion.contains("新闻")
                || normalizedQuestion.contains("动态")
                || normalizedQuestion.contains("价格");
    }

    private String normalizeQuestion(String rawQuestion) {
        return rawQuestion == null
                ? ""
                : rawQuestion.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate SHA-256 hash", exception);
        }
    }

    private record QaCacheEntry(
            String questionHash,
            String rawQuestion,
            String normalizedQuestion,
            String embeddingModelId,
            String embeddingVector,
            String answer,
            List<ChatResponse.Reference> references,
            String toolUsed,
            String createdAt,
            String updatedAt,
            Long hitCount
    ) {
    }

    public record CachedAnswer(
            String answer,
            List<ChatResponse.Reference> references,
            double similarity,
            String questionHash
    ) {
    }
}