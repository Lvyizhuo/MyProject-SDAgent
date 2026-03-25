package com.shandong.policyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.repository.ModelProviderRepository;
import com.shandong.policyagent.rag.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelProviderOptionsTest {

    @Mock
    private ModelProviderRepository modelProviderRepository;

    @Mock
    private ApiKeyCipherService apiKeyCipherService;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Test
    void shouldIncludeBuiltInEmbeddingDefaultInAgentConfigOptions() {
        EmbeddingModelConfig embeddingConfig = new EmbeddingModelConfig();
        embeddingConfig.setDefaultModel("ollama:nomic-embed-text");
        embeddingConfig.setModels(List.of(embeddingModel("ollama:nomic-embed-text", "ollama", "nomic-embed-text:latest")));
        EmbeddingService embeddingService = new EmbeddingService(embeddingConfig, new ObjectMapper(), mock(RestClient.Builder.class));

        when(modelProviderRepository.findByTypeAndIsEnabled(ModelType.EMBEDDING, true)).thenReturn(List.of(
                ModelProvider.builder().id(9L).name("自定义嵌入").type(ModelType.EMBEDDING).isEnabled(true).isDefault(false).build()
        ));
        when(modelProviderRepository.findByTypeAndIsEnabled(ModelType.LLM, true)).thenReturn(List.of());
        when(modelProviderRepository.findByTypeAndIsEnabled(ModelType.VISION, true)).thenReturn(List.of());
        when(modelProviderRepository.findByTypeAndIsEnabled(ModelType.AUDIO, true)).thenReturn(List.of());

        ModelProviderService service = new ModelProviderService(
                modelProviderRepository,
                apiKeyCipherService,
                restClientBuilder,
                embeddingService
        );

        Map<String, List<ModelProviderService.ModelOption>> options = service.getModelOptions();

        assertEquals(2, options.get("EMBEDDING").size());
        assertTrue(Boolean.TRUE.equals(options.get("EMBEDDING").getFirst().getBuiltIn()));
        assertEquals("ollama:nomic-embed-text", options.get("EMBEDDING").getFirst().getBuiltinCode());
        assertEquals(null, options.get("EMBEDDING").getFirst().getId());
    }

    private EmbeddingModelConfig.EmbeddingModel embeddingModel(String id, String provider, String modelName) {
        EmbeddingModelConfig.EmbeddingModel model = new EmbeddingModelConfig.EmbeddingModel();
        model.setId(id);
        model.setProvider(provider);
        model.setModelName(modelName);
        return model;
    }
}
