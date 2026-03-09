package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component("runtimeRagVectorStore")
@RequiredArgsConstructor
public class RuntimeRagVectorStore implements VectorStore {

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final ModelProviderService modelProviderService;
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
        log.debug("RAG 检索使用嵌入模型: {}", embeddingModelId);
        return multiVectorStoreService.getVectorStore(embeddingModelId).similaritySearch(request);
    }

    private String resolveEmbeddingModelId() {
        AgentConfig config = dynamicAgentConfigHolder.get();
        if (config != null && config.getEmbeddingModelId() != null) {
            try {
                ModelProvider provider = modelProviderService.getModelEntity(config.getEmbeddingModelId());
                if (provider.getType() == ModelType.EMBEDDING && Boolean.TRUE.equals(provider.getIsEnabled())) {
                    String matchedModelId = matchConfiguredEmbeddingModel(provider);
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

    private String matchConfiguredEmbeddingModel(ModelProvider provider) {
        String modelName = normalize(provider.getModelName());
        String apiUrl = normalizeBaseUrl(provider.getApiUrl());

        return embeddingService.getAvailableModels().stream()
                .filter(model -> matchesEmbeddingModel(model, modelName, apiUrl))
                .map(EmbeddingModelConfig.EmbeddingModel::getId)
                .findFirst()
                .orElseGet(() -> embeddingService.getAvailableModels().stream()
                        .filter(model -> normalize(model.getModelName()).equals(modelName))
                        .map(EmbeddingModelConfig.EmbeddingModel::getId)
                        .findFirst()
                        .orElse(null));
    }

    private boolean matchesEmbeddingModel(EmbeddingModelConfig.EmbeddingModel model, String modelName, String apiUrl) {
        return normalize(model.getModelName()).equals(modelName)
                && normalizeBaseUrl(model.getBaseUrl()).equals(apiUrl);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
