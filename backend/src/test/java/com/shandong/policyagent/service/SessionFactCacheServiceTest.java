package com.shandong.policyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionFactCacheServiceTest {

    private final SessionFactCacheService factCacheService = new SessionFactCacheService(null, new ObjectMapper());

    @Test
    void shouldExtractPriceRegionAndDeviceModel() {
        SessionFactCacheService.SessionFacts facts = factCacheService.extractFactsFromText(
                "我在山东省济南市历下区，想买iPhone17标准版，预算5999元"
        );

        assertEquals(5999.0, facts.getLatestPrice());
        assertTrue(facts.getRegions().stream().anyMatch(v -> v.contains("山东")));
        assertTrue(facts.getRegions().stream().anyMatch(v -> v.contains("济南")));
        assertTrue(facts.getDeviceModels().stream().anyMatch(v -> v.contains("iphone17")));
    }
}
