package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.repository.AgentConfigRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.rag.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    private AgentConfigBindingSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new AgentConfigBindingSanitizer(
                agentConfigRepository,
                knowledgeFolderRepository,
                modelProviderService,
                embeddingService
        );
    }

    @Test
    void shouldClearBrokenLlmBindingWhenRuntimeModelCannotBeResolved() {
        AgentConfig config = AgentConfig.builder()
                .id(1L)
                .llmModelId(17L)
                .build();

        doThrow(new IllegalStateException("API Key 解密失败"))
                .when(modelProviderService).validateRuntimeModelBinding(eq(17L), eq(ModelType.LLM));
        when(agentConfigRepository.save(any(AgentConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentConfig sanitized = sanitizer.sanitizeAndPersistIfNeeded(config);

        assertNull(sanitized.getLlmModelId());
        verify(agentConfigRepository).save(config);
    }
}
