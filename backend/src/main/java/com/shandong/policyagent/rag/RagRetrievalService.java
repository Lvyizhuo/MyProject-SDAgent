package com.shandong.policyagent.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private static final int DEFAULT_RRF_K = 60;
    private static final double VECTOR_RRF_WEIGHT = 0.65;
    private static final double KEYWORD_RRF_WEIGHT = 0.35;

    private final RuntimeRagVectorStore runtimeRagVectorStore;
    private final MultiVectorStoreService multiVectorStoreService;
    private final DashScopeRerankService rerankService;
    private final RagConfig ragConfig;
    private final KnowledgeService knowledgeService;

    public List<Document> retrieveRelevantDocuments(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        RuntimeRagVectorStore.RetrievalContext context = runtimeRagVectorStore.resolveRetrievalContext();
        if (context.missing()) {
            log.warn("知识库范围配置异常，返回空召回结果 | folderId={}", context.folderId());
            return List.of();
        }

        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();
        int finalTopK = Math.max(1, retrievalConfig.getTopK());
        int candidateTopK = Math.max(finalTopK, retrievalConfig.getCandidateTopK());

        List<Document> vectorCandidates = multiVectorStoreService.similaritySearch(
                context.embeddingModelId(),
                query,
                candidateTopK,
                context.folderPath(),
                "child"
        );

        if (vectorCandidates.isEmpty()) {
            vectorCandidates = multiVectorStoreService.similaritySearch(
                    context.embeddingModelId(),
                    query,
                    candidateTopK,
                    context.folderPath(),
                    null
            );
        }

        List<Document> keywordCandidates = multiVectorStoreService.keywordSearch(
                context.embeddingModelId(),
                query,
                candidateTopK,
                context.folderPath(),
                "child"
        );

        List<Document> fusedCandidates = fuseByRrf(vectorCandidates, keywordCandidates, candidateTopK);
        List<Document> reranked = rerankService.rerank(
            query,
            fusedCandidates,
            finalTopK,
            context.rerankModelId(),
            context.rerankModelName()
        );
        List<Document> thresholdFiltered = applyRerankThreshold(reranked, knowledgeService.resolveRerankScoreThreshold());
        List<Document> expanded = expandChildHitsToParent(context.vectorTableName(), thresholdFiltered, finalTopK);

        log.debug("检索完成 | query={} | vector={} | keyword={} | fused={} | reranked={} | expanded={}",
                truncateQuery(query),
                vectorCandidates.size(),
                keywordCandidates.size(),
                fusedCandidates.size(),
                thresholdFiltered.size(),
                expanded.size());

        return expanded;
    }

    public List<Document> retrieveWithFilter(String query, String filterExpression) {
        return retrieveRelevantDocuments(query);
    }

    public List<Document> retrieveByCategory(String query, String category) {
        return retrieveRelevantDocuments(query);
    }

    public String buildContextFromDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是相关的政策文档内容：\n\n");

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("【文档 ").append(i + 1).append("】\n");
            context.append(doc.getText());
            context.append("\n\n");
        }

        return context.toString();
    }

    private List<Document> fuseByRrf(List<Document> vectorCandidates,
                                     List<Document> keywordCandidates,
                                     int limit) {
        Map<String, FusedCandidate> fused = new LinkedHashMap<>();

        mergeWithRrf(fused, vectorCandidates, VECTOR_RRF_WEIGHT);
        mergeWithRrf(fused, keywordCandidates, KEYWORD_RRF_WEIGHT);

        if (fused.isEmpty()) {
            return List.of();
        }

        List<FusedCandidate> sorted = fused.values().stream()
                .sorted(Comparator
                        .comparingDouble(FusedCandidate::fusedScore)
                        .reversed()
                        .thenComparingInt(FusedCandidate::bestRank))
                .toList();

        List<Document> result = new ArrayList<>();
        int maxSize = Math.max(1, limit);
        for (int i = 0; i < sorted.size() && result.size() < maxSize; i++) {
            FusedCandidate candidate = sorted.get(i);
            Map<String, Object> metadata = candidate.document().getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(candidate.document().getMetadata());
            metadata.put("hybridFusedScore", candidate.fusedScore());
            result.add(new Document(candidate.document().getId(), candidate.document().getText(), metadata));
        }

        return result;
    }

    private void mergeWithRrf(Map<String, FusedCandidate> fused,
                              List<Document> candidates,
                              double weight) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (int rank = 0; rank < candidates.size(); rank++) {
            Document document = candidates.get(rank);
            if (document == null || document.getId() == null || document.getId().isBlank()) {
                continue;
            }

            final int rankPosition = rank;
            double rrfScore = weight / (DEFAULT_RRF_K + rankPosition + 1.0);
            fused.compute(document.getId(), (id, existing) -> {
                if (existing == null) {
                    return new FusedCandidate(document, rrfScore, rankPosition);
                }
                return new FusedCandidate(
                        existing.document(),
                        existing.fusedScore() + rrfScore,
                        Math.min(existing.bestRank(), rankPosition)
                );
            });
        }
    }

    private List<Document> expandChildHitsToParent(String vectorTableName,
                                                    List<Document> reranked,
                                                    int topK) {
        if (reranked == null || reranked.isEmpty()) {
            return List.of();
        }

        List<String> parentChunkIds = reranked.stream()
                .map(this::extractParentChunkId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        Map<String, Document> parentDocuments = multiVectorStoreService.loadDocumentsByIds(vectorTableName, parentChunkIds);
        Map<String, Document> finalDocs = new LinkedHashMap<>();

        for (Document candidate : reranked) {
            if (candidate == null) {
                continue;
            }

            String chunkLevel = valueOf(candidate.getMetadata(), "chunkLevel").toLowerCase(Locale.ROOT);
            if (!"child".equals(chunkLevel)) {
                finalDocs.putIfAbsent(candidate.getId(), candidate);
                continue;
            }

            String parentChunkId = extractParentChunkId(candidate);
            Document parent = parentDocuments.get(parentChunkId);
            if (parent == null) {
                finalDocs.putIfAbsent(candidate.getId(), candidate);
                continue;
            }

            Map<String, Object> parentMetadata = parent.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(parent.getMetadata());
            parentMetadata.put("matchedChildChunkId", candidate.getId());

            Object score = candidate.getMetadata() == null ? null : candidate.getMetadata().get("rerankScore");
            if (score == null && candidate.getMetadata() != null) {
                score = candidate.getMetadata().get("score");
            }
            if (score != null) {
                parentMetadata.put("rerankScore", score);
                parentMetadata.put("score", score);
            }

            finalDocs.putIfAbsent(parent.getId(), new Document(parent.getId(), parent.getText(), parentMetadata));
            if (finalDocs.size() >= Math.max(1, topK)) {
                break;
            }
        }

        return finalDocs.values().stream().limit(Math.max(1, topK)).toList();
    }

    private List<Document> applyRerankThreshold(List<Document> reranked, double threshold) {
        if (reranked == null || reranked.isEmpty()) {
            return List.of();
        }

        List<Document> passed = reranked.stream()
                .filter(document -> extractScore(document) >= threshold)
                .toList();
        if (!passed.isEmpty()) {
            return passed;
        }
        return List.of(reranked.getFirst());
    }

    private double extractScore(Document document) {
        if (document == null || document.getMetadata() == null) {
            return 0.0D;
        }
        Object score = document.getMetadata().get("rerankScore");
        if (score == null) {
            score = document.getMetadata().get("score");
        }
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        if (score != null) {
            try {
                return Double.parseDouble(String.valueOf(score));
            } catch (Exception ignored) {
                return 0.0D;
            }
        }
        return 0.0D;
    }

    private String extractParentChunkId(Document document) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        Object value = document.getMetadata().get("parentChunkId");
        if (value == null) {
            return null;
        }
        String id = String.valueOf(value).trim();
        return id.isBlank() ? null : id;
    }

    private String valueOf(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return "";
        }
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String truncateQuery(String query) {
        return query.length() > 50 ? query.substring(0, 50) + "..." : query;
    }

    private record FusedCandidate(Document document, double fusedScore, int bestRank) {
    }
}
