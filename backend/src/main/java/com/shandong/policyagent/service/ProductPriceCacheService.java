package com.shandong.policyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.tool.WebSearchTool;
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
    private final WebSearchTool webSearchTool;
    private final SessionFactCacheService sessionFactCacheService;

    @Qualifier("chatAsyncTaskExecutor")
    private final Executor chatAsyncTaskExecutor;

    @Value("${app.price-cache.ttl-minutes:1440}")
    private long ttlMinutes;

    @Value("${app.price-cache.prefetch-max-results:3}")
    private int prefetchMaxResults;

    public void prefetchPriceAsync(String rawQuestion,
                                   SessionFactCacheService.SessionFacts facts) {
        if (rawQuestion == null || rawQuestion.isBlank() || !hasLookupAnchor(facts, rawQuestion)) {
            return;
        }

        CompletableFuture.runAsync(() -> doPrefetch(rawQuestion, facts), chatAsyncTaskExecutor)
                .exceptionally(exception -> {
                    log.warn("价格预取失败 | query={} | error={}", rawQuestion, exception.getMessage());
                    return null;
                });
    }

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

    private void doPrefetch(String rawQuestion,
                            SessionFactCacheService.SessionFacts facts) {
        String searchQuery = buildPriceSearchQuery(rawQuestion, facts);
        log.info("价格预取开始 | query={}", searchQuery);

        WebSearchTool.SearchResponse response = webSearchTool.webSearch()
                .apply(new WebSearchTool.SearchRequest(searchQuery, prefetchMaxResults));
        Optional<Double> price = extractPriceFromResponse(response);
        if (price.isEmpty()) {
            log.info("价格预取未提取到有效价格 | query={}", searchQuery);
            return;
        }

        SessionFactCacheService.SessionFacts prefetchFacts = buildPrefetchFacts(rawQuestion, facts, response, price.get());
        cachePriceAsync(prefetchFacts, searchQuery, "prefetch");
        log.info("价格预取成功 | query={} | price={}", searchQuery, price.get());
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

    private SessionFactCacheService.SessionFacts buildPrefetchFacts(String rawQuestion,
                                                                     SessionFactCacheService.SessionFacts sourceFacts,
                                                                     WebSearchTool.SearchResponse response,
                                                                     Double price) {
        String seedText = (rawQuestion == null ? "" : rawQuestion) + "\n" + flattenResponse(response);
        SessionFactCacheService.SessionFacts prefetchFacts = sessionFactCacheService.extractFactsFromText(seedText);
        if (sourceFacts != null) {
            prefetchFacts.getCategories().addAll(sourceFacts.getCategories());
            prefetchFacts.getDeviceModels().addAll(sourceFacts.getDeviceModels());
            if ((prefetchFacts.getBrand() == null || prefetchFacts.getBrand().isBlank())
                    && sourceFacts.getBrand() != null) {
                prefetchFacts.setBrand(sourceFacts.getBrand());
            }
            if ((prefetchFacts.getModel() == null || prefetchFacts.getModel().isBlank())
                    && sourceFacts.getModel() != null) {
                prefetchFacts.setModel(sourceFacts.getModel());
            }
        }
        prefetchFacts.setLatestPrice(price);
        return prefetchFacts;
    }

    private Optional<Double> extractPriceFromResponse(WebSearchTool.SearchResponse response) {
        if (response == null) {
            return Optional.empty();
        }

        SessionFactCacheService.SessionFacts extracted = sessionFactCacheService.extractFactsFromText(flattenResponse(response));
        if (extracted.getLatestPrice() == null || extracted.getLatestPrice() <= 0) {
            return Optional.empty();
        }
        return Optional.of(extracted.getLatestPrice());
    }

    private String flattenResponse(WebSearchTool.SearchResponse response) {
        if (response == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (response.summary() != null) {
            builder.append(response.summary()).append('\n');
        }
        if (response.results() != null) {
            for (WebSearchTool.SearchResult result : response.results()) {
                if (result == null) {
                    continue;
                }
                if (result.title() != null) {
                    builder.append(result.title()).append('\n');
                }
                if (result.snippet() != null) {
                    builder.append(result.snippet()).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String buildPriceSearchQuery(String rawQuestion,
                                         SessionFactCacheService.SessionFacts facts) {
        StringBuilder builder = new StringBuilder();
        if (facts != null) {
            if (facts.getBrand() != null && !facts.getBrand().isBlank()) {
                builder.append(facts.getBrand()).append(' ');
            }
            if (facts.getModel() != null && !facts.getModel().isBlank()) {
                builder.append(facts.getModel()).append(' ');
            } else if (facts.getDeviceModels() != null && !facts.getDeviceModels().isEmpty()) {
                builder.append(pickLast(facts.getDeviceModels())).append(' ');
            }
            if (facts.getProductYear() != null) {
                builder.append(facts.getProductYear()).append(' ');
            }
        }
        if (builder.isEmpty() && rawQuestion != null) {
            builder.append(rawQuestion.trim()).append(' ');
        }

        String normalized = builder.toString().toLowerCase(Locale.ROOT);
        if (!normalized.contains("价格") && !normalized.contains("报价") && !normalized.contains("多少钱")) {
            builder.append("价格 ");
        }
        if (!normalized.contains("山东")) {
            builder.append("山东 ");
        }
        return builder.toString().trim();
    }

    private boolean hasLookupAnchor(SessionFactCacheService.SessionFacts facts,
                                    String rawQuestion) {
        if (facts != null && facts.getDeviceModels() != null && !facts.getDeviceModels().isEmpty()) {
            return true;
        }
        if (facts != null && facts.getModel() != null && !facts.getModel().isBlank()) {
            return true;
        }
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return false;
        }
        String normalized = rawQuestion.toLowerCase(Locale.ROOT);
        return normalized.contains("iphone")
                || normalized.contains("ipad")
                || normalized.contains("macbook")
                || normalized.contains("thinkpad")
                || normalized.contains("surface")
                || normalized.contains("matebook")
                || normalized.contains("华为")
                || normalized.contains("小米")
                || normalized.contains("荣耀")
                || normalized.contains("oppo")
                || normalized.contains("vivo")
                || normalized.contains("三星")
                || normalized.contains("联想")
                || normalized.contains("戴尔")
                || normalized.contains("惠普");
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
