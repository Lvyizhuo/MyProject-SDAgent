package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.repository.AgentConfigRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.rag.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigBindingSanitizer {

    private final AgentConfigRepository agentConfigRepository;
    private final KnowledgeFolderRepository knowledgeFolderRepository;
    private final ModelProviderService modelProviderService;
    private final EmbeddingService embeddingService;

    @Transactional
    public AgentConfig sanitizeAndPersistIfNeeded(AgentConfig config) {
        if (config == null) {
            return null;
        }

        boolean changed = false;
        changed |= sanitizeModelBinding(config.getLlmModelId(), config::setLlmModelId, ModelType.LLM, "大语言");
        changed |= sanitizeModelBinding(config.getVisionModelId(), config::setVisionModelId, ModelType.VISION, "视觉");
        changed |= sanitizeModelBinding(config.getAudioModelId(), config::setAudioModelId, ModelType.AUDIO, "语音");
        changed |= sanitizeEmbeddingBinding(config);
        changed |= sanitizeKnowledgeBaseFolder(config);

        if (!changed) {
            return config;
        }

        AgentConfig saved = agentConfigRepository.save(config);
        log.info("已自动清理失效的智能体配置绑定: configId={}", saved.getId());
        return saved;
    }

    private boolean sanitizeEmbeddingBinding(AgentConfig config) {
        Long modelId = config.getEmbeddingModelId();
        if (modelId == null) {
            return false;
        }

        try {
            ModelProvider provider = resolveValidModelBinding(modelId, ModelType.EMBEDDING);
            String mappedModelId = embeddingService.findMappedModelId(provider);
            if (mappedModelId != null) {
                return false;
            }

            config.setEmbeddingModelId(null);
            log.warn("检测到失效的嵌入模型绑定，已自动清理 | configId={} | modelId={} | reason=未映射到知识库向量配置",
                    config.getId(), modelId);
            return true;
        } catch (Exception ex) {
            config.setEmbeddingModelId(null);
            log.warn("检测到失效的嵌入模型绑定，已自动清理 | configId={} | modelId={} | reason={}",
                    config.getId(), modelId, ex.getMessage());
            return true;
        }
    }

    private boolean sanitizeKnowledgeBaseFolder(AgentConfig config) {
        Long folderId = config.getKnowledgeBaseFolderId();
        if (folderId == null || knowledgeFolderRepository.findById(folderId).isPresent()) {
            return false;
        }

        config.setKnowledgeBaseFolderId(null);
        log.warn("检测到失效的知识库目录绑定，已自动清理 | configId={} | folderId={}", config.getId(), folderId);
        return true;
    }

    private boolean sanitizeModelBinding(Long modelId, Consumer<Long> setter, ModelType type, String label) {
        if (modelId == null) {
            return false;
        }

        try {
            resolveValidModelBinding(modelId, type);
            return false;
        } catch (Exception ex) {
            setter.accept(null);
            log.warn("检测到失效的{}模型绑定，已自动清理 | modelId={} | reason={}", label, modelId, ex.getMessage());
            return true;
        }
    }

    private ModelProvider resolveValidModelBinding(Long modelId, ModelType expectedType) {
        modelProviderService.validateRuntimeModelBinding(modelId, expectedType);
        return modelProviderService.getModelEntity(modelId);
    }
}
