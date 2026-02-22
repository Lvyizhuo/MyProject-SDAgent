package com.shandong.policyagent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolIntentClassifierTest {

    private final ToolIntentClassifier classifier = new ToolIntentClassifier();

    @Test
    void shouldBlockWebSearchWhenSubjectIsMissing() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "查询价格",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "联网搜索价格", "webSearch"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("使用搜索工具帮我查询一下价格", plan);

        assertFalse(decision.allowToolCall());
        assertTrue(decision.clarificationQuestion().contains("具体商品"));
    }

    @Test
    void shouldAllowWebSearchWhenSubjectIsSpecific() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "查询 iPhone 价格",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "联网搜索价格", "webSearch"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("iPhone17 标准版价格多少", plan);

        assertTrue(decision.allowToolCall());
    }

    @Test
    void shouldBlockMapSearchWithoutLocation() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "查询附近门店",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "查询附近门店", "amap-mcp"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("帮我找附近门店", plan);

        assertFalse(decision.allowToolCall());
    }
}
