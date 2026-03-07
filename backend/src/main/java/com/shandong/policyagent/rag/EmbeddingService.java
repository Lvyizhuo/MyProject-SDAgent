package com.shandong.policyagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.config.EmbeddingModelConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public List<EmbeddingModelConfig.EmbeddingModel> getAvailableModels() {
        List<EmbeddingModelConfig.EmbeddingModel> models = embeddingModelConfig.getModels();
        long invalidCount = models.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getId() == null || m.getId().isBlank())
                .count();
        if (invalidCount > 0) {
            log.warn("Detected {} embedding model entries without id. They will be ignored.", invalidCount);
        }
        return models.stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getId() != null && !m.getId().isBlank())
                .toList();
    }

    public EmbeddingModelConfig.EmbeddingModel getDefaultModel() {
        return getModelConfig(embeddingModelConfig.getDefaultModel());
    }

    public EmbeddingModelConfig.EmbeddingModel getModelConfig(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Embedding model id is required");
        }
        return getAvailableModels().stream()
                .filter(m -> modelId.equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Embedding model not found: " + modelId));
    }

    private List<float[]> embedWithOllama(EmbeddingModelConfig.EmbeddingModel modelConfig, List<String> texts) {
        RestClient restClient = restClientBuilder.baseUrl(modelConfig.getBaseUrl()).build();
        List<float[]> embeddings = new ArrayList<>();

        for (String text : texts) {
            OllamaEmbedRequest request = new OllamaEmbedRequest();
            request.setModel(modelConfig.getModelName());
            request.setInput(text);
            if (modelConfig.getDimensions() != null && modelConfig.getDimensions() > 0) {
                request.setDimensions(modelConfig.getDimensions());
            }

            OllamaEmbedResponse response = restClient.post()
                    .uri("/api/embed")
                    .body(request)
                    .retrieve()
                    .body(OllamaEmbedResponse.class);

            if (response != null
                    && response.getEmbeddings() != null
                    && !response.getEmbeddings().isEmpty()
                    && response.getEmbeddings().get(0) != null) {
                float[] vector = toFloatArray(response.getEmbeddings().get(0));
                validateEmbeddingDimensions(modelConfig, vector.length);
                embeddings.add(vector);
            } else {
                throw new RuntimeException("Failed to get embedding from Ollama");
            }
        }

        return embeddings;
    }

    private List<float[]> embedWithDashScope(EmbeddingModelConfig.EmbeddingModel modelConfig, List<String> texts) {
        RestClient restClient = restClientBuilder.build();
        List<float[]> embeddings = new ArrayList<>();
        String apiKey = resolveApiKey(modelConfig.getApiKey());
        String embeddingUrl = buildDashScopeEmbeddingUrl(modelConfig.getBaseUrl());
        log.debug("DashScope embedding request url: {} | model={}", embeddingUrl, modelConfig.getModelName());

        for (String text : texts) {
            Map<String, Object> request = new HashMap<>();
            request.put("model", modelConfig.getModelName());
            request.put("input", text);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(embeddingUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (!data.isEmpty() && data.get(0).containsKey("embedding")) {
                    @SuppressWarnings("unchecked")
                    List<Double> embeddingList = (List<Double>) data.get(0).get("embedding");
                    float[] vector = toFloatArray(embeddingList);
                    validateEmbeddingDimensions(modelConfig, vector.length);
                    embeddings.add(vector);
                } else {
                    throw new RuntimeException("Failed to get embedding from DashScope");
                }
            } else {
                throw new RuntimeException("Failed to get embedding from DashScope");
            }
        }

        return embeddings;
    }

    private String buildDashScopeEmbeddingUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/compatible-mode")) {
            return normalized + "/v1/embeddings";
        }
        return normalized + "/compatible-mode/v1/embeddings";
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

    private void validateEmbeddingDimensions(EmbeddingModelConfig.EmbeddingModel modelConfig, int actualDimensions) {
        Integer expectedDimensions = modelConfig.getDimensions();
        if (expectedDimensions != null && expectedDimensions > 0 && actualDimensions != expectedDimensions) {
            throw new IllegalStateException(String.format(
                    "Embedding dimension mismatch for model %s: expected %d but got %d",
                    modelConfig.getId(), expectedDimensions, actualDimensions
            ));
        }
    }

    @Data
    private static class OllamaEmbedRequest {
        private String model;
        private String input;
        private Integer dimensions;
    }

    @Data
    private static class OllamaEmbedResponse {
        private List<List<Double>> embeddings;
    }
}
