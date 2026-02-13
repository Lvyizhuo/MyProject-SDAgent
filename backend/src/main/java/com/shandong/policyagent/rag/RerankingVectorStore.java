package com.shandong.policyagent.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

@Slf4j
@RequiredArgsConstructor
public class RerankingVectorStore implements VectorStore {

    private final VectorStore delegate;
    private final DashScopeRerankService rerankService;
    private final RagConfig ragConfig;

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void add(List<Document> documents) {
        delegate.add(normalizeDocumentIds(documents));
    }

    @Override
    public void delete(List<String> idList) {
        delegate.delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        delegate.delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        if (request == null) {
            return List.of();
        }

        int finalTopK = Math.max(1, request.getTopK());
        SearchRequest.Builder candidateBuilder = SearchRequest.from(request)
                .topK(Math.max(finalTopK, ragConfig.getRetrieval().getCandidateTopK()));
        SearchRequest candidateRequest = candidateBuilder.build();

        List<Document> candidates = delegate.similaritySearch(candidateRequest);
        if (candidates.isEmpty()) {
            return candidates;
        }

        List<Document> reranked = rerankService.rerank(request.getQuery(), candidates, finalTopK);
        log.debug("向量召回 {} 条，重排后返回 {} 条", candidates.size(), reranked.size());
        return reranked;
    }

    private List<Document> normalizeDocumentIds(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Document> normalized = new ArrayList<>(documents.size());
        for (Document document : documents) {
            if (document == null) {
                continue;
            }
            Map<String, Object> metadata = document.getMetadata() == null
                    ? Map.of()
                    : new HashMap<>(document.getMetadata());
            String source = String.valueOf(metadata.getOrDefault("source", ""));
            String text = document.getText() == null ? "" : document.getText();
            String normalizedId = DocumentIdNormalizer.normalize(document.getId(), source, text);
            normalized.add(new Document(normalizedId, text, metadata));
        }
        return normalized;
    }
}
