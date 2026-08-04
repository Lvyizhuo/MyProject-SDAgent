package com.shandong.policyagent.agent;

import com.shandong.policyagent.config.AgentWorkflowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * ReAct 风格任务规划服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActPlanningService {

    private static final Pattern PRICE_PATTERN = Pattern.compile(".*\\d{3,6}(?:\\.\\d{1,2})?\\s*(元|rmb|人民币|¥|￥).*");
    private static final String CURRENT_QUESTION_MARKER = "【用户当前问题】";
    private static final String CONTEXT_MARKER = "【会话结构化摘要】";

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是智能体规划器，只输出 JSON，不要输出 Markdown。
            请根据用户问题，输出一个可执行计划，JSON 格式如下：
            {
              "summary": "一句话概述",
              "needToolCall": true,
              "steps": [
                {"id": 1, "action": "步骤描述", "toolHint": "none|rag|calculateSubsidy|parseFile|webSearch|amap-mcp"}
              ]
            }

            规划要求：
            1. 步骤数不超过 4 步，按执行顺序排列。
            2. 涉及线下门店、回收点、路线、导航时，toolHint 必须包含 amap-mcp。
            3. 涉及补贴金额计算时，toolHint 必须包含 calculateSubsidy。
            4. 涉及政策依据时，优先使用 rag。
            5. 涉及“最新/实时/今日/当前/新闻/价格/市场价/电商报价/官网报价/政策动态”的问题时，
                    优先考虑 webSearch。
                6. 若同时涉及“政策/补贴规则 + 价格咨询”，先用 rag 给出本地政策结论，
                    仅在需要补充实时价格时再追加 webSearch，且放在后续步骤。
                7. 不要声称“没有 webSearch 工具”或“无法联网搜索”；若属于实时信息查询，必须规划 webSearch。
            """;

    private final ChatModel chatModel;
    private final AgentPlanParser planParser;
    private final AgentWorkflowProperties properties;

    /**
     * ReAct 规划专用线程池（可选注入）。
     * 未注入时（如单元测试直接 new），supplyAsync 退回到 ForkJoinPool.commonPool()。
     */
    @Autowired(required = false)
    @Qualifier("agentPlannerTaskExecutor")
    private Executor agentPlannerTaskExecutor;

    public AgentExecutionPlan createPlan(String conversationId, String userMessage) {
        if (!properties.isPlanningEnabled()) {
            return planParser.parse("", userMessage, properties.getMaxPlanningSteps());
        }

        AgentExecutionPlan shortcutPlan = tryBuildShortcutPlan(conversationId, userMessage);
        if (shortcutPlan != null) {
            return shortcutPlan;
        }

        try {
            Prompt planningPrompt = new Prompt(List.of(
                    new SystemMessage(PLANNER_SYSTEM_PROMPT),
                    new UserMessage(userMessage)
            ));
            // 规划 LLM 调用走专用线程池，避免阻塞 ForkJoinPool.commonPool
            Supplier<String> llmCall = () ->
                    chatModel.call(planningPrompt).getResult().getOutput().getText();
            CompletableFuture<String> planFuture = agentPlannerTaskExecutor != null
                    ? CompletableFuture.supplyAsync(llmCall, agentPlannerTaskExecutor)
                    : CompletableFuture.supplyAsync(llmCall);
            String rawPlan = planFuture
                    .orTimeout(properties.getPlanningTimeoutSeconds(), TimeUnit.SECONDS)
                    .join();

            AgentExecutionPlan plan = planParser.parse(rawPlan, userMessage, properties.getMaxPlanningSteps());
            log.info("ReAct 规划完成 | conversationId={} | summary={} | steps={}",
                    conversationId, plan.summary(), plan.steps().size());
            return plan;
        } catch (Exception e) {
            log.warn("ReAct 规划失败，使用兜底计划 | conversationId={} | error={}",
                    conversationId, e.getMessage());
            return planParser.parse("", userMessage, properties.getMaxPlanningSteps());
        }
    }

    private AgentExecutionPlan tryBuildShortcutPlan(String conversationId, String userMessage) {
        String shortcutSource = extractCurrentQuestion(userMessage);
        String normalized = normalize(shortcutSource);
        if (normalized.isBlank()) {
            return null;
        }

        if (isGreeting(normalized)) {
            log.info("ReAct 规划命中问候快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "用户在进行问候，直接友好回应并说明可提供的帮助",
                    false,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "识别为问候语并简洁回应", AgentExecutionPlan.TOOL_HINT_NONE),
                            new AgentExecutionPlan.AgentStep(2, "简要说明可咨询的政策范围", AgentExecutionPlan.TOOL_HINT_NONE)
                    )
            );
        }

        if (requiresPriceLookupBeforeSubsidy(normalized)) {
            log.info("ReAct 规划命中先查价再补贴路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "缺少成交价，先联网查价再计算补贴",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "调用 webSearch 查询该型号近期价格区间", AgentExecutionPlan.TOOL_HINT_WEB_SEARCH),
                            new AgentExecutionPlan.AgentStep(2, "根据检索到的成交价调用 calculateSubsidy 计算补贴", AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY),
                            new AgentExecutionPlan.AgentStep(3, "输出补贴测算并提示用户确认成交价", AgentExecutionPlan.TOOL_HINT_NONE)
                    )
            );
        }
        if (requiresSubsidyCalculation(normalized)) {
            log.info("ReAct 规划命中补贴计算快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题聚焦补贴金额计算，优先走补贴工具",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "收集并校验补贴计算所需参数", AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY),
                            new AgentExecutionPlan.AgentStep(2, "调用 calculateSubsidy 输出补贴金额和说明", AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY)
                    )
            );
        }

            if (isPolicyPriceMixedQuestion(normalized)) {
                log.info("ReAct 规划命中政策+价格混合路径（先政策后价格） | conversationId={}", conversationId);
                return new AgentExecutionPlan(
                    "先给政策结论，再按需补充实时价格",
                    false,
                    List.of(
                        new AgentExecutionPlan.AgentStep(1, "从知识库检索并总结本地政策口径", AgentExecutionPlan.TOOL_HINT_RAG),
                        new AgentExecutionPlan.AgentStep(2, "先回答政策结论，再提示可继续补充实时价格", AgentExecutionPlan.TOOL_HINT_NONE)
                    )
                );
            }

                if (requiresRealtimeSearch(normalized)) {
                    log.info("ReAct 规划命中实时查询快捷路径 | conversationId={}", conversationId);
                    return new AgentExecutionPlan(
                        "问题需要实时信息，先联网检索后回答",
                        true,
                        List.of(
                            new AgentExecutionPlan.AgentStep(1, "调用 webSearch 查询实时信息，关键词：" + summarizeQuery(shortcutSource), AgentExecutionPlan.TOOL_HINT_WEB_SEARCH),
                            new AgentExecutionPlan.AgentStep(2, "结合检索结果给出结论并附来源链接", AgentExecutionPlan.TOOL_HINT_NONE)
                        )
                    );
                }

        if (requiresMapSearch(normalized)) {
            log.info("ReAct 规划命中地图检索快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题涉及门店、回收点或导航，优先走地图工具",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "补充或使用已有位置上下文", AgentExecutionPlan.TOOL_HINT_AMAP),
                            new AgentExecutionPlan.AgentStep(2, "调用 amap-mcp 查询附近地点并整理结果", AgentExecutionPlan.TOOL_HINT_AMAP)
                    )
            );
        }

        if (requiresFileParsing(normalized)) {
            log.info("ReAct 规划命中文件解析快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题涉及发票或附件解析，优先走文件解析工具",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "确认待解析文件类型与输入是否齐全", AgentExecutionPlan.TOOL_HINT_PARSE_FILE),
                            new AgentExecutionPlan.AgentStep(2, "调用 parseFile 提取结构化字段", AgentExecutionPlan.TOOL_HINT_PARSE_FILE)
                    )
            );
        }

        if (requiresPolicyLookup(normalized)) {
            log.info("ReAct 规划命中政策检索快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题聚焦政策规则或申请流程，优先结合知识库检索回答",
                    false,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "从知识库检索相关政策依据", AgentExecutionPlan.TOOL_HINT_RAG),
                            new AgentExecutionPlan.AgentStep(2, "整理规则、条件和办理流程并回答", AgentExecutionPlan.TOOL_HINT_NONE)
                    )
            );
        }

        return null;
    }

    private boolean isGreeting(String normalized) {
        return normalized.equals("你好")
                || normalized.equals("您好")
                || normalized.equals("hello")
                || normalized.equals("hi")
                || normalized.equals("哈喽")
                || normalized.equals("在吗")
                || normalized.equals("早上好")
                || normalized.equals("下午好")
                || normalized.equals("晚上好");
    }

    private boolean requiresRealtimeSearch(String normalized) {
        if (hasSubsidyComputationIntent(normalized)) {
            return false;
        }
        if (isPolicyPriceMixedQuestion(normalized)) {
            return false;
        }
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.REALTIME_SEARCH_KEYWORDS);
    }

    private boolean requiresSubsidyCalculation(String normalized) {
        boolean subsidyContext = QueryIntentRules.containsAny(normalized, "补贴", "国补", "以旧换新");
        boolean computationIntent = hasSubsidyComputationIntent(normalized);
        boolean subsidyTargetHint = hasCategory(normalized) || hasSubsidyDeviceHint(normalized);

        if (subsidyContext && computationIntent && (hasPrice(normalized) || subsidyTargetHint)) {
            return true;
        }

        // 直接命中补贴计算关键词，保持原始关键词集合不变（避免改变 shortcut 优先级顺序）
        return QueryIntentRules.containsAny(normalized,
                "算补贴", "补多少", "能补多少", "补贴金额", "补贴额度", "核算补贴", "国补能有多少",
                "补贴后", "补贴后的价格", "到手价", "计算补贴")
                || (subsidyContext
                && hasPrice(normalized)
                && subsidyTargetHint);
    }

    private boolean requiresPriceLookupBeforeSubsidy(String normalized) {
        boolean subsidyOrBenefitContext = QueryIntentRules.containsAny(normalized, "补贴", "国补", "以旧换新", "优惠");
        boolean computationIntent = hasSubsidyComputationIntent(normalized)
                || hasSubsidyBenefitQueryIntent(normalized);
        boolean deviceHint = hasSubsidyDeviceHint(normalized);
        return subsidyOrBenefitContext && computationIntent && deviceHint && !hasPrice(normalized);
    }

    private boolean hasSubsidyBenefitQueryIntent(String normalized) {
        return QueryIntentRules.containsAny(normalized,
                "优惠多少", "能优惠多少", "享受多少优惠", "能享受多少优惠", "补多少", "补贴多少", "有多少优惠");
    }

    private boolean requiresMapSearch(String normalized) {
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.MAP_KEYWORDS);
    }

    private boolean requiresFileParsing(String normalized) {
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.FILE_PARSE_KEYWORDS);
    }

    private boolean requiresPolicyLookup(String normalized) {
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.POLICY_CONSULTING_KEYWORDS);
    }

    private boolean isPolicyPriceMixedQuestion(String normalized) {
        return QueryIntentRules.isPolicyPriceMixed(normalized);
    }

    private String summarizeQuery(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "山东以旧换新最新政策";
        }
        String condensed = userMessage.replace("\n", " ").replaceAll("\\s+", " ").trim();
        return condensed.length() > 120 ? condensed.substring(0, 120) : condensed;
    }

    private String extractCurrentQuestion(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "";
        }

        String raw = userMessage.trim();
        int questionStart = raw.indexOf(CURRENT_QUESTION_MARKER);
        if (questionStart < 0) {
            return raw;
        }

        int contentStart = questionStart + CURRENT_QUESTION_MARKER.length();
        int contextStart = raw.indexOf(CONTEXT_MARKER, contentStart);
        String extracted = contextStart > contentStart
                ? raw.substring(contentStart, contextStart)
                : raw.substring(contentStart);

        extracted = extracted.replaceFirst("^[：:\\s]+", "").trim();
        return extracted.isBlank() ? raw : extracted;
    }

    private String normalize(String userMessage) {
        return QueryIntentRules.normalize(userMessage);
    }

    private boolean hasPrice(String normalized) {
        return PRICE_PATTERN.matcher(normalized).matches();
    }

    private boolean hasCategory(String normalized) {
        // 设备品类 + 品牌（原实现中 hasCategory 包含 macbook/thinkpad/surface，与 hasSubsidyDeviceHint 联合使用）
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.DEVICE_CATEGORIES)
                || QueryIntentRules.containsAny(normalized, QueryIntentRules.BRAND_MODEL_KEYWORDS);
    }

    private boolean hasSubsidyDeviceHint(String normalized) {
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.BRAND_MODEL_KEYWORDS);
    }

    private boolean hasSubsidyComputationIntent(String normalized) {
        // 保持原始关键词集合，仅覆盖"计算意图"语义，避免与补贴优惠查询意图混淆影响 shortcut 优先级
        return QueryIntentRules.containsAny(normalized,
                "计算", "算一下", "帮我算", "核算", "到手价", "补贴后", "补贴后的价格", "国补后");
    }
}
