package com.shandong.policyagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 LLM 输出的执行计划，并在异常时兜底为可执行计划。
 */
@Slf4j
@Component
public class AgentPlanParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentExecutionPlan parse(String rawPlan, String userMessage, int maxSteps) {
        if (rawPlan == null || rawPlan.isBlank()) {
            return fallback(userMessage);
        }

        try {
            String normalized = stripCodeFence(rawPlan);
            JsonNode root = objectMapper.readTree(normalized);

            String summary = root.path("summary").asText("根据用户问题直接规划执行");
            boolean needToolCall = root.path("needToolCall").asBoolean(false);

            List<AgentExecutionPlan.AgentStep> steps = new ArrayList<>();
            JsonNode stepNodes = root.path("steps");
            if (stepNodes.isArray()) {
                int index = 1;
                for (JsonNode stepNode : stepNodes) {
                    if (steps.size() >= Math.max(maxSteps, 1)) {
                        break;
                    }
                    steps.add(new AgentExecutionPlan.AgentStep(
                            stepNode.path("id").asInt(index),
                            stepNode.path("action").asText("执行任务步骤"),
                            stepNode.path("toolHint").asText("none")
                    ));
                    index++;
                }
            }

            if (steps.isEmpty()) {
                return fallback(userMessage);
            }

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
        List<AgentExecutionPlan.AgentStep> steps = List.of(
                new AgentExecutionPlan.AgentStep(1, "识别用户核心意图与关键参数", "none"),
                new AgentExecutionPlan.AgentStep(2, "按需调用RAG、补贴计算和地图工具完成回答", "tool")
        );
        log.debug("使用兜底计划 | userMessage={}", userMessage);
        return new AgentExecutionPlan("根据用户问题直接规划执行", true, steps);
    }
}
