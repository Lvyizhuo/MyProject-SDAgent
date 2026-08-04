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

    /** 工具 hint 常量，集中定义避免硬编码散落各处。 */
    public static final String TOOL_HINT_NONE = "none";
    public static final String TOOL_HINT_RAG = "rag";
    public static final String TOOL_HINT_CALCULATE_SUBSIDY = "calculateSubsidy";
    public static final String TOOL_HINT_PARSE_FILE = "parseFile";
    public static final String TOOL_HINT_WEB_SEARCH = "webSearch";
    public static final String TOOL_HINT_AMAP = "amap-mcp";

    public record AgentStep(
            int id,
            String action,
            String toolHint
    ) {
    }
}
