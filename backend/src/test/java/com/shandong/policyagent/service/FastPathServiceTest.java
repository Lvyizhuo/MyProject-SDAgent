package com.shandong.policyagent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastPathServiceTest {

    private final FastPathService fastPathService = new FastPathService();

    @Test
    void shouldHitSubsidyRateQuestion() {
        String answer = fastPathService.tryDirectAnswer("补贴比例是多少", new SessionFactCacheService.SessionFacts());

        assertNotNull(answer);
        assertTrue(answer.contains("15%"));
    }

    @Test
    void shouldNotHitSubsidyRateForUnrelatedQuestion() {
        String answer = fastPathService.tryDirectAnswer("帮我解释这个政策条款", new SessionFactCacheService.SessionFacts());

        assertNull(answer);
    }

    @Test
    void shouldHitMaterialsQuestion() {
        String answer = fastPathService.tryDirectAnswer("申请需要什么材料", new SessionFactCacheService.SessionFacts());

        assertNotNull(answer);
        assertTrue(answer.contains("发票"));
    }

    @Test
    void shouldNotHitMaterialsForUnrelatedQuestion() {
        String answer = fastPathService.tryDirectAnswer("今天气温怎么样", new SessionFactCacheService.SessionFacts());

        assertNull(answer);
    }

    @Test
    void shouldHitProcessQuestion() {
        String answer = fastPathService.tryDirectAnswer("怎么申请补贴", new SessionFactCacheService.SessionFacts());

        assertNotNull(answer);
        assertTrue(answer.contains("一般流程"));
    }

    @Test
    void shouldHitArrivalTimeQuestion() {
        String answer = fastPathService.tryDirectAnswer("补贴多久能到账", new SessionFactCacheService.SessionFacts());

        assertNotNull(answer);
        assertTrue(answer.contains("到账"));
    }

    @Test
    void shouldHitLocalSubsidyQuestionWhenCityInFacts() {
        SessionFactCacheService.SessionFacts facts = new SessionFactCacheService.SessionFacts();
        facts.getRegions().add("济南市");

        String answer = fastPathService.tryDirectAnswer("我们这有补贴吗", facts);

        assertNotNull(answer);
        assertTrue(answer.contains("济南市"));
    }

    @Test
    void shouldNotHitLocalSubsidyQuestionWithoutCityHint() {
        String answer = fastPathService.tryDirectAnswer("我们这有补贴吗", new SessionFactCacheService.SessionFacts());

        assertNull(answer);
    }
}
