package com.shandong.policyagent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
