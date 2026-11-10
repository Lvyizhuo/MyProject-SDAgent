package com.shandong.policyagent.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对「联网搜索搜不到手机价格」这一线上问题的回归测试。
 *
 * <p>问题链路（修复前）：
 * <ol>
 *   <li>用户问「你联网搜索一下价格，iPhone17标准版256GB」</li>
 *   <li>{@code isPolicyPriceMixed} 把型号后缀「标准」当成政策关键词 → 判为「政策+价格混合问法」</li>
 *   <li>规划命中混合问法路径 → 计划里只有 RAG 步骤，<b>没有 webSearch 步骤</b></li>
 *   <li>服务端预执行器不执行 webSearch → 模型上下文里没有搜索结果 → 回复「未检索到实时价格」</li>
 * </ol>
 */
class QueryIntentRulesSearchRoutingTest {

    @Test
    @DisplayName("iPhone17标准版这类商品型号不应被判为政策+价格混合问法")
    void shouldNotTreatProductModelSuffixAsPolicyIntent() {
        String message = "你联网搜索一下价格，iphone17标准版256gb";

        assertFalse(QueryIntentRules.isPolicyPriceMixed(message),
                "「标准版」是商品型号后缀，不是政策口径，不应触发混合问法判定");
    }

    @Test
    @DisplayName("真实政策+价格混合问法仍应被识别（避免过度放宽）")
    void shouldStillDetectRealPolicyPriceMixed() {
        String message = "山东以旧换新补贴标准和手机价格是多少";

        assertTrue(QueryIntentRules.isPolicyPriceMixed(message),
                "同时出现补贴政策口径与价格诉求时，仍应识别为混合问法");
    }

    @Test
    @DisplayName("只含弱政策词「标准」+ 价格，不算政策意图")
    void shouldRequireStrongPolicySignal() {
        String message = "这批货的执行标准报价是多少";

        assertFalse(QueryIntentRules.isPolicyPriceMixed(message),
                "仅凭「标准」这类弱政策词不应判定为政策意图");
    }

    @Test
    @DisplayName("显式联网请求应被识别")
    void shouldDetectExplicitSearchRequest() {
        assertTrue(QueryIntentRules.isExplicitSearchRequest("你联网搜索一下价格，iphone17标准版256gb"));
        assertTrue(QueryIntentRules.isExplicitSearchRequest("帮我搜一下iPhone17的价格"));
        assertTrue(QueryIntentRules.isExplicitSearchRequest("上网查查最新政策"));
    }

    @Test
    @DisplayName("普通问法不应被误判为显式联网请求")
    void shouldNotDetectExplicitSearchRequestForNormalMessages() {
        assertFalse(QueryIntentRules.isExplicitSearchRequest("山东以旧换新补贴政策是什么"));
        assertFalse(QueryIntentRules.isExplicitSearchRequest("家电以旧换新需要满足什么条件"));
        assertFalse(QueryIntentRules.isExplicitSearchRequest(null));
        assertFalse(QueryIntentRules.isExplicitSearchRequest(""));
    }
}
