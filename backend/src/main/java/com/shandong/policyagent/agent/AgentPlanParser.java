package com.shandong.policyagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 解析 LLM 输出的执行计划，并在异常时兜底为可执行计划。
 */
@Slf4j
@Component
public class AgentPlanParser {

    private static final Set<String> ALLOWED_TOOL_HINTS = Set.of(
            AgentExecutionPlan.TOOL_HINT_NONE, AgentExecutionPlan.TOOL_HINT_RAG,
            AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY, AgentExecutionPlan.TOOL_HINT_PARSE_FILE,
            AgentExecutionPlan.TOOL_HINT_WEB_SEARCH, AgentExecutionPlan.TOOL_HINT_AMAP);
    private static final Set<String> EXTERNAL_TOOL_HINTS = Set.of(
            AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY, AgentExecutionPlan.TOOL_HINT_PARSE_FILE,
            AgentExecutionPlan.TOOL_HINT_WEB_SEARCH, AgentExecutionPlan.TOOL_HINT_AMAP);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentExecutionPlan parse(String rawPlan, String userMessage, int maxSteps) {
        if (rawPlan == null || rawPlan.isBlank()) {
            return fallback(userMessage);
        }

        try {
            String normalized = stripCodeFence(rawPlan);
            JsonNode root = objectMapper.readTree(normalized);

            String summary = root.path("summary").asText("根据用户问题直接规划执行");
            List<AgentExecutionPlan.AgentStep> steps = new ArrayList<>();
            JsonNode stepNodes = root.path("steps");
            if (stepNodes.isArray()) {
                int index = 1;
                for (JsonNode stepNode : stepNodes) {
                    if (steps.size() >= Math.max(maxSteps, 1)) {
                        break;
                    }
                    String action = stepNode.path("action").asText("执行任务步骤");
                    String toolHint = normalizeToolHint(stepNode.path("toolHint").asText("none"), action);
                    steps.add(new AgentExecutionPlan.AgentStep(
                            stepNode.path("id").asInt(index),
                            action,
                            toolHint
                    ));
                    index++;
                }
            }

            if (steps.isEmpty()) {
                return fallback(userMessage);
            }

            boolean needToolCall = shouldUseExternalTools(root.path("needToolCall").asBoolean(false), userMessage, steps);
            return new AgentExecutionPlan(summary, needToolCall, steps);
        } catch (Exception e) {
            log.warn("解析 ReAct 计划失败，使用兜底计划 | rawPlan={}", rawPlan);
            return fallback(userMessage);
        }
    }

    private String stripCodeFence(String raw) {
        String normalized = raw.trim();
        if (normalized.startsWith("```")) {
            int firstLineBreak = normalized.indexOf('\n');
            if (firstLineBreak > 0) {
                normalized = normalized.substring(firstLineBreak + 1);
            }
            if (normalized.endsWith("```")) {
                normalized = normalized.substring(0, normalized.length() - 3);
            }
        }
        return normalized.trim();
    }

    private AgentExecutionPlan fallback(String userMessage) {
        String message = normalize(userMessage);
        String toolHint = inferToolHint(message);

        List<AgentExecutionPlan.AgentStep> steps = new ArrayList<>();
        steps.add(new AgentExecutionPlan.AgentStep(1, "识别用户核心意图与关键参数", AgentExecutionPlan.TOOL_HINT_NONE));
        if (AgentExecutionPlan.TOOL_HINT_NONE.equals(toolHint)) {
            steps.add(new AgentExecutionPlan.AgentStep(2, "优先基于历史对话与检索内容直接回答", AgentExecutionPlan.TOOL_HINT_RAG));
        } else {
            steps.add(new AgentExecutionPlan.AgentStep(2, "按需调用工具补全事实并生成结论", toolHint));
        }

        boolean needToolCall = EXTERNAL_TOOL_HINTS.contains(toolHint);
        log.debug("使用兜底计划 | userMessage={}", userMessage);
        return new AgentExecutionPlan("根据用户问题直接规划执行", needToolCall, steps);
    }

    private boolean shouldUseExternalTools(boolean modelNeedToolCall,
                                           String userMessage,
                                           List<AgentExecutionPlan.AgentStep> steps) {
        if (steps.stream().map(AgentExecutionPlan.AgentStep::toolHint).anyMatch(EXTERNAL_TOOL_HINTS::contains)) {
            return true;
        }
        String inferred = inferToolHint(normalize(userMessage));
        if (EXTERNAL_TOOL_HINTS.contains(inferred)) {
            return true;
        }
        String normalized = normalize(userMessage);
        return modelNeedToolCall && QueryIntentRules.containsAny(normalized,
                "调用工具", "工具", "tool", "联网", "搜索", "地图", "导航", "补贴", "计算", "解析文件");
    }

    private String normalizeToolHint(String rawHint, String action) {
        String hint = rawHint == null ? "" : rawHint.trim();
        if (ALLOWED_TOOL_HINTS.contains(hint)) {
            return hint;
        }
        return inferToolHint(normalize(hint + " " + action));
    }

    private String inferToolHint(String normalizedMessage) {
        if (QueryIntentRules.containsAny(normalizedMessage, QueryIntentRules.MAP_KEYWORDS)) {
            return AgentExecutionPlan.TOOL_HINT_AMAP;
        }
        if (QueryIntentRules.containsAny(normalizedMessage, "补贴", "计算", "到手价", "优惠多少", "补多少钱")) {
            return AgentExecutionPlan.TOOL_HINT_CALCULATE_SUBSIDY;
        }
        if (QueryIntentRules.containsAny(normalizedMessage, "发票", "文件", "解析", "识别图片", "附件")) {
            return AgentExecutionPlan.TOOL_HINT_PARSE_FILE;
        }
        if (QueryIntentRules.containsAny(normalizedMessage, "联网", "搜索", "实时", "最新", "今天", "价格", "股价", "指数", "新闻")) {
            return AgentExecutionPlan.TOOL_HINT_WEB_SEARCH;
        }
        if (QueryIntentRules.containsAny(normalizedMessage, "政策", "依据", "文档", "检索")) {
            return AgentExecutionPlan.TOOL_HINT_RAG;
        }
        return AgentExecutionPlan.TOOL_HINT_NONE;
    }

    private String normalize(String text) {
        return QueryIntentRules.normalize(text);
    }
}
