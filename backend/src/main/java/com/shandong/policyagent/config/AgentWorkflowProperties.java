package com.shandong.policyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 智能体工作流配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.agent")
public class AgentWorkflowProperties {

    /**
     * 是否开启规划阶段。
     */
    private boolean planningEnabled = true;

    /**
     * 规划阶段最大步骤数。
     */
    private int maxPlanningSteps = 4;

    /**
     * 规划阶段超时时间（秒）。
     */
    private int planningTimeoutSeconds = 15;
}
