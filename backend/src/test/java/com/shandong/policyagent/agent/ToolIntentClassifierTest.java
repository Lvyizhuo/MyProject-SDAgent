package com.shandong.policyagent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolIntentClassifierTest {

    private final ToolIntentClassifier classifier = new ToolIntentClassifier();

    @Test
    void shouldKeepClarificationPlanToolFreeWhenSubsidyParamsAreMissing() {
        AgentExecutionPlan originalPlan = new AgentExecutionPlan(
                "计算补贴",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "调用补贴计算工具", "calculateSubsidy"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("帮我算补贴", originalPlan);
        AgentExecutionPlan revisedPlan = classifier.applyDecision(originalPlan, decision);

        assertFalse(decision.allowToolCall());
        assertEquals(List.of("none", "none"),
                revisedPlan.steps().stream().map(AgentExecutionPlan.AgentStep::toolHint).toList());
    }

    @Test
    void shouldBlockWebSearchWhenModelIsMissing() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "联网查价",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "调用 webSearch", "webSearch"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("帮我查下手机价格", plan);

        assertFalse(decision.allowToolCall());
        assertTrue(decision.clarificationQuestion().contains("品牌"));
    }

    @Test
    void shouldAllowWebSearchWhenModelIsExplicit() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "联网查价",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "调用 webSearch", "webSearch"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("查华为 Mate 70 Pro 512GB 价格", plan);

        assertTrue(decision.allowToolCall());
    }

    @Test
    void shouldBlockSubsidyWhenOnlyCategoryPresent() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "计算补贴",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "调用补贴计算工具", "calculateSubsidy"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("帮我算手机补贴", plan);

        assertFalse(decision.allowToolCall());
    }

    @Test
    void shouldAllowSubsidyWhenCategoryAndPricePresent() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "计算补贴",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "调用补贴计算工具", "calculateSubsidy"))
        );

        ToolIntentClassifier.IntentDecision decision = classifier.classify("手机 5999元能补贴多少", plan);

        assertTrue(decision.allowToolCall());
    }
}
