package com.shandong.policyagent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionFactCacheServiceTest {

    private final SessionFactCacheService sessionFactCacheService = new SessionFactCacheService(null, null);

    @Test
    void shouldDescribeCachedAmountWithoutRealtimePriceKeyword() {
        SessionFactCacheService.SessionFacts facts = new SessionFactCacheService.SessionFacts();
        facts.setLatestPrice(3999D);

        String promptContext = sessionFactCacheService.toPromptContext(facts);

        assertTrue(promptContext.contains("最近提及金额：3999.0元"));
        assertFalse(promptContext.contains("最近提及价格"));
    }

    @Test
    void shouldExtractGenericCategoryIntoPromptContext() {
        SessionFactCacheService.SessionFacts facts = sessionFactCacheService.extractFactsFromText("电视补贴标准");

        assertEquals(java.util.Set.of("电视"), facts.getCategories());
        assertTrue(sessionFactCacheService.toPromptContext(facts).contains("商品类别：电视"));
    }

    @Test
    void shouldIgnoreSubsidyAmountWhenExtractingPurchasePrice() {
        SessionFactCacheService.SessionFacts facts = sessionFactCacheService.extractFactsFromText("实际补贴149.85元，优惠后到手");

        assertNull(facts.getLatestPrice());
    }

    @Test
    void shouldInferLaptopCategoryFromMacbookModel() {
        SessionFactCacheService.SessionFacts facts = sessionFactCacheService.extractFactsFromText("查一下MacBook Pro m5pro的价格");

        assertTrue(facts.getDeviceModels().stream().anyMatch(model -> model.toLowerCase().contains("macbook")));
        assertTrue(facts.getCategories().contains("笔记本"));
    }
}
