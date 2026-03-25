package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component("runtimeRagVectorStore")
@RequiredArgsConstructor
public class RuntimeRagVectorStore implements VectorStore {

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final ModelProviderService modelProviderService;
    private final KnowledgeFolderRepository knowledgeFolderRepository;
    private final KnowledgeService knowledgeService;
    private final MultiVectorStoreService multiVectorStoreService;
    private final EmbeddingService embeddingService;

    @Override
    public void add(List<Document> documents) {
        multiVectorStoreService.getVectorStore(resolveEmbeddingModelId()).add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        multiVectorStoreService.getVectorStore(resolveEmbeddingModelId()).delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        multiVectorStoreService.getVectorStore(resolveEmbeddingModelId()).delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String embeddingModelId = resolveEmbeddingModelId();
        ScopeResolution knowledgeBaseScope = resolveKnowledgeBaseScope();
        log.debug("RAG 检索使用嵌入模型: {}", embeddingModelId);

        if (knowledgeBaseScope.missing()) {
            log.warn("知识库范围配置的文件夹不存在，返回空召回结果 | folderId={}", knowledgeBaseScope.folderId());
            return List.of();
        }

        if (knowledgeBaseScope.enabled()) {
            log.debug("RAG 检索按知识库目录范围收敛: folderId={} | folderPath={}",
                    knowledgeBaseScope.folderId(), knowledgeBaseScope.folderPath());
            return multiVectorStoreService.similaritySearchInFolderScope(
                    embeddingModelId,
                    request.getQuery(),
                    request.getTopK(),
                    knowledgeBaseScope.folderPath()
            );
        }

        return multiVectorStoreService.getVectorStore(embeddingModelId).similaritySearch(request);
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

        return ScopeResolution.folder(folder.get().getId(), folder.get().getPath());
    }

    private record ScopeResolution(Long folderId, String folderPath, boolean enabled, boolean missing) {
        private static ScopeResolution all() {
            return new ScopeResolution(null, null, false, false);
        }

        private static ScopeResolution folder(Long folderId, String folderPath) {
            return new ScopeResolution(folderId, folderPath, true, false);
        }

        private static ScopeResolution missing(Long folderId) {
            return new ScopeResolution(folderId, null, false, true);
        }
    }

    private String resolveEmbeddingModelId() {
        AgentConfig config = dynamicAgentConfigHolder.get();
        if (config != null && config.getEmbeddingModelId() != null) {
            try {
                ModelProvider provider = modelProviderService.getModelEntity(config.getEmbeddingModelId());
                if (provider.getType() == ModelType.EMBEDDING && Boolean.TRUE.equals(provider.getIsEnabled())) {
                    String matchedModelId = embeddingService.findMappedModelId(provider);
                    if (matchedModelId != null) {
                        return matchedModelId;
                    }
                    log.warn("未找到与管理员嵌入模型匹配的知识库配置，回退到知识库默认模型 | modelId={} | modelName={}",
                            provider.getId(), provider.getModelName());
                }
            } catch (Exception ex) {
                log.warn("解析管理员嵌入模型失败，回退到知识库默认模型: {}", ex.getMessage());
            }
        }

        return Optional.ofNullable(knowledgeService.getConfig().getDefaultEmbeddingModel())
                .filter(value -> !value.isBlank())
                .orElseGet(() -> embeddingService.getDefaultModel().getId());
    }
}
