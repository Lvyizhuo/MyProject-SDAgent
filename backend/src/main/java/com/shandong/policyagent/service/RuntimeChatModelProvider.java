package com.shandong.policyagent.service;

import com.shandong.policyagent.agent.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时模型解析器：把「当前生效的 LLM 配置」统一收敛到一处。
 *
 * <p><b>解决的问题</b>：此前只有 {@link DynamicChatClientFactory} 会走运行时配置，
 * 而 {@code RedisChatMemory}（后台摘要）与 {@code ReActPlanningService}（ReAct 规划）
 * 直接注入了 Spring 默认的 {@code ChatModel} Bean —— 那个 Bean 用的是
 * {@code spring.ai.openai.api-key}（即固定的 {@code DASHSCOPE_API_KEY}），
 * 与管理员在管理台配置的模型（{@code agent_config.llm_model_id} → {@code model_provider}）无关。
 * 一旦管理员配的是别的模型/别的 Key，这两处就会拿着失效的默认 Key 去调模型，
 * 稳定返回 401 {@code invalid_api_key}。
 *
 * <p><b>另一个作用</b>：按 (baseUrl, model, temperature, maxTokens, topP) 缓存构建好的
 * {@link OpenAiChatModel} / {@link OpenAiApi}，避免每请求新建连接池导致每次都重新 TLS 握手。
 *
 * <p>本类不依赖 {@code ChatMemory} / {@code ChatClient} 体系，因此不会形成循环依赖。
 */
@Slf4j
@Component
public class RuntimeChatModelProvider {

    /** 运行时模型配置（已解密）。 */
    public record ResolvedChatModelConfig(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens,
            BigDecimal topP
    ) {
    }

    private final OpenAiChatModel openAiChatModel;
    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final ModelProviderService modelProviderService;

    @Value("${app.model-provider.openai.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${app.model-provider.openai.read-timeout-seconds:90}")
    private int readTimeoutSeconds;

    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String defaultBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen3.5-plus}")
    private String defaultModelName;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double defaultTemperature;

    /** 已构建模型的缓存：key 为 (baseUrl, modelName, temperature, maxTokens, topP)。 */
    private final ConcurrentHashMap<String, OpenAiChatModel> modelCache = new ConcurrentHashMap<>();

    public RuntimeChatModelProvider(OpenAiChatModel openAiChatModel,
                                    DynamicAgentConfigHolder dynamicAgentConfigHolder,
                                    ModelProviderService modelProviderService) {
        this.openAiChatModel = openAiChatModel;
        this.dynamicAgentConfigHolder = dynamicAgentConfigHolder;
        this.modelProviderService = modelProviderService;
    }

    // ==================== 配置解析 ====================

    /**
     * 解析当前生效的运行时模型配置。
     * <p>优先级：管理员配置的托管模型 → 手工填写的模型配置 → 系统默认。
     */
    public ResolvedChatModelConfig resolveRuntimeConfig() {
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

    // ==================== 模型构建（带缓存） ====================

    /**
     * 按运行时配置构建 {@link ChatModel}。相同配置复用同一实例（复用 HTTP 连接池，避免重复 TLS 握手）。
     */
    public ChatModel buildChatModel() {
        return buildChatModel(resolveRuntimeConfig());
    }

    public ChatModel buildChatModel(ResolvedChatModelConfig config) {
        String cacheKey = cacheKey(config);
        return modelCache.computeIfAbsent(cacheKey, key -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeoutSeconds * 1000);
            requestFactory.setReadTimeout(readTimeoutSeconds * 1000);

            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(normalizeOpenAiBaseUrl(config.baseUrl()))
                    .apiKey(config.apiKey())
                    .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                    .build();

            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(config.modelName())
                    .temperature(config.temperature());
            if (config.maxTokens() != null) {
                optionsBuilder.maxTokens(config.maxTokens());
            }
            if (config.topP() != null) {
                optionsBuilder.topP(config.topP().doubleValue());
            }

            log.debug("构建运行时 ChatModel | model={} | baseUrl={}", config.modelName(), config.baseUrl());
            return openAiChatModel.mutate()
                    .openAiApi(api)
                    .defaultOptions(optionsBuilder.build())
                    .build();
        });
    }

    /**
     * 使缓存失效：管理员改了模型配置后调用，下次构建会重建实例。
     */
    public void invalidateCache() {
        modelCache.clear();
    }

    private String cacheKey(ResolvedChatModelConfig config) {
        return String.join("|",
                String.valueOf(config.baseUrl()),
                String.valueOf(config.modelName()),
                String.valueOf(config.temperature()),
                String.valueOf(config.maxTokens()),
                String.valueOf(config.topP()));
    }

    // ==================== 配置构造 ====================

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

    /** 把 {@code ${ENV_VAR}} 占位符替换为真实环境变量值。 */
    public String resolveApiKey(String apiKey) {
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

    private double safeTemperature(Double temperature) {
        return temperature != null ? temperature : 0.7D;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 去掉末尾斜杠与 {@code /v1} 后缀（Spring AI 会自行拼接路径）。 */
    public String normalizeOpenAiBaseUrl(String baseUrl) {
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
}
