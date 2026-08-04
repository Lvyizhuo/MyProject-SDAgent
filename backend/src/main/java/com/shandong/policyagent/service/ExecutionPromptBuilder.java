package com.shandong.policyagent.service;

import com.shandong.policyagent.agent.AgentExecutionPlan;
import com.shandong.policyagent.agent.PlannedToolExecutor;
import com.shandong.policyagent.agent.QueryIntentRules;
import com.shandong.policyagent.agent.ToolIntentClassifier;
import com.shandong.policyagent.tool.SubsidyCalculatorTool;
import com.shandong.policyagent.tool.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 执行提示词构建器。
 * <p>
 * 负责根据 ReAct 计划和预执行工具结果，拼接出注入模型的完整执行提示词。
 * 同时处理实时搜索计划强制修正、混合问法策略注入等逻辑。
 * 从 ChatService 拆分而来，保持原逻辑行为完全不变。
 * </p>
 */
@Slf4j
@Service
public class ExecutionPromptBuilder {

    /**
     * 构建注入模型的完整执行提示词（含执行计划、要求、预执行结果）。
     */
    public String buildExecutionPrompt(String userMessage,
                                        AgentExecutionPlan plan,
                                        PlannedToolExecutor.ToolExecutionOutcome toolOutcome) {
        StringBuilder stepsBuilder = new StringBuilder();
        for (AgentExecutionPlan.AgentStep step : plan.steps()) {
            stepsBuilder.append(step.id())
                    .append(". ")
                    .append(step.action())
                    .append(" [toolHint=")
                    .append(step.toolHint())
                    .append("]\n");
        }

        String prompt = String.format("""
                【用户原始问题】
                %s

                【ReAct执行计划（由系统规划）】
                目标：%s
                需要工具：%s
                步骤：
                %s

                【执行要求】
                1. 严格按计划执行，但如果执行中发现用户信息不足，可先提最少必要问题。
                2. 需要事实依据时优先使用RAG检索结果。
                3. 需要计算补贴时必须调用 calculateSubsidy。
                4. 用户询问线下购买、门店、回收点、路线、导航时，优先调用高德地图MCP工具；若MCP暂不可用再给出人工兜底建议。
                5. 不要暴露内部思维链路，只输出对用户可读的结论、步骤和建议。
                6. 问题涉及"政策+价格"混合诉求时，先给出本地政策结论，再补充价格信息；仅在用户明确要实时价格或本地证据不足时再联网检索。
                7. 若问题明确包含最新/实时/今日/当前/新闻/政策动态等强时效诉求，应调用 webSearch 并给出来源链接。
                """, userMessage, plan.summary(), plan.needToolCall(), stepsBuilder);

        // 注入预执行工具结果
        if (toolOutcome != null && toolOutcome.hasAnyResult()) {
            prompt += "\n\n" + buildPreExecutedToolsSection(toolOutcome);
        }

        if (isPolicyPriceMixedQuestion(userMessage)) {
            return prompt + "\n\n【混合问法补充策略】\n"
                    + "1. 先输出山东本地政策口径与适用边界。\n"
                    + "2. 若暂缺实时价格证据，不要编造最新价格；引导用户补充品牌+型号后再执行 webSearch。\n"
                    + "3. 价格补充放在政策结论之后。";
        }
        return prompt;
    }

