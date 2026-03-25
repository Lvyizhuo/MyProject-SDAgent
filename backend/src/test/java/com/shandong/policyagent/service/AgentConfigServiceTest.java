package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.model.admin.AgentConfigResponse;
import com.shandong.policyagent.repository.AgentConfigRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.rag.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConfigServiceTest {

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private KnowledgeFolderRepository knowledgeFolderRepository;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private AgentConfigBindingSanitizer agentConfigBindingSanitizer;

    @Mock
    private EmbeddingService embeddingService;

    @Test
    void shouldReturnSanitizedBindingsInCurrentConfigResponse() {
        AgentConfig persistedConfig = baseConfig();
        persistedConfig.setEmbeddingModelId(14L);

        AgentConfig sanitizedConfig = baseConfig();
        sanitizedConfig.setEmbeddingModelId(null);

        when(agentConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(persistedConfig));
        when(agentConfigBindingSanitizer.sanitizeAndPersistIfNeeded(persistedConfig)).thenReturn(sanitizedConfig);

        AgentConfigService service = new AgentConfigService(
                agentConfigRepository,
                knowledgeFolderRepository,
                modelProviderService,
                agentConfigBindingSanitizer,
                embeddingService
        );

        AgentConfigResponse response = service.getCurrentConfig();

        assertNull(response.getEmbeddingModelId());
        assertNull(response.getResolvedEmbeddingModel());
    }

    private AgentConfig baseConfig() {
        LocalDateTime now = LocalDateTime.now();
        return AgentConfig.builder()
                .id(1L)
                .name("政策问答智能体")
                .description("测试配置")
                .modelProvider("dashscope")
                .apiKey("test-key")
                .apiUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .modelName("qwen3.5-plus")
                .temperature(0.7)
                .systemPrompt("system")
                .greetingMessage("hello")
                .skills("{}")
                .mcpServersConfig("[]")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
