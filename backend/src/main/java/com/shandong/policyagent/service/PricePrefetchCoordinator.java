package com.shandong.policyagent.service;

import com.shandong.policyagent.agent.QueryIntentRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 价格预取协调器。
 * <p>
 * 负责价格查询意图检测、异步价格预取触发与等待、商品价格缓存读写编排。
 * 从 ChatService 拆分而来，保持原逻辑行为完全不变。
 * </p>
 */
@Slf4j
@Service
public class PricePrefetchCoordinator {

    private final SessionFactCacheService sessionFactCacheService;
    private final ProductPriceCacheService productPriceCacheService;

    @Value("${app.price-cache.prefetch-await-enabled:true}")
    private boolean prefetchAwaitEnabled;

    @Value("${app.price-cache.prefetch-await-max-seconds:1}")
    private long prefetchAwaitMaxSeconds;

    @Value("${app.price-cache.prefetch-await-poll-millis:200}")
    private long prefetchAwaitPollMillis;

    public PricePrefetchCoordinator(SessionFactCacheService sessionFactCacheService,
                                     ProductPriceCacheService productPriceCacheService) {
        this.sessionFactCacheService = sessionFactCacheService;
        this.productPriceCacheService = productPriceCacheService;
    }

    /**
     * 检测是否为纯价格查询（不含政策意图）。
     */
    public boolean isPurePriceQuery(String rawQuestion,
                                     SessionFactCacheService.SessionFacts facts) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return false;
        }
        String normalized = rawQuestion.toLowerCase(Locale.ROOT);
        boolean hasPriceIntent = QueryIntentRules.containsAny(normalized,
                "价格", "报价", "多少钱", "市场价", "售价", "价位", "优惠");
        if (!hasPriceIntent) {
            return false;
        }

        boolean hasPolicyIntent = QueryIntentRules.containsAny(normalized,
                "政策", "补贴", "国补", "以旧换新", "申领", "流程", "资格", "条件");
        if (hasPolicyIntent) {
            return false;
        }

        return hasExplicitBrandOrModel(normalized, facts);
    }

    /**
     * 判断是否包含明确品牌或型号信息。
     */
    public boolean hasExplicitBrandOrModel(String normalized,
                                            SessionFactCacheService.SessionFacts facts) {
        if (facts != null) {
            if (facts.getDeviceModels() != null && !facts.getDeviceModels().isEmpty()) {
                return true;
            }
            if (facts.getModel() != null && !facts.getModel().isBlank()) {
                return true;
            }
            if (facts.getBrand() != null && !facts.getBrand().isBlank()) {
                return true;
            }
        }

        return QueryIntentRules.containsAny(normalized, QueryIntentRules.BRAND_MODEL_KEYWORDS)
                || normalized.matches(".*(?:\\d{2,4}(gb|g|tb|t)|20[2-6]\\d款).*");
    }

    /**
     * 从商品价格缓存中补全 facts 中的价格信息。
     */
    public SessionFactCacheService.SessionFacts enrichFactsWithProductPriceCacheIfNeeded(
            String conversationId,
            String rawUserMessage,
            SessionFactCacheService.SessionFacts facts) {
        if (facts == null || facts.getLatestPrice() != null) {
            return facts;
        }
        String normalized = rawUserMessage == null ? "" : rawUserMessage.trim().toLowerCase(Locale.ROOT);
        if (!isSubsidyOrBenefitIntent(normalized, facts) && !isPriceLookupIntent(normalized, facts)) {
            return facts;
        }

        return productPriceCacheService.lookupPrice(facts)
                .map(price -> {
                    log.info("命中商品价格缓存 | conversationId={} | price={}", conversationId, price);
                    String seedText = (rawUserMessage == null ? "" : rawUserMessage) + " " + price + "元";
                    return sessionFactCacheService.mergeFactsFromText(
                            conversationId,
                            seedText,
                            SessionFactCacheService.FactSource.WEB_SEARCH
                    );
                })
                .orElse(facts);
    }

    /**
     * 阻塞等待预取的价格结果，超时后继续主链路。
     */
    public SessionFactCacheService.SessionFacts waitForPrefetchedPrice(
            String conversationId,
            String rawUserMessage,
            SessionFactCacheService.SessionFacts facts) {
        if (!prefetchAwaitEnabled || facts == null) {
            return facts;
        }
        if (facts.getLatestPrice() != null && facts.getLatestPrice() > 0) {
            return facts;
        }

        long maxWaitMillis = Math.max(0L, prefetchAwaitMaxSeconds) * 1000L;
        if (maxWaitMillis <= 0L) {
            return facts;
        }
        long pollMillis = Math.max(100L, prefetchAwaitPollMillis);
        long start = System.currentTimeMillis();
        long deadline = start + maxWaitMillis;

        while (System.currentTimeMillis() < deadline) {
            var cachedPrice = productPriceCacheService.lookupPrice(facts);
            if (cachedPrice != null && cachedPrice.isPresent() && cachedPrice.get() > 0) {
                long waited = System.currentTimeMillis() - start;
                log.info("价格预取等待命中 | conversationId={} | waitedMs={} | price={}",
                        conversationId, waited, cachedPrice.get());
                String seedText = (rawUserMessage == null ? "" : rawUserMessage) + " " + cachedPrice.get() + "元";
                return sessionFactCacheService.mergeFactsFromText(
                        conversationId,
                        seedText,
                        SessionFactCacheService.FactSource.WEB_SEARCH
                );
            }

            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0L) {
                break;
            }
            long sleepMillis = Math.min(pollMillis, remain);
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.warn("价格预取等待被中断，继续执行主链路 | conversationId={}", conversationId);
                return facts;
            }
        }

        long waited = System.currentTimeMillis() - start;
        log.info("价格预取等待超时，继续执行主链路 | conversationId={} | waitedMs={}", conversationId, waited);
        return facts;
    }

    /**
     * 触发异步价格预取，并在启用等待时阻塞等待结果。
     *
     * @return 可能已更新价格的 facts（如果等待命中）
     */
    public SessionFactCacheService.SessionFacts handlePurePriceQuery(
            String conversationId,
            String rawQuestion,
            SessionFactCacheService.SessionFacts facts) {
        if (!isPurePriceQuery(rawQuestion, facts)) {
            return facts;
        }
        log.info("检测到纯价格查询，触发异步价格预取 | conversationId={}", conversationId);
        productPriceCacheService.prefetchPriceAsync(rawQuestion, facts);
        return waitForPrefetchedPrice(conversationId, rawQuestion, facts);
    }

    /**
     * 判断用户意图是否包含价格查询。
     */
    public boolean isPriceLookupIntent(String normalized,
                                        SessionFactCacheService.SessionFacts facts) {
        if (QueryIntentRules.containsAny(normalized, "价格", "报价", "多少钱", "市场价", "售价", "价位", "官网", "电商")) {
            return true;
        }
        return facts != null && facts.getIntentHints() != null && facts.getIntentHints().contains("价格查询");
    }

    /**
     * 如果 facts 中已包含价格信息，异步缓存到商品价格缓存。
     */
    public void cacheProductPriceIfPresent(SessionFactCacheService.SessionFacts facts,
                                            String rawUserMessage,
                                            String source) {
        if (facts == null || facts.getLatestPrice() == null || facts.getLatestPrice() <= 0) {
            return;
        }
        productPriceCacheService.cachePriceAsync(facts, rawUserMessage, source);
    }

    /**
     * 判断用户意图是否包含补贴测算或优惠政策。
     */
    public boolean isSubsidyOrBenefitIntent(String normalized,
                                             SessionFactCacheService.SessionFacts facts) {
        if (QueryIntentRules.containsAny(normalized,
                "补贴", "国补", "以旧换新", "优惠", "补多少", "享受多少", "到手价", "帮我算", "算一下", "计算")) {
            return true;
        }
        return facts != null && facts.getIntentHints() != null && facts.getIntentHints().contains("补贴测算");
    }
}
