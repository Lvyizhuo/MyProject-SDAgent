package com.shandong.policyagent.service;

import com.shandong.policyagent.advisor.LoggingAdvisor;
import com.shandong.policyagent.advisor.ReReadingAdvisor;
import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicChatClientFactory {

    private final OpenAiChatModel openAiChatModel;
    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final ModelProviderService modelProviderService;
    private final SecurityAdvisor securityAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final ReReadingAdvisor reReadingAdvisor;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final LoggingAdvisor loggingAdvisor;
    private final List<ToolCallbackProvider> toolCallbackProviders;

    @Value("${app.model-provider.openai.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${app.model-provider.openai.read-timeout-seconds:45}")
    private int readTimeoutSeconds;

    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String defaultBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen3.5-plus}")
    private String defaultModelName;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double defaultTemperature;

    public ChatClient create() {
        return create(true, true);
    }

    public ChatClient create(boolean enableTools) {
        return create(enableTools, true);
    }

    public ChatClient create(boolean enableTools, boolean enableRag) {
        ResolvedChatModelConfig runtimeConfig = resolveRuntimeConfig();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutSeconds * 1000);
        requestFactory.setReadTimeout(readTimeoutSeconds * 1000);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(normalizeOpenAiBaseUrl(runtimeConfig.baseUrl()))
                .apiKey(runtimeConfig.apiKey())
            .restClientBuilder(org.springframework.web.client.RestClient.builder().requestFactory(requestFactory))
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(runtimeConfig.modelName())
                .temperature(runtimeConfig.temperature());
        if (runtimeConfig.maxTokens() != null) {
            optionsBuilder.maxTokens(runtimeConfig.maxTokens());
        }
        if (runtimeConfig.topP() != null) {
            optionsBuilder.topP(runtimeConfig.topP().doubleValue());
        }

        OpenAiChatModel chatModel = openAiChatModel.mutate()
                .openAiApi(api)
                .defaultOptions(optionsBuilder.build())
                .build();

        List<Advisor> advisors = new ArrayList<>();
        advisors.add(securityAdvisor);
        advisors.add(messageChatMemoryAdvisor);
        advisors.add(reReadingAdvisor);
        if (enableRag) {
            advisors.add(questionAnswerAdvisor);
        }
        advisors.add(loggingAdvisor);

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(advisors.toArray(Advisor[]::new));

        if (enableTools) {
            builder.defaultToolCallbacks(toolCallbackProviders.toArray(ToolCallbackProvider[]::new));
        }

        return builder.build();
    }

    ResolvedChatModelConfig resolveRuntimeConfig() {
        AgentConfig config = dynamicAgentConfigHolder.get();
        if (config == null) {
            return defaultRuntimeConfig();
        }

        if (config.getLlmModelId() != null) {
            try {
                ModelProvider provider = modelProviderService.getModelEntityForRuntime(config.getLlmModelId(), ModelType.LLM);
                log.debug("使用管理员已配置模型执行对话: modelId={}, name={}", provider.getId(), provider.getName());
                return managedRuntimeConfig(provider, config);
            } catch (Exception ex) {
                if (hasManualRuntimeConfig(config)) {
                    log.warn("管理员已配置模型不可用，回退到手工模型配置 | modelId={} | error={}",
                            config.getLlmModelId(), ex.getMessage());
                    return manualRuntimeConfig(config);
                }

                log.warn("管理员已配置模型不可用，回退到默认模型配置 | modelId={} | error={}",
                        config.getLlmModelId(), ex.getMessage());
                return defaultRuntimeConfig();
            }
        }

        if (hasManualRuntimeConfig(config)) {
            return manualRuntimeConfig(config);
        }

        return defaultRuntimeConfig();
    }

    private double safeTemperature(Double temperature) {
        return temperature != null ? temperature : 0.7D;
    }

    private ResolvedChatModelConfig managedRuntimeConfig(ModelProvider provider, AgentConfig config) {
        return new ResolvedChatModelConfig(
                provider.getApiUrl(),
                provider.getApiKey(),
                provider.getModelName(),
                provider.getTemperature() != null ? provider.getTemperature().doubleValue() : safeTemperature(config.getTemperature()),
                provider.getMaxTokens(),
                provider.getTopP()
        );
    }

    private ResolvedChatModelConfig manualRuntimeConfig(AgentConfig config) {
        return new ResolvedChatModelConfig(
                config.getApiUrl(),
                resolveApiKey(config.getApiKey()),
                config.getModelName(),
                safeTemperature(config.getTemperature()),
                null,
                null
        );
    }

    private ResolvedChatModelConfig defaultRuntimeConfig() {
        return new ResolvedChatModelConfig(
                defaultBaseUrl,
                resolveApiKey(defaultApiKey),
                defaultModelName,
                safeTemperature(defaultTemperature),
                null,
                null
        );
    }

    private boolean hasManualRuntimeConfig(AgentConfig config) {
        String resolvedApiKey = resolveApiKey(config.getApiKey());
        return hasText(config.getApiUrl())
                && hasText(config.getModelName())
                && hasText(resolvedApiKey)
                && !isPlaceholder(resolvedApiKey);
    }

    private String resolveApiKey(String apiKey) {
        if (isPlaceholder(apiKey)) {
            String envVar = apiKey.substring(2, apiKey.length() - 1);
            String value = System.getenv(envVar);
            if (hasText(value)) {
                return value;
            }
        }
        return apiKey;
    }

    private boolean isPlaceholder(String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeOpenAiBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }

        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    record ResolvedChatModelConfig(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens,
            java.math.BigDecimal topP
    ) {
    }
}
