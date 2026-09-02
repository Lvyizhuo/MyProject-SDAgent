package com.shandong.policyagent.config;

import com.shandong.policyagent.tool.ToolFailurePolicyCenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型侧工具调用失败兜底配置。
 * <p>
 * Spring AI 1.0.3 默认的 ToolExecutionExceptionProcessor 为 alwaysThrow=true：
 * 模型调用工具（webSearch/calculateSubsidy/parseFile/amap-mcp 等）抛出的异常会直接重抛，
 * 导致整轮对话 500，ToolFailurePolicyCenter 的兜底文案成为死代码。
 * 此处注册用户级 processor（自动装配侧为 @ConditionalOnMissingBean，用户定义优先），
 * 将工具异常转换为 ToolFailurePolicyCenter.fallbackMessage 的自然语言文本回喂模型。
 * </p>
 * <p>
 * 注意：该 bean 必须放在独立配置类中——若放在 DynamicChatClientFactory（构造注入
 * OpenAiChatModel）内，会形成 openAiChatModel → toolCallingManager → processor →
 * DynamicChatClientFactory → openAiChatModel 的循环依赖，导致启动失败。
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ToolFailureConfig {

    private final ToolFailurePolicyCenter toolFailurePolicyCenter;

    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return (ToolExecutionException exception) -> {
            String toolName = exception.getToolDefinition() == null
                    ? null
                    : exception.getToolDefinition().name();
            log.warn("模型侧工具调用失败，返回兜底文案 | tool={} | error={}",
                    toolName, exception.getMessage());
            return toolFailurePolicyCenter.fallbackMessage(
                    toolName == null ? "default" : toolName,
                    exception.getMessage());
        };
    }
}
