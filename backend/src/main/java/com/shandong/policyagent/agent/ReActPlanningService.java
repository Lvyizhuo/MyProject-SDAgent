package com.shandong.policyagent.agent;

import com.shandong.policyagent.config.AgentWorkflowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ReAct 风格任务规划服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActPlanningService {

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
               必须将关键步骤的 toolHint 设为 webSearch，并将 needToolCall 设为 true。
            6. 不要声称“没有 webSearch 工具”或“无法联网搜索”；若属于实时信息查询，必须规划 webSearch。
            """;

    private final ChatModel chatModel;
    private final AgentPlanParser planParser;
    private final AgentWorkflowProperties properties;

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
            String rawPlan = CompletableFuture.supplyAsync(() ->
                            chatModel.call(planningPrompt).getResult().getOutput().getText())
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
        String normalized = normalize(userMessage);
        if (normalized.isBlank()) {
            return null;
        }

        if (isGreeting(normalized)) {
            log.info("ReAct 规划命中问候快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "用户在进行问候，直接友好回应并说明可提供的帮助",
                    false,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "识别为问候语并简洁回应", "none"),
                            new AgentExecutionPlan.AgentStep(2, "简要说明可咨询的政策范围", "none")
                    )
            );
        }

        if (requiresRealtimeSearch(normalized)) {
            log.info("ReAct 规划命中实时查询快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题需要实时信息，先联网检索后回答",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "调用 webSearch 查询实时信息，关键词：" + summarizeQuery(userMessage), "webSearch"),
                            new AgentExecutionPlan.AgentStep(2, "结合检索结果给出结论并附来源链接", "none")
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
        return normalized.contains("最新")
                || normalized.contains("实时")
                || normalized.contains("今日")
                || normalized.contains("当前")
                || normalized.contains("新闻")
                || normalized.contains("动态")
                || normalized.contains("价格")
                || normalized.contains("市场价")
                || normalized.contains("报价")
                || normalized.contains("电商")
                || normalized.contains("官网")
                || normalized.contains("search")
                || normalized.contains("websearch");
    }

    private String summarizeQuery(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "山东以旧换新最新政策";
        }
        String condensed = userMessage.replace("\n", " ").replaceAll("\\s+", " ").trim();
        return condensed.length() > 120 ? condensed.substring(0, 120) : condensed;
    }

    private String normalize(String userMessage) {
        return userMessage == null ? "" : userMessage.trim().toLowerCase(Locale.ROOT);
    }
}
