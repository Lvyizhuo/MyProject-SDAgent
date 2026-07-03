package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.repository.AgentConfigRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * 智能体配置绑定清理器。
 *
 * 注意：本类不使用 @Slf4j 注解，避免在某些 Lombok 注解处理器
 * 未正确初始化的环境下（如增量编译缓存命中）出现 "找不到 log 变量" 的编译错误。
 * 改用 slf4j 原生 API 显式声明 log 静态字段。
 */
@Service
@RequiredArgsConstructor
public class AgentConfigBindingSanitizer {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigBindingSanitizer.class);

    private final AgentConfigRepository agentConfigRepository;
    private final KnowledgeFolderRepository knowledgeFolderRepository;
    private final ModelProviderService modelProviderService;

    @Transactional
    public AgentConfig sanitizeAndPersistIfNeeded(AgentConfig config) {
        if (config == null) {
            return null;
        }

        boolean changed = false;
        changed |= sanitizeModelBinding(config.getLlmModelId(), config::setLlmModelId, ModelType.LLM, "大语言");
        changed |= sanitizeModelBinding(config.getVisionModelId(), config::setVisionModelId, ModelType.VISION, "视觉");
        changed |= sanitizeModelBinding(config.getAudioModelId(), config::setAudioModelId, ModelType.AUDIO, "语音");
        changed |= sanitizeKnowledgeBaseFolder(config);

        if (!changed) {
            return config;
        }

        AgentConfig saved = agentConfigRepository.save(config);
        log.info("已自动清理失效的智能体配置绑定: configId={}", saved.getId());
        return saved;
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
