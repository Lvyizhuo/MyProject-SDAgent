package com.shandong.policyagent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanParserTest {

    private final AgentPlanParser parser = new AgentPlanParser();

    @Test
    void shouldParsePlanFromJsonCodeBlock() {
        String raw = """
                ```json
                {
                  \"summary\": \"用户要找附近可参加国补活动的门店\",
                  \"needToolCall\": true,
                  \"steps\": [
                    {\"id\": 1, \"action\": \"确认用户所在城市\", \"toolHint\": \"none\"},
                    {\"id\": 2, \"action\": \"调用地图服务查询门店\", \"toolHint\": \"amap-mcp\"}
                  ]
                }
                ```
                """;

        AgentExecutionPlan plan = parser.parse(raw, "帮我找附近门店", 4);

        assertEquals("用户要找附近可参加国补活动的门店", plan.summary());
        assertEquals(2, plan.steps().size());
        assertFalse(plan.steps().isEmpty());
        assertEquals("调用地图服务查询门店", plan.steps().get(1).action());
    }

    @Test
    void shouldFallbackWhenPlanIsNotJson() {
        AgentExecutionPlan plan = parser.parse("这不是JSON", "我想买空调并以旧换新", 3);

        assertEquals("根据用户问题直接规划执行", plan.summary());
        assertEquals(2, plan.steps().size());
        assertEquals("识别用户核心意图与关键参数", plan.steps().get(0).action());
        assertFalse(plan.needToolCall());
    }

    @Test
    void shouldNotForceToolCallForGeneralQuestionWhenFallback() {
        AgentExecutionPlan plan = parser.parse("not-json", "你好，请用一句话回复", 3);

        assertFalse(plan.needToolCall());
        assertEquals("rag", plan.steps().get(1).toolHint());
    }

    @Test
    void shouldNormalizeInvalidToolHintToKnownValue() {
        String raw = """
                {
                  \"summary\": \"test\",
                  \"needToolCall\": false,
                  \"steps\": [
                    {\"id\": 1, \"action\": \"调用地图服务查询路线\", \"toolHint\": \"tool\"}
                  ]
                }
                """;

        AgentExecutionPlan plan = parser.parse(raw, "帮我规划路线", 2);

        assertEquals("amap-mcp", plan.steps().get(0).toolHint());
        assertTrue(plan.needToolCall());
    }

    @Test
    void shouldTrimStepsByMaxStep() {
        String raw = """
                {
                  \"summary\": \"test\",
                  \"needToolCall\": true,
                  \"steps\": [
                    {\"id\": 1, \"action\": \"s1\", \"toolHint\": \"none\"},
                    {\"id\": 2, \"action\": \"s2\", \"toolHint\": \"none\"},
                    {\"id\": 3, \"action\": \"s3\", \"toolHint\": \"none\"}
                  ]
                }
                """;

        AgentExecutionPlan plan = parser.parse(raw, "test", 2);

        assertEquals(2, plan.steps().size());
        assertEquals("s1", plan.steps().get(0).action());
        assertEquals("s2", plan.steps().get(1).action());
    }
}
