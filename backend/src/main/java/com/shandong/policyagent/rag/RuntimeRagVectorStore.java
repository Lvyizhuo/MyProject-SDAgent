package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component("runtimeRagVectorStore")
@RequiredArgsConstructor
public class RuntimeRagVectorStore implements VectorStore {

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final KnowledgeFolderRepository knowledgeFolderRepository;
    private final KnowledgeService knowledgeService;
    private final MultiVectorStoreService multiVectorStoreService;
    private final EmbeddingService embeddingService;
    private final RagConfig ragConfig;

    @Override
    public void add(List<Document> documents) {
        RetrievalContext retrievalContext = resolveRetrievalContext();
        multiVectorStoreService.getVectorStore(retrievalContext.embeddingModelId()).add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        RetrievalContext retrievalContext = resolveRetrievalContext();
        multiVectorStoreService.getVectorStore(retrievalContext.embeddingModelId()).delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        RetrievalContext retrievalContext = resolveRetrievalContext();
        multiVectorStoreService.getVectorStore(retrievalContext.embeddingModelId()).delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        RetrievalContext retrievalContext = resolveRetrievalContext();
        log.debug("RAG 检索使用嵌入模型: {}", retrievalContext.embeddingModelId());

        if (retrievalContext.missing()) {
            log.warn("知识库范围配置的文件夹不存在，返回空召回结果 | folderId={}", retrievalContext.folderId());
            return List.of();
        }

        if (retrievalContext.folderPath() != null) {
            log.debug("RAG 检索按知识库目录范围收敛: folderId={} | folderPath={}",
                    retrievalContext.folderId(), retrievalContext.folderPath());
            return sanitizeRetrievedDocuments(multiVectorStoreService.similaritySearchInFolderScope(
                    retrievalContext.embeddingModelId(),
                    request.getQuery(),
                    request.getTopK(),
                    retrievalContext.folderPath()
            ));
        }

        return sanitizeRetrievedDocuments(multiVectorStoreService.getVectorStore(retrievalContext.embeddingModelId()).similaritySearch(request));
    }

    public RetrievalContext resolveRetrievalContext() {
        ScopeResolution scope = resolveKnowledgeBaseScope();
        String embeddingModelId = scope.enabled() && scope.embeddingModelId() != null && !scope.embeddingModelId().isBlank()
            ? scope.embeddingModelId()
            : resolveFallbackEmbeddingModelId();
        String vectorTableName = scope.enabled() && scope.vectorTableName() != null && !scope.vectorTableName().isBlank()
            ? scope.vectorTableName()
            : embeddingService.getModelConfig(embeddingModelId).getVectorTable();

        if (scope.missing()) {
            return new RetrievalContext(embeddingModelId, vectorTableName, scope.folderId(), null, null, null, true);
        }

        String folderPath = scope.enabled() ? scope.folderPath() : null;
        Long folderId = scope.enabled() ? scope.folderId() : null;
        Long rerankModelId = scope.enabled() ? scope.rerankModelId() : null;
        String rerankModelName = scope.enabled() ? scope.rerankModelName() : null;
        return new RetrievalContext(embeddingModelId, vectorTableName, folderId, folderPath, rerankModelId, rerankModelName, false);
    }

    public record RetrievalContext(String embeddingModelId,
                                   String vectorTableName,
                                   Long folderId,
                                   String folderPath,
                                   Long rerankModelId,
                                   String rerankModelName,
                                   boolean missing) {
    }

    private ScopeResolution resolveKnowledgeBaseScope() {
        AgentConfig config = dynamicAgentConfigHolder.get();
        if (config == null || config.getKnowledgeBaseFolderId() == null) {
            return ScopeResolution.all();
        }

        Optional<KnowledgeFolder> folder = knowledgeFolderRepository.findById(config.getKnowledgeBaseFolderId());
        if (folder.isEmpty()) {
            return ScopeResolution.missing(config.getKnowledgeBaseFolderId());
        }

        KnowledgeFolder knowledgeBase = folder.get();
        return ScopeResolution.folder(
                knowledgeBase.getId(),
                knowledgeBase.getPath(),
                knowledgeBase.getEmbeddingModel(),
                knowledgeBase.getVectorTableName(),
                knowledgeBase.getRerankModelId(),
                knowledgeBase.getRerankModelName()
        );
    }

    private record ScopeResolution(Long folderId,
                                   String folderPath,
                                   String embeddingModelId,
                                   String vectorTableName,
                                   Long rerankModelId,
                                   String rerankModelName,
                                   boolean enabled,
                                   boolean missing) {
        private static ScopeResolution all() {
            return new ScopeResolution(null, null, null, null, null, null, false, false);
        }

        private static ScopeResolution folder(Long folderId,
                                              String folderPath,
                                              String embeddingModelId,
                                              String vectorTableName,
                                              Long rerankModelId,
                                              String rerankModelName) {
            return new ScopeResolution(folderId, folderPath, embeddingModelId, vectorTableName, rerankModelId, rerankModelName, true, false);
        }

        private static ScopeResolution missing(Long folderId) {
            return new ScopeResolution(folderId, null, null, null, null, null, false, true);
        }
    }

    private String resolveFallbackEmbeddingModelId() {
        return embeddingService.resolveDefaultModelId(knowledgeService.getConfig().getDefaultEmbeddingModel());
    }

    private List<Document> sanitizeRetrievedDocuments(List<Document> documents) {
        int promptMaxDocChars = Math.max(200, ragConfig.getRetrieval().getPromptMaxDocChars());
        return documents.stream()
                .map(document -> truncateForPrompt(document, promptMaxDocChars))
                .toList();
    }

    private Document truncateForPrompt(Document source, int promptMaxDocChars) {
        String text = source.getText();
        if (text == null || text.length() <= promptMaxDocChars) {
            return source;
        }

        HashMap<String, Object> metadata = source.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(source.getMetadata());
        metadata.put("truncatedForPrompt", true);
        metadata.put("promptChars", promptMaxDocChars);
        return new Document(source.getId(), text.substring(0, promptMaxDocChars).trim(), metadata);
    }
}
