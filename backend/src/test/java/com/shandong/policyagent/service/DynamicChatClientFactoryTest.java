package com.shandong.policyagent.service;

import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.agent.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicChatClientFactoryTest {

    @Mock
    private DynamicAgentConfigHolder dynamicAgentConfigHolder;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private SecurityAdvisor securityAdvisor;

    private RuntimeChatModelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RuntimeChatModelProvider(null, dynamicAgentConfigHolder, modelProviderService);
        ReflectionTestUtils.setField(provider, "defaultBaseUrl", "https://dashscope.aliyuncs.com/compatible-mode");
        ReflectionTestUtils.setField(provider, "defaultApiKey", "fallback-api-key");
        ReflectionTestUtils.setField(provider, "defaultModelName", "qwen3.5-plus");
        ReflectionTestUtils.setField(provider, "defaultTemperature", 0.7D);
    }

    @Test
    void shouldFallbackToManualConfigWhenManagedModelCannotBeResolved() {
        AgentConfig config = AgentConfig.builder()
                .llmModelId(17L)
                .apiUrl("https://manual.example.com/compatible-mode")
                .apiKey("manual-api-key")
                .modelName("manual-model")
                .temperature(0.2D)
                .build();

        when(dynamicAgentConfigHolder.get()).thenReturn(config);
        when(modelProviderService.getModelEntityForRuntime(eq(17L), eq(ModelType.LLM)))
                .thenThrow(new IllegalStateException("API Key 解密失败"));

        RuntimeChatModelProvider.ResolvedChatModelConfig runtimeConfig = provider.resolveRuntimeConfig();

        assertEquals("https://manual.example.com/compatible-mode", runtimeConfig.baseUrl());
        assertEquals("manual-api-key", runtimeConfig.apiKey());
        assertEquals("manual-model", runtimeConfig.modelName());
        assertEquals(0.2D, runtimeConfig.temperature());
    }

    @Test
    void shouldFallbackToDefaultConfigWhenManagedAndManualConfigAreUnavailable() {
        AgentConfig config = AgentConfig.builder()
                .llmModelId(17L)
                .apiUrl("")
                .apiKey("")
                .modelName("")
                .temperature(null)
                .build();

        when(dynamicAgentConfigHolder.get()).thenReturn(config);
        when(modelProviderService.getModelEntityForRuntime(eq(17L), eq(ModelType.LLM)))
                .thenThrow(new IllegalStateException("API Key 解密失败"));

        RuntimeChatModelProvider.ResolvedChatModelConfig runtimeConfig = provider.resolveRuntimeConfig();

        assertEquals("https://dashscope.aliyuncs.com/compatible-mode", runtimeConfig.baseUrl());
        assertEquals("fallback-api-key", runtimeConfig.apiKey());
        assertEquals("qwen3.5-plus", runtimeConfig.modelName());
        assertEquals(0.7D, runtimeConfig.temperature());
    }

    @Test
    void shouldFallbackToDefaultConfigWhenManualApiKeyIsOnlyPlaceholder() {
        // 占位符形式 ${ENV} 在环境变量缺失时无法解析为真实 Key，
        // 因此手工配置不可用，应回退到系统默认配置
        AgentConfig config = AgentConfig.builder()
                .llmModelId(null)
                .apiUrl("https://manual.example.com/compatible-mode")
                .apiKey("${SOME_ENV_NOT_EXIST_FOR_TEST}")
                .modelName("manual-model")
                .temperature(0.2D)
                .build();

        when(dynamicAgentConfigHolder.get()).thenReturn(config);

        RuntimeChatModelProvider.ResolvedChatModelConfig runtimeConfig = provider.resolveRuntimeConfig();

        assertEquals("https://dashscope.aliyuncs.com/compatible-mode", runtimeConfig.baseUrl());
        assertEquals("fallback-api-key", runtimeConfig.apiKey());
        assertEquals("qwen3.5-plus", runtimeConfig.modelName());
    }

    @Test
    void shouldUseManualConfigWhenApiKeyIsConcrete() {
        AgentConfig config = AgentConfig.builder()
                .llmModelId(null)
                .apiUrl("https://manual.example.com/compatible-mode")
                .apiKey("concrete-api-key")
                .modelName("manual-model")
                .temperature(0.2D)
                .build();

        when(dynamicAgentConfigHolder.get()).thenReturn(config);

        RuntimeChatModelProvider.ResolvedChatModelConfig runtimeConfig = provider.resolveRuntimeConfig();

        assertEquals("https://manual.example.com/compatible-mode", runtimeConfig.baseUrl());
        assertEquals("concrete-api-key", runtimeConfig.apiKey());
        assertEquals("manual-model", runtimeConfig.modelName());
    }
}
