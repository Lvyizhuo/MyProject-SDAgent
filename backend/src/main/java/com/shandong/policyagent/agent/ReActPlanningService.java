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
import java.util.regex.Pattern;

/**
 * ReAct 风格任务规划服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActPlanningService {

    private static final Pattern PRICE_PATTERN = Pattern.compile(".*\\d{3,6}(?:\\.\\d{1,2})?\\s*(元|rmb|¥|￥).*");

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

        if (requiresSubsidyCalculation(normalized)) {
            log.info("ReAct 规划命中补贴计算快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题聚焦补贴金额计算，优先走补贴工具",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "收集并校验补贴计算所需参数", "calculateSubsidy"),
                            new AgentExecutionPlan.AgentStep(2, "调用 calculateSubsidy 输出补贴金额和说明", "calculateSubsidy")
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

        if (requiresMapSearch(normalized)) {
            log.info("ReAct 规划命中地图检索快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题涉及门店、回收点或导航，优先走地图工具",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "补充或使用已有位置上下文", "amap-mcp"),
                            new AgentExecutionPlan.AgentStep(2, "调用 amap-mcp 查询附近地点并整理结果", "amap-mcp")
                    )
            );
        }

        if (requiresFileParsing(normalized)) {
            log.info("ReAct 规划命中文件解析快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题涉及发票或附件解析，优先走文件解析工具",
                    true,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "确认待解析文件类型与输入是否齐全", "parseFile"),
                            new AgentExecutionPlan.AgentStep(2, "调用 parseFile 提取结构化字段", "parseFile")
                    )
            );
        }

        if (requiresPolicyLookup(normalized)) {
            log.info("ReAct 规划命中政策检索快捷路径 | conversationId={}", conversationId);
            return new AgentExecutionPlan(
                    "问题聚焦政策规则或申请流程，优先结合知识库检索回答",
                    false,
                    List.of(
                            new AgentExecutionPlan.AgentStep(1, "从知识库检索相关政策依据", "rag"),
                            new AgentExecutionPlan.AgentStep(2, "整理规则、条件和办理流程并回答", "none")
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

    private boolean requiresSubsidyCalculation(String normalized) {
        boolean subsidyContext = containsAny(normalized, "补贴", "国补", "以旧换新");
        boolean computationIntent = hasSubsidyComputationIntent(normalized);
        boolean subsidyTargetHint = hasCategory(normalized) || hasSubsidyDeviceHint(normalized);

        if (subsidyContext && computationIntent && (hasPrice(normalized) || subsidyTargetHint)) {
            return true;
        }

        return containsAny(normalized,
                "算补贴", "补多少", "能补多少", "补贴金额", "补贴额度", "核算补贴", "国补能有多少",
                "补贴后", "补贴后的价格", "到手价", "计算补贴")
                || (subsidyContext
                && hasPrice(normalized)
                && subsidyTargetHint);
    }

    private boolean requiresMapSearch(String normalized) {
        return containsAny(normalized, "地图", "导航", "路线", "门店", "附近", "回收点", "高德", "amap");
    }

    private boolean requiresFileParsing(String normalized) {
        return containsAny(normalized, "发票", "附件", "上传", "图片", "文件", "pdf", "jpg", "png");
    }

    private boolean requiresPolicyLookup(String normalized) {
        return containsAny(normalized, "政策", "申请", "流程", "条件", "资格", "标准", "规则", "材料", "怎么办");
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

    private boolean hasPrice(String normalized) {
        return PRICE_PATTERN.matcher(normalized).matches();
    }

    private boolean hasCategory(String normalized) {
        return containsAny(normalized,
                "手机", "平板", "手表", "手环", "空调", "冰箱", "洗衣机", "电视", "热水器",
                "微波炉", "油烟机", "洗碗机", "燃气灶", "净水器", "笔记本", "电脑", "macbook", "thinkpad", "surface");
    }

    private boolean hasSubsidyDeviceHint(String normalized) {
        return containsAny(normalized,
                "macbook", "thinkpad", "surface", "matebook", "笔记本", "电脑",
                "iphone", "ipad", "华为", "小米", "荣耀", "oppo", "vivo");
    }

    private boolean hasSubsidyComputationIntent(String normalized) {
        return containsAny(normalized,
                "计算", "算一下", "帮我算", "核算", "到手价", "补贴后", "补贴后的价格", "国补后");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
