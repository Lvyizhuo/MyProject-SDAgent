package com.shandong.policyagent.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawQueryExtractorTest {

    @Test
    void shouldReturnOriginalWhenNoMarkerPresent() {
        assertEquals("山东省汽车以旧换新补贴政策", RawQueryExtractor.extract("山东省汽车以旧换新补贴政策"));
    }

    @Test
    void shouldExtractQuestionFromAugmentedPrompt() {
        String prompt = """
                【用户原始问题】
                山东省汽车以旧换新补贴政策是什么
                【会话事实缓存】
                - 商品类别：汽车
                【ReAct执行计划（由系统规划）】
                目标：政策检索
                【执行要求】
                1. 严格按计划执行
                """;

        assertEquals("山东省汽车以旧换新补贴政策是什么", RawQueryExtractor.extract(prompt));
    }

    @Test
    void shouldHandleColonAfterMarker() {
        String prompt = "【用户原始问题】：空调补贴多少\n【执行要求】\n1. xxx";
        assertEquals("空调补贴多少", RawQueryExtractor.extract(prompt));
    }

    @Test
    void shouldReturnOriginalWhenExtractionWouldBeEmpty() {
        String prompt = "【用户原始问题】\n【会话事实缓存】\n- x";
        // 提取结果为空时回退到原始入参
        assertTrue(RawQueryExtractor.extract(prompt).contains("【用户原始问题】"));
    }

    @Test
    void shouldTruncateForCacheKey() {
        String prompt = "【用户原始问题】\n" + "很长的问题".repeat(100);
        assertEquals(200, RawQueryExtractor.extractForCache(prompt, 200).length());
    }
}
