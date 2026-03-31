package com.shandong.policyagent.service;

import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.rag.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagPrefetchService {

    private static final String RETRIEVED_DOCUMENTS = "qa_retrieved_documents";

    private final RagRetrievalService ragRetrievalService;
    private final KnowledgeReferenceService knowledgeReferenceService;

    @Value("${app.agent.pre-rag-direct-threshold:0.86}")
    private double directAnswerThreshold;

    @Value("${app.agent.pre-rag-context-max-chars-per-doc:0}")
    private int preRagContextMaxCharsPerDoc;

    public RagPrefetchResult prefetch(String query) {
        if (query == null || query.isBlank()) {
            return RagPrefetchResult.empty();
        }

        List<Document> documents = ragRetrievalService.retrieveRelevantDocuments(query);
        if (documents == null || documents.isEmpty()) {
            return RagPrefetchResult.empty();
        }

        double topScore = extractTopScore(documents.get(0));
        List<ChatResponse.Reference> references;
        try {
            references = knowledgeReferenceService.buildReferences(
                Map.of(RETRIEVED_DOCUMENTS, documents)
            );
        } catch (Exception exception) {
            log.warn("RAG 预检索引用构建失败，降级为空引用 | error={}", exception.getMessage());
            references = List.of();
        }

        String context = buildContextSnippet(documents);
        boolean canAnswerDirectly = topScore >= directAnswerThreshold;

        log.info("RAG 预检索完成 | docs={} | topScore={} | canAnswerDirectly={}",
                documents.size(), topScore, canAnswerDirectly);

        return new RagPrefetchResult(
                List.copyOf(documents),
                references == null ? List.of() : List.copyOf(references),
                topScore,
                canAnswerDirectly,
                context
        );
    }

    private double extractTopScore(Document document) {
        if (document == null || document.getMetadata() == null) {
            return 0.0;
        }

        Object rerankScore = document.getMetadata().get("rerankScore");
        if (rerankScore instanceof Number number) {
            return number.doubleValue();
        }

        Object score = document.getMetadata().get("score");
        if (score instanceof Number number) {
            return number.doubleValue();
        }

        try {
            if (rerankScore != null) {
                return Double.parseDouble(String.valueOf(rerankScore));
            }
            if (score != null) {
                return Double.parseDouble(String.valueOf(score));
            }
        } catch (Exception ignored) {
            return 0.0;
        }

        return 0.0;
    }

    private String buildContextSnippet(List<Document> documents) {
        StringBuilder builder = new StringBuilder();
        builder.append("【RAG预检索结果】\n");
        int limit = Math.min(3, documents.size());
        for (int i = 0; i < limit; i++) {
            Document document = documents.get(i);
            Map<String, Object> metadata = document.getMetadata();
            String title = resolveTitle(metadata, i + 1);
            String content = document.getText() == null ? "" : document.getText().trim();
            if (preRagContextMaxCharsPerDoc > 0
                    && content.length() > preRagContextMaxCharsPerDoc) {
                content = content.substring(0, preRagContextMaxCharsPerDoc).trim() + "...";
            }
            builder.append(i + 1)
                    .append(". ")
                    .append(title)
                    .append("\n")
                    .append(content)
                    .append("\n\n");
        }

        return builder.toString().trim();
    }

    private String resolveTitle(Map<String, Object> metadata, int defaultIndex) {
        if (metadata == null || metadata.isEmpty()) {
            return "参考文档" + defaultIndex;
        }

        String title = toString(metadata.get("title"));
        if (title.isBlank()) {
            title = toString(metadata.get("sourceTitle"));
        }
        if (title.isBlank()) {
            title = toString(metadata.get("documentName"));
        }

        return title.isBlank() ? "参考文档" + defaultIndex : title;
    }

    private String toString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    public record RagPrefetchResult(
            List<Document> documents,
            List<ChatResponse.Reference> references,
            double topScore,
            boolean canAnswerDirectly,
            String contextSnippet
    ) {
        public static RagPrefetchResult empty() {
            return new RagPrefetchResult(List.of(), List.of(), 0.0, false, "");
        }

        public boolean hasDocuments() {
            return documents != null && !documents.isEmpty();
        }

        public boolean isPolicyLikeQuestion(String query) {
            String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
            return normalized.contains("政策")
                    || normalized.contains("补贴")
                    || normalized.contains("申领")
                    || normalized.contains("条件")
                    || normalized.contains("流程")
                    || normalized.contains("材料");
        }
    }
}