package com.shandong.policyagent.rag;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagConfig ragConfig;

    public List<Document> retrieveRelevantDocuments(String query) {
        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(retrievalConfig.getTopK())
                .similarityThreshold(retrievalConfig.getSimilarityThreshold())
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.debug("检索到 {} 个相关文档, 查询: {}", results.size(), truncateQuery(query));

        return results;
    }

    public List<Document> retrieveWithFilter(String query, String filterExpression) {
        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(retrievalConfig.getTopK())
                .similarityThreshold(retrievalConfig.getSimilarityThreshold())
                .filterExpression(filterExpression)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.debug("检索到 {} 个相关文档 (带过滤), 查询: {}, 过滤条件: {}",
                results.size(), truncateQuery(query), filterExpression);

        return results;
    }

    public List<Document> retrieveByCategory(String query, String category) {
        String filterExpression = String.format("category == '%s'", category);
        return retrieveWithFilter(query, filterExpression);
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

    private String truncateQuery(String query) {
        return query.length() > 50 ? query.substring(0, 50) + "..." : query;
    }
}
