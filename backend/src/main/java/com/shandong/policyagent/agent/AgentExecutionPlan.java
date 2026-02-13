package com.shandong.policyagent.agent;

import java.util.List;

/**
 * ReAct 智能体执行计划。
 */
public record AgentExecutionPlan(
        String summary,
        boolean needToolCall,
        List<AgentStep> steps
) {

    public record AgentStep(
            int id,
            String action,
            String toolHint
    ) {
    }
}
