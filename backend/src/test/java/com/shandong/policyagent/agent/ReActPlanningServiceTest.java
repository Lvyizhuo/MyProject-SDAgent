package com.shandong.policyagent.agent;

import com.shandong.policyagent.config.AgentWorkflowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReActPlanningServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private AgentPlanParser planParser;

    private ReActPlanningService planningService;

    @BeforeEach
    void setUp() {
        AgentWorkflowProperties properties = new AgentWorkflowProperties();
        properties.setPlanningEnabled(true);
        properties.setMaxPlanningSteps(4);
        properties.setPlanningTimeoutSeconds(15);
        planningService = new ReActPlanningService(chatModel, planParser, properties);
    }

    @Test
    void shouldUseSubsidyShortcutWithoutCallingPlannerModel() {
        AgentExecutionPlan plan = planningService.createPlan("conv-1", "电视 5999元补多少");

        assertTrue(plan.needToolCall());
        assertEquals("calculateSubsidy", plan.steps().getFirst().toolHint());
        verifyNoInteractions(chatModel, planParser);
    }

    @Test
    void shouldUseMapShortcutWithoutCallingPlannerModel() {
        AgentExecutionPlan plan = planningService.createPlan("conv-2", "帮我找附近能参加国补的门店");

        assertTrue(plan.needToolCall());
        assertEquals("amap-mcp", plan.steps().getFirst().toolHint());
        verifyNoInteractions(chatModel, planParser);
    }

    @Test
    void shouldUsePolicyLookupShortcutWithoutCallingPlannerModel() {
        AgentExecutionPlan plan = planningService.createPlan("conv-3", "山东家电以旧换新怎么申请");

        assertFalse(plan.needToolCall());
        assertEquals("rag", plan.steps().getFirst().toolHint());
        verifyNoInteractions(chatModel, planParser);
    }

    @Test
    void shouldTreatSubsidyAfterPriceQuestionAsSubsidyShortcut() {
        AgentExecutionPlan plan = planningService.createPlan("conv-4", "根据26年的补贴政策，帮我计算下补贴后的价格");

        assertTrue(plan.needToolCall());
        assertEquals("calculateSubsidy", plan.steps().getFirst().toolHint());
        verifyNoInteractions(chatModel, planParser);
    }
}
