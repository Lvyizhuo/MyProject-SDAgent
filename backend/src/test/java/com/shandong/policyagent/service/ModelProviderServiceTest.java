package com.shandong.policyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.model.ModelProviderResponse;
import com.shandong.policyagent.rag.EmbeddingService;
import com.shandong.policyagent.repository.ModelProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelProviderServiceTest {

    @Mock
    private ModelProviderRepository modelProviderRepository;

    @Mock
    private ApiKeyCipherService apiKeyCipherService;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Test
    void shouldExposeBuiltInEmbeddingModelsInModelManagementList() {
        EmbeddingModelConfig embeddingConfig = new EmbeddingModelConfig();
        embeddingConfig.setDefaultModel("ollama:nomic-embed-text");
        embeddingConfig.setModels(List.of(
                embeddingModel("ollama:nomic-embed-text", "ollama", "nomic-embed-text:latest", 768)
        ));
        EmbeddingService embeddingService = new EmbeddingService(embeddingConfig, new ObjectMapper(), mock(RestClient.Builder.class));

        when(modelProviderRepository.findByType(ModelType.EMBEDDING)).thenReturn(List.of(
                ModelProvider.builder()
                        .id(1L)
                        .name("自定义嵌入")
                        .type(ModelType.EMBEDDING)
                        .provider("dashscope")
                        .apiUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .modelName("text-embedding-v3")
                        .temperature(new BigDecimal("0.7"))
                        .isDefault(false)
                        .isEnabled(true)
                        .build()
        ));

        ModelProviderService service = new ModelProviderService(
                modelProviderRepository,
                apiKeyCipherService,
                restClientBuilder,
                embeddingService
        );

        List<ModelProviderResponse> models = service.getAllModels(ModelType.EMBEDDING);

        assertEquals(2, models.size());
        assertTrue(models.stream().anyMatch(model ->
                Boolean.TRUE.equals(model.getBuiltIn())
                        && "ollama:nomic-embed-text".equals(model.getBuiltinCode())
                        && Boolean.TRUE.equals(model.getIsDefault())
        ));
    }

    private EmbeddingModelConfig.EmbeddingModel embeddingModel(String id, String provider, String modelName, int dimensions) {
        EmbeddingModelConfig.EmbeddingModel model = new EmbeddingModelConfig.EmbeddingModel();
        model.setId(id);
        model.setProvider(provider);
        model.setModelName(modelName);
        model.setDimensions(dimensions);
        return model;
    }
}
