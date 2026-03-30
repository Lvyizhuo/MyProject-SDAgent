package com.shandong.policyagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.config.EmbeddingModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class EmbeddingServiceTest {

    @Test
    void shouldFallbackToBuiltInOllamaModelWhenConfiguredDefaultIsBlank() {
        EmbeddingModelConfig config = new EmbeddingModelConfig();
        config.setDefaultModel("");
        config.setModels(List.of(
            model("ollama:all-minilm", "ollama", "all-minilm:latest"),
                model("dashscope:text-embedding-v3", "dashscope", "text-embedding-v3")
        ));

        EmbeddingService service = new EmbeddingService(config, new ObjectMapper(), mock(RestClient.Builder.class));

        assertEquals("ollama:all-minilm", service.resolveDefaultModelId(null));
        assertEquals("ollama:all-minilm", service.getDefaultModel().getId());
    }

    @Test
    void shouldRejectUnknownExplicitDefaultEmbeddingModel() {
        EmbeddingModelConfig config = new EmbeddingModelConfig();
        config.setDefaultModel("ollama:all-minilm");
        config.setModels(List.of(model("ollama:all-minilm", "ollama", "all-minilm:latest")));

        EmbeddingService service = new EmbeddingService(config, new ObjectMapper(), mock(RestClient.Builder.class));

        assertThrows(IllegalArgumentException.class, () -> service.validateOrDefaultModelId("unknown:model"));
    }

    @Test
    void shouldPreferExplicitValidModelIdOverFallback() {
        EmbeddingModelConfig config = new EmbeddingModelConfig();
        config.setDefaultModel("dashscope:text-embedding-v3");
        config.setModels(List.of(
            model("ollama:all-minilm", "ollama", "all-minilm:latest"),
                model("dashscope:text-embedding-v3", "dashscope", "text-embedding-v3")
        ));

        EmbeddingService service = new EmbeddingService(config, new ObjectMapper(), mock(RestClient.Builder.class));

        assertEquals("dashscope:text-embedding-v3", service.resolveDefaultModelId("dashscope:text-embedding-v3"));
    }

    @Test
    void shouldUseConfiguredMaxInputCharsForDefaultModel() {
        EmbeddingModelConfig config = new EmbeddingModelConfig();
        config.setDefaultModel("ollama:all-minilm");
        config.setModels(List.of(
            model("ollama:all-minilm", "ollama", "all-minilm:latest", 900)
        ));

        EmbeddingService service = new EmbeddingService(config, new ObjectMapper(), mock(RestClient.Builder.class));

        assertEquals(900, service.resolveMaxInputChars(null));
    }

    private EmbeddingModelConfig.EmbeddingModel model(String id, String provider, String modelName) {
        return model(id, provider, modelName, null);
    }

    private EmbeddingModelConfig.EmbeddingModel model(String id, String provider, String modelName, Integer maxInputChars) {
        EmbeddingModelConfig.EmbeddingModel model = new EmbeddingModelConfig.EmbeddingModel();
        model.setId(id);
        model.setProvider(provider);
        model.setModelName(modelName);
        model.setMaxInputChars(maxInputChars);
        return model;
    }
}
