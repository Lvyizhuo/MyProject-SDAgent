package com.shandong.policyagent.agent;

import com.shandong.policyagent.service.SessionFactCacheService;
import com.shandong.policyagent.tool.SubsidyCalculatorTool;
import com.shandong.policyagent.tool.ToolFailurePolicyCenter;
import com.shandong.policyagent.tool.WebSearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 服务端工具预执行器。
 *
 * <p>在智能体计划含 calculateSubsidy/webSearch 且意图分类器放行时，
 * 由服务端先执行工具，把结果注入 prompt，再让模型基于事实生成最终答案。
 * 将计划从"提示词装饰"升级为"真正的控制流"。</p>
 *
 * <h3>预执行范围决策</h3>
 * <ul>
 *   <li><b>calculateSubsidy</b>：服务端可控（参数为品类+价格，均可从会话事实和用户消息中提取），适合预执行</li>
 *   <li><b>webSearch</b>：服务端可控（参数为查询关键词，可从计划步骤或用户消息中提取），适合预执行</li>
 *   <li><b>amap-mcp</b>：不适合预执行（需要前端实时坐标，模型端可根据定位上下文灵活构造调用，服务端参数不完整）</li>
 *   <li><b>parseFile</b>：不适合预执行（需要上传文件上下文，模型端需先确认文件可用性才能传参）</li>
 * </ul>
 *
 * <h3>失败兜底</h3>
 * <p>工具执行通过 {@link ToolFailurePolicyCenter#executeWithRetry} 包裹，
 * 失败后返回 null 并记录 skipReason，绝不抛异常拖垮主链路。模型仍可自行调用工具兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannedToolExecutor {

    private final SubsidyCalculatorTool subsidyCalculatorTool;
    private final WebSearchTool webSearchTool;
    private final ToolFailurePolicyCenter failurePolicyCenter;

    /**
     * 预执行结果记录。
     *
     * @param subsidyResult     补贴计算结果，null 表示未执行或失败
     * @param subsidySkipReason 跳过/失败原因，供日志追溯
     * @param webSearchResult   联网搜索结果，null 表示未执行或失败
     * @param webSearchSkipReason 跳过/失败原因，供日志追溯
     */
    public record ToolExecutionOutcome(
            SubsidyCalculatorTool.SubsidyResponse subsidyResult,
            String subsidySkipReason,
            WebSearchTool.SearchResponse webSearchResult,
            String webSearchSkipReason
    ) {
        /** 空结果，用于无工具计划或意图拦截场景。 */
        public static ToolExecutionOutcome empty() {
            return new ToolExecutionOutcome(null, null, null, null);
        }

        public boolean hasSubsidyResult() {
            return subsidyResult != null;
        }

        public boolean hasWebSearchResult() {
            return webSearchResult != null;
        }

        public boolean hasAnyResult() {
            return hasSubsidyResult() || hasWebSearchResult();
        }
    }

    /**
     * 执行预执行逻辑。
     *
     * @param plan           智能体执行计划
     * @param userMessage    用户消息（含事实上下文）
     * @param facts          会话结构化事实
     * @param intentDecision 意图分类器决策
     * @return 工具执行结果记录
     */
    public ToolExecutionOutcome execute(AgentExecutionPlan plan,
                                         String userMessage,
                                         SessionFactCacheService.SessionFacts facts,
                                         ToolIntentClassifier.IntentDecision intentDecision) {
        // 意图不放行，不执行任何工具
        if (intentDecision == null || !intentDecision.allowToolCall()) {
            log.info("预执行跳过 | reason=意图分类器拦截 | targetTool={}",
                    intentDecision != null ? intentDecision.targetTool() : "null");
            return ToolExecutionOutcome.empty();
        }

        if (plan == null || !plan.needToolCall() || plan.steps() == null) {
            return ToolExecutionOutcome.empty();
        }

        SubsidyCalculatorTool.SubsidyResponse subsidyResult = null;
        String subsidySkipReason = null;
        WebSearchTool.SearchResponse webSearchResult = null;
        String webSearchSkipReason = null;

        // 计算补贴预执行：参数为品类+价格，均可从会话事实中提取
        if (containsToolHint(plan, AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY)) {
            String category = extractCategory(facts);
            Double price = extractPrice(facts);

            if (category != null && !category.isBlank() && price != null && price > 0) {
                subsidyResult = executeSubsidyWithFallback(category, price);
                if (subsidyResult != null) {
                    log.info("服务端预执行补贴计算完成 | category={} | price={} | actualSubsidy={}",
                            category, price, subsidyResult.actualSubsidy());
                } else {
                    subsidySkipReason = "补贴计算执行失败，走 ToolFailurePolicyCenter 兜底返回 null";
                }
            } else {
                subsidySkipReason = String.format(
                        "参数不充分(category=%s, price=%s)，跳过预执行，由模型继续澄清",
                        category, price);
                log.info("服务端预执行补贴计算跳过 | reason={}", subsidySkipReason);
            }
        }

        // 联网搜索预执行：参数为查询关键词，从计划步骤或用户消息中提取
        if (containsToolHint(plan, AgentExecutionPlan.TOOL_HINT_WEB_SEARCH)) {
            String query = extractSearchQuery(plan, userMessage);
            if (query != null && !query.isBlank()) {
                webSearchResult = executeWebSearchWithFallback(query);
                if (webSearchResult != null) {
                    log.info("服务端预执行联网搜索完成 | query={} | resultCount={}",
                            query, webSearchResult.totalResults());
                } else {
                    webSearchSkipReason = "联网搜索执行失败，走 ToolFailurePolicyCenter 兜底返回 null";
                }
            } else {
                webSearchSkipReason = "无法提取有效搜索关键词，跳过预执行";
                log.info("服务端预执行联网搜索跳过 | reason={}", webSearchSkipReason);
            }
        }

        return new ToolExecutionOutcome(subsidyResult, subsidySkipReason, webSearchResult, webSearchSkipReason);
    }

    // ---- 工具调用封装 ----

    private SubsidyCalculatorTool.SubsidyResponse executeSubsidyWithFallback(String category, double price) {
        return failurePolicyCenter.executeWithRetry(
                "calculateSubsidy",
                () -> subsidyCalculatorTool.calculateSubsidy().apply(
                        new SubsidyCalculatorTool.SubsidyRequest(category, price)),
                this::isRetryableError,
                () -> null
        );
    }

    private WebSearchTool.SearchResponse executeWebSearchWithFallback(String query) {
        return failurePolicyCenter.executeWithRetry(
                "webSearch",
                () -> webSearchTool.webSearch().apply(
                        new WebSearchTool.SearchRequest(query, 5)),
                this::isRetryableError,
                () -> null
        );
    }

    private boolean isRetryableError(Throwable error) {
        if (error == null) {
            return false;
        }
        String message = error.getMessage();
        if (message == null) {
            return true;
        }
        return message.contains("timeout")
                || message.contains("timed out")
                || message.contains("503")
                || message.contains("502")
                || message.contains("connection");
    }

    // ---- 参数提取 ----

    /**
     * 从会话事实中提取商品类别。
     * 优先级：productType > categories 首个元素
     */
    private String extractCategory(SessionFactCacheService.SessionFacts facts) {
        if (facts == null) {
            return null;
        }
        if (facts.getProductType() != null && !facts.getProductType().isBlank()) {
            return facts.getProductType();
        }
        if (facts.getCategories() != null && !facts.getCategories().isEmpty()) {
            return facts.getCategories().iterator().next();
        }
        return null;
    }

    /**
     * 从会话事实中提取价格。
     */
    private Double extractPrice(SessionFactCacheService.SessionFacts facts) {
        if (facts == null) {
            return null;
        }
        if (facts.getLatestPrice() != null && facts.getLatestPrice() > 0) {
            return facts.getLatestPrice();
        }
        return null;
    }

    /**
     * 从计划步骤或用户消息中提取联网搜索关键词。
     * 优先从计划步骤的 action 文本中解析"关键词："标记，
     * 回退到清理后的用户消息。
     */
    private String extractSearchQuery(AgentExecutionPlan plan, String userMessage) {
        // 优先从计划步骤中提取
        if (plan != null && plan.steps() != null) {
            for (AgentExecutionPlan.AgentStep step : plan.steps()) {
                if (AgentExecutionPlan.TOOL_HINT_WEB_SEARCH.equals(step.toolHint())) {
                    String query = extractKeywordFromAction(step.action());
                    if (query != null && !query.isBlank()) {
                        return query;
                    }
                }
            }
        }

        // 回退到用户消息
        if (userMessage != null && !userMessage.isBlank()) {
            return cleanUserMessageForSearch(userMessage);
        }
        return null;
    }

    /**
     * 从 action 文本中提取"关键词："后面的内容。
     */
    private String extractKeywordFromAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        int keywordIdx = action.indexOf("关键词：");
        if (keywordIdx < 0) {
            keywordIdx = action.indexOf("关键词:");
        }
        if (keywordIdx >= 0) {
            String query = action.substring(keywordIdx).replaceFirst("关键词[：:]\\s*", "").trim();
            if (!query.isBlank()) {
                return query;
            }
        }
        return null;
    }

    /**
     * 清理用户消息，移除上下文标记块，截断至合理长度。
     */
    private String cleanUserMessageForSearch(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "山东以旧换新最新政策";
        }
        String cleaned = userMessage;

        int idx = cleaned.indexOf("【用户城市上下文】");
        if (idx >= 0) {
            cleaned = cleaned.substring(0, idx);
        }
        idx = cleaned.indexOf("【用户定位上下文】");
        if (idx >= 0) {
            cleaned = cleaned.substring(0, idx);
        }
        idx = cleaned.indexOf("【会话事实缓存】");
        if (idx >= 0) {
            cleaned = cleaned.substring(0, idx);
        }

        String condensed = cleaned
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return condensed.length() > 180 ? condensed.substring(0, 180) : condensed;
    }

    // ---- 工具方法 ----

    private boolean containsToolHint(AgentExecutionPlan plan, String toolHint) {
        return plan.steps() != null
                && plan.steps().stream().anyMatch(step -> toolHint.equals(step.toolHint()));
    }
}
