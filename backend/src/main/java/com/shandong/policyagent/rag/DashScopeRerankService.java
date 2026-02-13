package com.shandong.policyagent.rag;

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
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeRerankService {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    private final RagConfig ragConfig;

    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (!ragConfig.getRetrieval().isRerankEnabled()
                || query == null
                || query.isBlank()
                || documents == null
                || documents.isEmpty()
                || apiKey == null
                || apiKey.isBlank()) {
            return shrink(documents, topK);
        }

        try {
            List<String> docTexts = buildRerankDocs(documents);
            if (docTexts.isEmpty()) {
                return shrink(documents, topK);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", ragConfig.getRetrieval().getRerankModel());
            body.put("input", Map.of(
                    "query", query,
                    "documents", docTexts
            ));
            body.put("parameters", Map.of(
                    "top_n", Math.min(topK, documents.size()),
                    "return_documents", false
            ));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = RestClient.create()
                    .post()
                    .uri(ragConfig.getRetrieval().getRerankEndpoint())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

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
                Map<String, Object> metadata = new HashMap<>(original.getMetadata());
                metadata.put("rerankScore", item.score);
                reranked.add(new Document(original.getId(), original.getText(), metadata));
            }
            if (reranked.isEmpty()) {
                return shrink(documents, topK);
            }
            return shrink(reranked, topK);
        } catch (Exception e) {
            log.warn("Rerank 调用失败，回退为纯向量检索: {}", e.getMessage());
            return shrink(documents, topK);
        }
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

    @SuppressWarnings("unchecked")
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
}
