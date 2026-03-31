package com.shandong.policyagent.rag;

import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.service.ModelProviderService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeRerankService {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    private final RagConfig ragConfig;
    private final ModelProviderService modelProviderService;

    public List<Document> rerank(String query, List<Document> documents, int topK) {
        return rerank(query, documents, topK, null, null);
    }

    public List<Document> rerank(String query,
                                 List<Document> documents,
                                 int topK,
                                 Long rerankModelId,
                                 String rerankModelName) {
        if (!ragConfig.getRetrieval().isRerankEnabled()) {
            log.debug("Rerank 未启用，回退纯向量结果");
            return shrink(documents, topK);
        }
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return shrink(documents, topK);
        }

        RerankRuntime runtime = resolveRuntime(rerankModelId, rerankModelName);
        if (runtime.apiKey() == null || runtime.apiKey().isBlank()) {
            log.warn("Rerank 被跳过：API Key 为空，回退纯向量检索 | model={}", runtime.modelName());
            return shrink(documents, topK);
        }

        try {
            List<String> docTexts = buildRerankDocs(documents);
            if (docTexts.isEmpty()) {
                return shrink(documents, topK);
            }

            log.debug("发起 Rerank 调用 | model={} | endpoint={} | docs={} | topK={}",
                    runtime.modelName(),
                    runtime.endpoint(),
                    docTexts.size(),
                    Math.min(topK, documents.size()));

            Map<String, Object> response = callRerankEndpoint(runtime.endpoint(), runtime, query, docTexts, topK);

            List<ScoredIndex> scores = parseScores(response);
            if (scores.isEmpty()) {
                return shrink(documents, topK);
            }

            List<Document> reranked = new ArrayList<>();
            for (ScoredIndex item : scores) {
                if (item.index < 0 || item.index >= documents.size()) {
                    continue;
                }
                Document original = documents.get(item.index);
                Map<String, Object> metadata = original.getMetadata() == null
                        ? new HashMap<>()
                        : new HashMap<>(original.getMetadata());
                metadata.put("rerankScore", item.score);
                reranked.add(new Document(original.getId(), original.getText(), metadata));
            }
            if (reranked.isEmpty()) {
                return shrink(documents, topK);
            }
            return shrink(reranked, topK);
        } catch (Exception e) {
            String fallbackEndpoint = buildDashScopeApiFallbackEndpoint(runtime.endpoint());
            if (isNotFoundError(e)
                    && fallbackEndpoint != null
                    && !fallbackEndpoint.equals(runtime.endpoint())) {
                try {
                    log.warn("Rerank 调用返回 404，切换备用 endpoint 重试 | model={} | from={} | to={}",
                            runtime.modelName(), runtime.endpoint(), fallbackEndpoint);
                    Map<String, Object> retriedResponse = callRerankEndpoint(
                            fallbackEndpoint,
                            runtime,
                            query,
                            buildRerankDocs(documents),
                            topK
                    );
                    List<ScoredIndex> retryScores = parseScores(retriedResponse);
                    if (!retryScores.isEmpty()) {
                        List<Document> reranked = new ArrayList<>();
                        for (ScoredIndex item : retryScores) {
                            if (item.index < 0 || item.index >= documents.size()) {
                                continue;
                            }
                            Document original = documents.get(item.index);
                            Map<String, Object> metadata = original.getMetadata() == null
                                    ? new HashMap<>()
                                    : new HashMap<>(original.getMetadata());
                            metadata.put("rerankScore", item.score);
                            reranked.add(new Document(original.getId(), original.getText(), metadata));
                        }
                        if (!reranked.isEmpty()) {
                            return shrink(reranked, topK);
                        }
                    }
                } catch (Exception retryException) {
                    log.warn("Rerank 备用 endpoint 调用失败，回退纯向量检索 | model={} | endpoint={} | error={}",
                            runtime.modelName(), fallbackEndpoint, retryException.getMessage());
                }
            }

            log.warn("Rerank 调用失败，回退为纯向量检索 | model={} | endpoint={} | error={}",
                    runtime.modelName(),
                    runtime.endpoint(),
                    e.getMessage());
            return shrink(documents, topK);
        }
    }

    private Map<String, Object> callRerankEndpoint(String endpoint,
                                                   RerankRuntime runtime,
                                                   String query,
                                                   List<String> docTexts,
                                                   int topK) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", runtime.modelName());
        body.put("input", Map.of(
                "query", query,
                "documents", docTexts
        ));
        body.put("parameters", Map.of(
                "top_n", Math.min(topK, docTexts.size()),
                "return_documents", false
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = RestClient.create()
                .post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + runtime.apiKey())
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
        return response == null ? Map.of() : response;
    }

    private RerankRuntime resolveRuntime(Long rerankModelId, String rerankModelName) {
        if (rerankModelId != null) {
            try {
                ModelProvider model = modelProviderService.getModelEntityForRuntime(rerankModelId, ModelType.RERANK);
                String endpoint = normalizeRerankEndpoint(model.getApiUrl());
                return new RerankRuntime(model.getModelName(), endpoint, model.getApiKey());
            } catch (Exception exception) {
                log.warn("知识库绑定的重排模型不可用，回退到系统配置 | modelId={} | error={}", rerankModelId, exception.getMessage());
            }
        }

        String modelName = (rerankModelName != null && !rerankModelName.isBlank())
                ? rerankModelName
                : ragConfig.getRetrieval().getRerankModel();
        return new RerankRuntime(modelName, normalizeRerankEndpoint(ragConfig.getRetrieval().getRerankEndpoint()), apiKey);
    }

    private String normalizeRerankEndpoint(String endpointOrBaseUrl) {
        if (endpointOrBaseUrl == null || endpointOrBaseUrl.isBlank()) {
            return ragConfig.getRetrieval().getRerankEndpoint();
        }
        String normalized = endpointOrBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.contains("dashscope.aliyuncs.com/compatible-mode/v1/services/rerank/")) {
            return normalized.replace("/compatible-mode/v1/services/rerank/", "/api/v1/services/rerank/");
        }
        if (normalized.contains("dashscope.aliyuncs.com/compatible-mode/v1")) {
            return normalized.replace("/compatible-mode/v1", "/api/v1")
                    + "/services/rerank/text-rerank/text-rerank";
        }
        if (normalized.contains("dashscope.aliyuncs.com/compatible-mode")) {
            return normalized.replace("/compatible-mode", "/api/v1")
                    + "/services/rerank/text-rerank/text-rerank";
        }

        if (normalized.contains("/services/rerank/")) {
            return normalized;
        }
        return normalized + "/services/rerank/text-rerank/text-rerank";
    }

    private String buildDashScopeApiFallbackEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String normalized = endpoint.trim();
        if (normalized.contains("dashscope.aliyuncs.com/compatible-mode/")) {
            return normalized.replace("/compatible-mode/", "/api/");
        }
        return null;
    }

    private boolean isNotFoundError(Exception exception) {
        if (exception instanceof HttpClientErrorException.NotFound) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.contains("404");
    }

    private List<String> buildRerankDocs(List<Document> documents) {
        int maxChars = ragConfig.getRetrieval().getRerankMaxDocChars();
        List<String> results = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            String text = doc.getText() == null ? "" : doc.getText().trim();
            if (text.isBlank()) {
                text = String.valueOf(doc.getMetadata().getOrDefault("title", ""));
            }
            if (text.isBlank()) {
                text = String.valueOf(doc.getMetadata().getOrDefault("source", ""));
            }
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars);
            }
            results.add(text);
        }
        return results;
    }

    private List<ScoredIndex> parseScores(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object outputObj = response.get("output");
        if (!(outputObj instanceof Map<?, ?> output)) {
            return List.of();
        }
        Object resultsObj = output.get("results");
        if (!(resultsObj instanceof List<?> resultList)) {
            return List.of();
        }

        List<ScoredIndex> scores = new ArrayList<>();
        for (Object rowObj : resultList) {
            if (!(rowObj instanceof Map<?, ?> row)) {
                continue;
            }
            int index = toInt(row.get("index"), -1);
            double score = toDouble(row.get("relevance_score"), 0.0);
            if (index >= 0) {
                scores.add(new ScoredIndex(index, score));
            }
        }
        scores.sort(Comparator.comparingDouble((ScoredIndex s) -> s.score).reversed());
        return scores;
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private List<Document> shrink(List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        if (documents.size() <= limit) {
            return documents;
        }
        return documents.subList(0, limit);
    }

    private record ScoredIndex(int index, double score) {
    }

    private record RerankRuntime(String modelName, String endpoint, String apiKey) {
    }
}
