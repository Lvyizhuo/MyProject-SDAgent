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

    @Test
    void shouldNotMisfireRealtimeShortcutFromPlanningContextWrapper() {
        String plannerWrappedMessage = """
                【用户当前问题】
                根据2026年的电脑补贴政策，我能有优惠多少？

                【会话结构化摘要】
                年份=2026 | 品类=笔记本 | 诉求=价格查询

                【规划提示】
                请优先依据结构化摘要理解上下文，再判断是否需要 ReAct 工具调用。
                """;

        AgentExecutionPlan plan = planningService.createPlan("conv-5", plannerWrappedMessage);

        assertFalse(plan.needToolCall());
        assertEquals("rag", plan.steps().getFirst().toolHint());
        verifyNoInteractions(chatModel, planParser);
    }

    @Test
    void shouldPreferPolicyConclusionForPolicyPriceMixedQuestion() {
        AgentExecutionPlan plan = planningService.createPlan("conv-6", "根据2026年山东电脑补贴政策，MacBook 价格大概多少？");

        assertFalse(plan.needToolCall());
        assertEquals("rag", plan.steps().getFirst().toolHint());
        verifyNoInteractions(chatModel, planParser);
    }

    @Test
    void shouldLookupPriceBeforeSubsidyWhenPriceMissing() {
        AgentExecutionPlan plan = planningService.createPlan("conv-7", "MacBook Pro m5pro有多少优惠，帮我算一下");

        assertTrue(plan.needToolCall());
        assertEquals("webSearch", plan.steps().get(0).toolHint());
        assertEquals("calculateSubsidy", plan.steps().get(1).toolHint());
        verifyNoInteractions(chatModel, planParser);
    }

    @Test
    void shouldLookupPriceBeforeSubsidyForBenefitQuestionWithoutComputeKeyword() {
        AgentExecutionPlan plan = planningService.createPlan("conv-8", "我想购买 MacBook Pro m5pro，我能享受多少优惠");

        assertTrue(plan.needToolCall());
        assertEquals("webSearch", plan.steps().get(0).toolHint());
        assertEquals("calculateSubsidy", plan.steps().get(1).toolHint());
        verifyNoInteractions(chatModel, planParser);
    }
}