    /**
     * 构建【系统已执行工具结果】注入区块。
     * 明确告知模型这些工具已由服务端执行完毕，直接使用结果作答，不要重复调用。
     */
    public String buildPreExecutedToolsSection(PlannedToolExecutor.ToolExecutionOutcome toolOutcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("【系统已执行工具结果】\n");
        sb.append("以下工具已由系统预先执行完毕，请直接基于以下结果作答。\n");
        sb.append("不要重复调用 calculateSubsidy 和 webSearch 工具，它们的结果已提供。\n");

        if (toolOutcome.hasSubsidyResult()) {
            sb.append("\n--- 补贴计算结果 ---\n");
            sb.append(buildDirectSubsidyAnswer(toolOutcome.subsidyResult()));
            sb.append("\n");
        }

        if (toolOutcome.hasWebSearchResult()) {
            sb.append("\n--- 联网搜索结果 ---\n");
            sb.append(buildWebSearchFallbackAnswer(toolOutcome.webSearchResult()));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 将补贴计算结果格式化为模型可读文案。
     */
    public String buildDirectSubsidyAnswer(SubsidyCalculatorTool.SubsidyResponse response) {
        if (response == null) {
            return "";
        }
        if (response.summary() == null || response.summary().isBlank()) {
            return "已完成补贴计算，但暂未生成可读结果。";
        }
        if (response.actualSubsidy() <= 0) {
            return response.summary();
        }
        return response.summary() + "\n\n如需，我还可以继续帮您整理申领条件、所需材料和操作流程。";
    }

    /**
     * 将联网搜索结果格式化为模型可读文案。
     */
    public String buildWebSearchFallbackAnswer(WebSearchTool.SearchResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("我已为您执行联网搜索。\n\n");

        if (response.summary() != null && !response.summary().isBlank()) {
            builder.append("检索摘要：")
                    .append(response.summary())
                    .append("\n\n");
        }

        if (response.results() == null || response.results().isEmpty()) {
            builder.append("当前没有拿到可用搜索结果。您可以稍后重试，或直接查看品牌官网、电商官方旗舰店获取最新价格。\n");
            return builder.toString().trim();
        }

        builder.append("主要来源：\n");
        int count = Math.min(response.results().size(), 3);
        for (int index = 0; index < count; index++) {
            WebSearchTool.SearchResult result = response.results().get(index);
            builder.append(index + 1)
                    .append(". ")
                    .append(result.title())
                    .append("\n")
                    .append("链接：")
                    .append(result.url())
                    .append("\n");
            if (result.snippet() != null && !result.snippet().isBlank()) {
                builder.append("摘要：")
                        .append(result.snippet())
                        .append("\n");
            }
            builder.append("\n");
        }

        builder.append("如果您愿意，我还可以继续帮您把这些实时价格和山东以旧换新补贴规则结合起来，估算实际到手价。\n");
        return builder.toString().trim();
    }

    /**
     * 检测到实时查询意图时，强制修正计划为 webSearch 优先。
     */
    public AgentExecutionPlan enforceRealtimeWebSearch(String userMessage,
                                                        AgentExecutionPlan plan,
                                                        ToolIntentClassifier.IntentDecision intentDecision,
                                                        String conversationId) {
        if (plan == null || !requiresRealtimeSearch(userMessage)) {
            return plan;
        }
        if (isPolicyPriceMixedQuestion(userMessage)) {
            return plan;
        }
        if (containsToolHint(plan, AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY) || isSubsidyQuestion(userMessage)) {
            return plan;
        }
        if (plan.steps() != null && plan.steps().stream().anyMatch(step -> AgentExecutionPlan.TOOL_HINT_WEB_SEARCH.equals(step.toolHint()))) {
            return plan;
        }
        if (!intentDecision.allowToolCall() && AgentExecutionPlan.TOOL_HINT_WEB_SEARCH.equals(intentDecision.targetTool())) {
            return plan;
        }

        String conciseQuery = extractQueryForWebSearch(userMessage);
        log.info("检测到实时查询意图，强制修正为 webSearch 计划 | conversationId={} | query={}",
                conversationId, conciseQuery);

        return new AgentExecutionPlan(
                "问题需要实时信息，先联网检索后回答",
                true,
                List.of(
                        new AgentExecutionPlan.AgentStep(1,
                                "调用 webSearch 查询实时信息，关键词：" + conciseQuery,
                                AgentExecutionPlan.TOOL_HINT_WEB_SEARCH),
                        new AgentExecutionPlan.AgentStep(2,
                                "结合检索结果给出结论并附来源链接",
                                AgentExecutionPlan.TOOL_HINT_NONE)
                )
        );
    }

    /**
     * 判断用户消息是否包含实时搜索关键词。
     */
    public boolean requiresRealtimeSearch(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalized = userMessage.toLowerCase();
        if (isPolicyPriceMixedQuestion(normalized)) {
            return false;
        }
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.REALTIME_SEARCH_KEYWORDS);
    }

    /**
     * 判断是否政策+价格混合问法。
     */
    public boolean isPolicyPriceMixedQuestion(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        return QueryIntentRules.isPolicyPriceMixed(normalized);
    }

    /**
     * 判断是否补贴相关问题。
     */
    public boolean isSubsidyQuestion(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        return QueryIntentRules.containsAny(normalized, "补贴", "国补", "以旧换新", "到手价", "补贴后");
    }

    /**
     * 从用户消息中提取用于联网搜索的简洁关键词。
     */
    public String extractQueryForWebSearch(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "山东以旧换新最新政策";
        }
        String cleaned = userMessage;
        int contextMarkerIndex = cleaned.indexOf("【用户城市上下文】");
        if (contextMarkerIndex >= 0) {
            cleaned = cleaned.substring(0, contextMarkerIndex);
        }
        contextMarkerIndex = cleaned.indexOf("【用户定位上下文】");
        if (contextMarkerIndex >= 0) {
            cleaned = cleaned.substring(0, contextMarkerIndex);
        }

        String condensed = cleaned
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return condensed.length() > 120 ? condensed.substring(0, 120) : condensed;
    }

    private boolean containsToolHint(AgentExecutionPlan plan, String toolHint) {
        return plan.steps() != null
                && plan.steps().stream().anyMatch(step -> toolHint.equals(step.toolHint()));
    }
}
