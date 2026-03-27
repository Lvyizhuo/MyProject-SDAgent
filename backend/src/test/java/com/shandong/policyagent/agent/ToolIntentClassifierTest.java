package com.shandong.policyagent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
