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
            """;

    private final ChatModel chatModel;
    private final AgentPlanParser planParser;
    private final AgentWorkflowProperties properties;

    public AgentExecutionPlan createPlan(String conversationId, String userMessage) {
        if (!properties.isPlanningEnabled()) {
            return planParser.parse("", userMessage, properties.getMaxPlanningSteps());
        }

        try {
            Prompt planningPrompt = new Prompt(List.of(
                    new SystemMessage(PLANNER_SYSTEM_PROMPT),
                    new UserMessage(userMessage)
            ));
            String rawPlan = chatModel.call(planningPrompt).getResult().getOutput().getText();

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
}
