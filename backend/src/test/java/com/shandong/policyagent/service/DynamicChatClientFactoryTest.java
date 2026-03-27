package com.shandong.policyagent.service;

import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicChatClientFactoryTest {

    @Mock
    private OpenAiChatModel openAiChatModel;

    @Mock
    private DynamicAgentConfigHolder dynamicAgentConfigHolder;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private SecurityAdvisor securityAdvisor;

    private DynamicChatClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DynamicChatClientFactory(
                openAiChatModel,
                dynamicAgentConfigHolder,
                modelProviderService,
                securityAdvisor,
                null,
                null,
                null,
                null,
                java.util.List.of()
        );
        ReflectionTestUtils.setField(factory, "defaultBaseUrl", "https://dashscope.aliyuncs.com/compatible-mode");
        ReflectionTestUtils.setField(factory, "defaultApiKey", "fallback-api-key");
        ReflectionTestUtils.setField(factory, "defaultModelName", "qwen3.5-plus");
        ReflectionTestUtils.setField(factory, "defaultTemperature", 0.7D);
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

        DynamicChatClientFactory.ResolvedChatModelConfig runtimeConfig = factory.resolveRuntimeConfig();

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

        DynamicChatClientFactory.ResolvedChatModelConfig runtimeConfig = factory.resolveRuntimeConfig();

        assertEquals("https://dashscope.aliyuncs.com/compatible-mode", runtimeConfig.baseUrl());
        assertEquals("fallback-api-key", runtimeConfig.apiKey());
        assertEquals("qwen3.5-plus", runtimeConfig.modelName());
        assertEquals(0.7D, runtimeConfig.temperature());
    }
}
