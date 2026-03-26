package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.entity.KnowledgeDocumentSource;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.rag.MultiVectorStoreService;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KnowledgeReferenceService {

    private static final String RETRIEVED_DOCUMENTS = "qa_retrieved_documents";
    private static final int MAX_REFERENCES = 4;
    private static final int CONTEXT_RADIUS = 1;
    private static final int MAX_SNIPPET_CHARS = 1200;

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;
    private final MultiVectorStoreService multiVectorStoreService;

    public List<ChatResponse.Reference> buildReferences(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        Object retrieved = context.get(RETRIEVED_DOCUMENTS);
        if (!(retrieved instanceof List<?> rawDocuments) || rawDocuments.isEmpty()) {
            return List.of();
        }

        List<Document> retrievedDocuments = rawDocuments.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
        if (retrievedDocuments.isEmpty()) {
            return List.of();
        }

        Map<Long, KnowledgeDocument> documentMapping = loadDocumentMapping(retrievedDocuments);
        Map<Long, KnowledgeDocumentSource> sourceMapping = loadSourceMapping(documentMapping.keySet());
        LinkedHashMap<String, ChatResponse.Reference> references = new LinkedHashMap<>();

        for (Document retrievedDocument : retrievedDocuments) {
            ChatResponse.Reference reference = toReference(
                    retrievedDocument,
                    documentMapping.get(getLongValue(retrievedDocument.getMetadata().get("knowledgeDocumentId"))),
                    sourceMapping.get(getLongValue(retrievedDocument.getMetadata().get("knowledgeDocumentId")))
            );
            if (reference == null) {
                continue;
            }

            String key = buildReferenceKey(reference);
            ChatResponse.Reference existing = references.get(key);
            if (existing == null || compareScore(reference.getScore(), existing.getScore()) > 0) {
                references.put(key, reference);
            }
            if (references.size() >= MAX_REFERENCES) {
                break;
            }
        }

        return new ArrayList<>(references.values());
    }

    private Map<Long, KnowledgeDocument> loadDocumentMapping(List<Document> retrievedDocuments) {
        List<Long> documentIds = retrievedDocuments.stream()
                .map(document -> getLongValue(document.getMetadata().get("knowledgeDocumentId")))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return knowledgeDocumentRepository.findAllById(documentIds).stream()
                .collect(LinkedHashMap::new, (map, document) -> map.put(document.getId(), document), Map::putAll);
    }

    private Map<Long, KnowledgeDocumentSource> loadSourceMapping(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        return knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(documentIds).stream()
                .filter(item -> item.getKnowledgeDocument() != null && item.getKnowledgeDocument().getId() != null)
                .collect(LinkedHashMap::new,
                        (map, item) -> map.put(item.getKnowledgeDocument().getId(), item),
                        Map::putAll);
    }

    private ChatResponse.Reference toReference(Document retrievedDocument,
                                               KnowledgeDocument knowledgeDocument,
                                               KnowledgeDocumentSource sourceMapping) {
        Map<String, Object> metadata = retrievedDocument.getMetadata();
        String title = defaultString(
                knowledgeDocument != null ? knowledgeDocument.getTitle() : null,
                stringValue(metadata.get("title"))
        );
        if (title == null || title.isBlank()) {
            title = defaultString(stringValue(metadata.get("sourceTitle")), "知识库参考");
        }

        Long documentId = knowledgeDocument != null ? knowledgeDocument.getId() : getLongValue(metadata.get("knowledgeDocumentId"));
        Integer chunkIndex = getIntValue(metadata.get("chunkIndex"));
        String snippet = resolveSnippet(retrievedDocument, knowledgeDocument, chunkIndex);
        String url = resolveSourceUrl(sourceMapping);
        String sourceSite = defaultString(
                sourceMapping != null ? sourceMapping.getSourceSite() : null,
                knowledgeDocument != null ? knowledgeDocument.getSource() : null
        );
        String publishedAt = knowledgeDocument != null && knowledgeDocument.getPublishDate() != null
                ? knowledgeDocument.getPublishDate().toString()
                : null;
        String scope = defaultString(
                stringValue(metadata.get("folderPath")),
                sourceSite
        );
        List<String> keywords = knowledgeDocument != null && knowledgeDocument.getTags() != null
                ? knowledgeDocument.getTags()
                : List.of();

        return ChatResponse.Reference.builder()
                .documentId(documentId)
                .title(title)
                .url(url)
                .sourceSite(sourceSite)
                .publishedAt(publishedAt)
                .scope(scope)
                .keywords(keywords)
                .snippet(snippet)
                .documentName(defaultString(
                        knowledgeDocument != null ? knowledgeDocument.getFileName() : null,
                        stringValue(metadata.get("documentName"))
                ))
                .content(snippet)
                .pageNumber(chunkIndex)
                .score(resolveScore(metadata))
                .build();
    }

    private String resolveSnippet(Document retrievedDocument,
                                  KnowledgeDocument knowledgeDocument,
                                  Integer chunkIndex) {
        String excerpt = null;
        if (knowledgeDocument != null) {
            excerpt = multiVectorStoreService.loadChunkExcerpt(
                    knowledgeDocument.getVectorTableName(),
                    knowledgeDocument.getId(),
                    chunkIndex,
                    CONTEXT_RADIUS
            );
        }

        if (excerpt == null || excerpt.isBlank()) {
            excerpt = retrievedDocument.getText();
        }
        if ((excerpt == null || excerpt.isBlank()) && knowledgeDocument != null) {
            excerpt = knowledgeDocument.getSummary();
        }
        if (excerpt == null || excerpt.isBlank()) {
            return "暂无原文片段";
        }

        String normalized = excerpt
                .replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        if (normalized.length() <= MAX_SNIPPET_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_CHARS).trim() + "...";
    }

    private String resolveSourceUrl(KnowledgeDocumentSource sourceMapping) {
        if (sourceMapping == null) {
            return null;
        }
        if (sourceMapping.getSourcePage() != null && !sourceMapping.getSourcePage().isBlank()) {
            return sourceMapping.getSourcePage();
        }
        return sourceMapping.getSourceUrl();
    }

    private String buildReferenceKey(ChatResponse.Reference reference) {
        if (reference.getDocumentId() != null) {
            return "doc:" + reference.getDocumentId();
        }
        String title = reference.getTitle() == null ? "" : reference.getTitle().trim().toLowerCase(Locale.ROOT);
        String url = reference.getUrl() == null ? "" : reference.getUrl().trim().toLowerCase(Locale.ROOT);
        return title + "|" + url;
    }

    private int compareScore(Double left, Double right) {
        return Double.compare(left == null ? Double.NEGATIVE_INFINITY : left,
                right == null ? Double.NEGATIVE_INFINITY : right);
    }

    private Double resolveScore(Map<String, Object> metadata) {
        Double rerankScore = getDoubleValue(metadata.get("rerankScore"));
        if (rerankScore != null) {
            return rerankScore;
        }
        return getDoubleValue(metadata.get("score"));
    }

    private Long getLongValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer getIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double getDoubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultString(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
