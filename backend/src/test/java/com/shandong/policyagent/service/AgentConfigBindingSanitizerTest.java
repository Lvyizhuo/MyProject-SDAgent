package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.repository.AgentConfigRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.rag.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConfigBindingSanitizerTest {

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private KnowledgeFolderRepository knowledgeFolderRepository;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private EmbeddingService embeddingService;

    @Test
    void shouldClearMissingEmbeddingBindingAndPersistConfig() {
        AgentConfig config = baseConfig();
        config.setEmbeddingModelId(14L);

        when(modelProviderService.getModelEntity(14L))
                .thenThrow(new IllegalArgumentException("模型不存在: 14"));
        when(agentConfigRepository.save(any(AgentConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentConfigBindingSanitizer sanitizer = new AgentConfigBindingSanitizer(
                agentConfigRepository,
                knowledgeFolderRepository,
                modelProviderService,
                embeddingService
        );

        AgentConfig sanitized = sanitizer.sanitizeAndPersistIfNeeded(config);

        assertNull(sanitized.getEmbeddingModelId());
        verify(agentConfigRepository).save(config);
    }

    @Test
    void shouldClearUnmappedEmbeddingBindingAndMissingKnowledgeFolder() {
        AgentConfig config = baseConfig();
        config.setEmbeddingModelId(15L);
        config.setKnowledgeBaseFolderId(88L);

        ModelProvider provider = ModelProvider.builder()
                .id(15L)
                .name("自定义嵌入")
                .type(ModelType.EMBEDDING)
                .provider("openai")
                .modelName("text-embedding-3-large")
                .isEnabled(true)
                .build();

        when(modelProviderService.getModelEntity(15L)).thenReturn(provider);
        when(embeddingService.findMappedModelId(provider)).thenReturn(null);
        when(knowledgeFolderRepository.findById(88L)).thenReturn(Optional.empty());
        when(agentConfigRepository.save(any(AgentConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentConfigBindingSanitizer sanitizer = new AgentConfigBindingSanitizer(
                agentConfigRepository,
                knowledgeFolderRepository,
                modelProviderService,
                embeddingService
        );

        AgentConfig sanitized = sanitizer.sanitizeAndPersistIfNeeded(config);

        assertNull(sanitized.getEmbeddingModelId());
        assertNull(sanitized.getKnowledgeBaseFolderId());
        verify(agentConfigRepository).save(config);
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
