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
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;

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

    public ChatClient create() {
        return create(true);
    }

    public ChatClient create(boolean enableTools) {
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

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        securityAdvisor,
                        messageChatMemoryAdvisor,
                        reReadingAdvisor,
                        questionAnswerAdvisor,
                        loggingAdvisor
            );

        if (enableTools) {
            builder.defaultToolCallbacks(toolCallbackProviders.toArray(ToolCallbackProvider[]::new));
        }

        return builder.build();
    }

    private ResolvedChatModelConfig resolveRuntimeConfig() {
        AgentConfig config = dynamicAgentConfigHolder.get();
        if (config == null) {
            return new ResolvedChatModelConfig(
                    "https://dashscope.aliyuncs.com/compatible-mode",
                    "",
                    "qwen3.5-plus",
                    0.7,
                    null,
                    null
            );
        }

        if (config.getLlmModelId() != null) {
            ModelProvider provider = modelProviderService.getModelEntityForRuntime(config.getLlmModelId(), ModelType.LLM);
            log.debug("使用管理员已配置模型执行对话: modelId={}, name={}", provider.getId(), provider.getName());
            return new ResolvedChatModelConfig(
                    provider.getApiUrl(),
                    provider.getApiKey(),
                    provider.getModelName(),
                    provider.getTemperature() != null ? provider.getTemperature().doubleValue() : safeTemperature(config.getTemperature()),
                    provider.getMaxTokens(),
                    provider.getTopP()
            );
        }

        return new ResolvedChatModelConfig(
                config.getApiUrl(),
                config.getApiKey(),
                config.getModelName(),
                safeTemperature(config.getTemperature()),
                null,
                null
        );
    }

    private double safeTemperature(Double temperature) {
        return temperature != null ? temperature : 0.7D;
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

    private record ResolvedChatModelConfig(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens,
            java.math.BigDecimal topP
    ) {
    }
}
