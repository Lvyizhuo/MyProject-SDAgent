package com.shandong.policyagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModelConfig embeddingModelConfig;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    private final Map<String, EmbeddingModel> modelCache = new HashMap<>();

    public List<float[]> embedDocuments(String modelId, List<Document> documents) {
        List<String> texts = documents.stream()
                .map(Document::getText)
                .toList();
        return embedTexts(modelId, texts);
    }

    public List<float[]> embedTexts(String modelId, List<String> texts) {
        EmbeddingModelConfig.EmbeddingModel modelConfig = getModelConfig(modelId);

        if ("ollama".equalsIgnoreCase(modelConfig.getProvider())) {
            return embedWithOllama(modelConfig, texts);
        } else if ("dashscope".equalsIgnoreCase(modelConfig.getProvider())) {
            return embedWithDashScope(modelConfig, texts);
        }

        throw new IllegalArgumentException("Unsupported embedding provider: " + modelConfig.getProvider());
    }

    public EmbeddingModel getSpringAiEmbeddingModel(String modelId) {
        return modelCache.computeIfAbsent(modelId, id -> {
            EmbeddingModelConfig.EmbeddingModel modelConfig = getModelConfig(id);
            if ("dashscope".equalsIgnoreCase(modelConfig.getProvider())) {
                String apiKey = resolveApiKey(modelConfig.getApiKey());
                OpenAiApi openAiApi = new OpenAiApi(modelConfig.getBaseUrl(), apiKey);
                return new OpenAiEmbeddingModel(openAiApi,
                        OpenAiEmbeddingOptions.builder()
                                .withModel(modelConfig.getModelName())
                                .build());
            }
            throw new IllegalArgumentException("Spring AI EmbeddingModel not supported for provider: " + modelConfig.getProvider());
        });
    }

    public List<EmbeddingModelConfig.EmbeddingModel> getAvailableModels() {
        return embeddingModelConfig.getModels();
    }

    public EmbeddingModelConfig.EmbeddingModel getDefaultModel() {
        return getModelConfig(embeddingModelConfig.getDefaultModel());
    }

    public EmbeddingModelConfig.EmbeddingModel getModelConfig(String modelId) {
        return embeddingModelConfig.getModels().stream()
                .filter(m -> m.getId().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Embedding model not found: " + modelId));
    }

    private List<float[]> embedWithOllama(EmbeddingModelConfig.EmbeddingModel modelConfig, List<String> texts) {
        RestClient restClient = restClientBuilder.baseUrl(modelConfig.getBaseUrl()).build();
        List<float[]> embeddings = new ArrayList<>();

        for (String text : texts) {
            OllamaEmbeddingRequest request = new OllamaEmbeddingRequest();
            request.setModel(modelConfig.getModelName());
            request.setPrompt(text);

            OllamaEmbeddingResponse response = restClient.post()
                    .uri("/api/embeddings")
                    .body(request)
                    .retrieve()
                    .body(OllamaEmbeddingResponse.class);

            if (response != null && response.getEmbedding() != null) {
                embeddings.add(toFloatArray(response.getEmbedding()));
            } else {
                throw new RuntimeException("Failed to get embedding from Ollama");
            }
        }

        return embeddings;
    }

    private List<float[]> embedWithDashScope(EmbeddingModelConfig.EmbeddingModel modelConfig, List<String> texts) {
        EmbeddingModel embeddingModel = getSpringAiEmbeddingModel(modelConfig.getId());
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            float[] embedding = embeddingModel.embed(text);
            embeddings.add(embedding);
        }
        return embeddings;
    }

    private String resolveApiKey(String apiKey) {
        if (apiKey != null && apiKey.startsWith("${") && apiKey.endsWith("}")) {
            String envVar = apiKey.substring(2, apiKey.length() - 1);
            String value = System.getenv(envVar);
            if (value != null) {
                return value;
            }
        }
        return apiKey;
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }

    @Data
    private static class OllamaEmbeddingRequest {
        private String model;
        private String prompt;
    }

    @Data
    private static class OllamaEmbeddingResponse {
        private List<Double> embedding;
    }
}
