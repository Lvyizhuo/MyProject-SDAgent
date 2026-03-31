package com.shandong.policyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 商品价格缓存服务：按“型号/品类”维度缓存价格，供补贴计算场景复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPriceCacheService {

    private static final String KEY_PREFIX = "chat:price-cache:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Qualifier("chatAsyncTaskExecutor")
    private final Executor chatAsyncTaskExecutor;

    @Value("${app.price-cache.ttl-minutes:1440}")
    private long ttlMinutes;

    public Optional<Double> lookupPrice(SessionFactCacheService.SessionFacts facts) {
        if (facts == null) {
            return Optional.empty();
        }

        for (String key : buildLookupKeys(facts)) {
            try {
                String payload = redisTemplate.opsForValue().get(key);
                if (payload == null || payload.isBlank()) {
                    continue;
                }
                PriceCacheEntry entry = objectMapper.readValue(payload, PriceCacheEntry.class);
                if (entry.price() != null && entry.price() > 0) {
                    return Optional.of(entry.price());
                }
            } catch (Exception exception) {
                log.debug("读取商品价格缓存失败 | key={} | error={}", key, exception.getMessage());
            }
        }

        return Optional.empty();
    }

    public void cachePriceAsync(SessionFactCacheService.SessionFacts facts,
                                String query,
                                String source) {
        if (facts == null || facts.getLatestPrice() == null || facts.getLatestPrice() <= 0) {
            return;
        }

        List<String> keys = buildLookupKeys(facts);
        if (keys.isEmpty()) {
            return;
        }

        PriceCacheEntry entry = new PriceCacheEntry(
                pickLast(facts.getCategories()),
                pickLast(facts.getDeviceModels()),
                facts.getLatestPrice(),
                source == null ? "unknown" : source,
                query,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        CompletableFuture.runAsync(() -> save(keys, entry), chatAsyncTaskExecutor)
                .exceptionally(exception -> {
                    log.debug("异步写入商品价格缓存失败 | error={}", exception.getMessage());
                    return null;
                });
    }

    private void save(List<String> keys, PriceCacheEntry entry) {
        try {
            String payload = objectMapper.writeValueAsString(entry);
            for (String key : keys) {
                redisTemplate.opsForValue().set(
                        key,
                        payload,
                        Math.max(30L, ttlMinutes),
                        TimeUnit.MINUTES
                );
            }
        } catch (Exception exception) {
            log.debug("写入商品价格缓存失败 | keys={} | error={}", keys, exception.getMessage());
        }
    }

    private List<String> buildLookupKeys(SessionFactCacheService.SessionFacts facts) {
        Set<String> keys = new LinkedHashSet<>();

        List<String> categories = new ArrayList<>();
        if (facts.getCategories() != null) {
            categories.addAll(facts.getCategories());
        }

        if (facts.getDeviceModels() != null) {
            for (String model : facts.getDeviceModels()) {
                String normalizedModel = normalizeKey(model);
                if (normalizedModel.isBlank()) {
                    continue;
                }
                keys.add(KEY_PREFIX + "model:" + normalizedModel);
                for (String category : categories) {
                    String normalizedCategory = normalizeKey(category);
                    if (!normalizedCategory.isBlank()) {
                        keys.add(KEY_PREFIX + "model:" + normalizedModel + ":category:" + normalizedCategory);
                    }
                }
            }
        }

        for (String category : categories) {
            String normalizedCategory = normalizeKey(category);
            if (!normalizedCategory.isBlank()) {
                keys.add(KEY_PREFIX + "category:" + normalizedCategory);
            }
        }

        return new ArrayList<>(keys);
    }

    private String pickLast(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String last = null;
        for (String value : values) {
            last = value;
        }
        return last;
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\p{IsHan}a-z0-9-]", "");
    }

    private record PriceCacheEntry(
            String category,
            String model,
            Double price,
            String source,
            String query,
            String cachedAt
    ) {
    }
}
