package com.shandong.policyagent.agent;

import com.shandong.policyagent.config.properties.AgentWorkflowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReActPlanningServiceTest {

    @Mock
    private com.shandong.policyagent.service.RuntimeChatModelProvider runtimeChatModelProvider;

    @Mock
    private AgentPlanParser planParser;

    private ReActPlanningService planningService;

    @BeforeEach
    void setUp() {
        AgentWorkflowProperties properties = new AgentWorkflowProperties();
        properties.setPlanningEnabled(true);
        properties.setMaxPlanningSteps(4);
        properties.setPlanningTimeoutSeconds(15);
        planningService = new ReActPlanningService(runtimeChatModelProvider, planParser, properties);
    }

    @Test
    void shouldUseSubsidyShortcutWithoutCallingPlannerModel() {
        AgentExecutionPlan plan = planningService.createPlan("conv-1", "电视 5999元补多少");

        assertTrue(plan.needToolCall());
        assertEquals("calculateSubsidy", plan.steps().getFirst().toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldUseMapShortcutWithoutCallingPlannerModel() {
        AgentExecutionPlan plan = planningService.createPlan("conv-2", "帮我找附近能参加国补的门店");

        assertTrue(plan.needToolCall());
        assertEquals("amap-mcp", plan.steps().getFirst().toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldUsePolicyLookupShortcutWithoutCallingPlannerModel() {
        AgentExecutionPlan plan = planningService.createPlan("conv-3", "山东家电以旧换新怎么申请");

        assertFalse(plan.needToolCall());
        assertEquals("rag", plan.steps().getFirst().toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldTreatSubsidyAfterPriceQuestionAsSubsidyShortcut() {
        AgentExecutionPlan plan = planningService.createPlan("conv-4", "根据26年的补贴政策，帮我计算下补贴后的价格");

        assertTrue(plan.needToolCall());
        assertEquals("calculateSubsidy", plan.steps().getFirst().toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
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
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldPreferPolicyConclusionForPolicyPriceMixedQuestion() {
        AgentExecutionPlan plan = planningService.createPlan("conv-6", "根据2026年山东电脑补贴政策，MacBook 价格大概多少？");

        assertFalse(plan.needToolCall());
        assertEquals("rag", plan.steps().getFirst().toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldLookupPriceBeforeSubsidyWhenPriceMissing() {
        AgentExecutionPlan plan = planningService.createPlan("conv-7", "MacBook Pro m5pro有多少优惠，帮我算一下");

        assertTrue(plan.needToolCall());
        assertEquals("webSearch", plan.steps().get(0).toolHint());
        assertEquals("calculateSubsidy", plan.steps().get(1).toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldLookupPriceBeforeSubsidyForBenefitQuestionWithoutComputeKeyword() {
        AgentExecutionPlan plan = planningService.createPlan("conv-8", "我想购买 MacBook Pro m5pro，我能享受多少优惠");

        assertTrue(plan.needToolCall());
        assertEquals("webSearch", plan.steps().get(0).toolHint());
        assertEquals("calculateSubsidy", plan.steps().get(1).toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldPlanWebSearchForExplicitSearchRequestWithProductModelSuffix() {
        // 回归：用户原话是「你联网搜索一下价格，iPhone17标准版256GB」。
        // 修复前「标准版」被当成政策关键词 → 命中混合问法路径 → 计划里没有 webSearch 步骤，
        // 服务端预执行器因此不搜索，模型只能基于 RAG 政策文档回答「未检索到实时价格」。
        AgentExecutionPlan plan = planningService.createPlan("conv-9", "你联网搜索一下价格，iPhone17标准版256GB");

        assertTrue(plan.needToolCall(), "显式要求联网搜索时，计划必须启用工具调用");
        assertEquals("webSearch", plan.steps().get(0).toolHint(), "计划的第一步必须是联网搜索");
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }

    @Test
    void shouldPlanWebSearchForExplicitSearchRequestWithPolicyContext() {
        // 即使同时提到政策，显式联网指令也优先于「先政策后价格」的保守策略
        AgentExecutionPlan plan = planningService.createPlan("conv-10", "帮我搜一下山东以旧换新补贴政策的最新通知");

        assertTrue(plan.needToolCall());
        assertEquals("webSearch", plan.steps().get(0).toolHint());
        verifyNoInteractions(runtimeChatModelProvider, planParser);
    }
}
