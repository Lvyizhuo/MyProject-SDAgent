package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.config.MinioConfig;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class KnowledgeConfigInitializer implements CommandLineRunner {
    private static final Set<String> LEGACY_DEFAULT_MODELS = Set.of(
            "dashscope:text-embedding-v3",
            "ollama:qwen3-embedding"
    );

    private final KnowledgeConfigRepository configRepository;
    private final StorageService storageService;
    private final MinioConfig minioConfig;
    private final EmbeddingModelConfig embeddingModelConfig;

    @Override
    public void run(String... args) {
        String configuredDefaultModel = resolveConfiguredDefaultModel();
        if (configRepository.findById(1L).isEmpty()) {
            KnowledgeConfig config = KnowledgeConfig.builder()
                    .chunkSize(1600)
                    .chunkOverlap(300)
                    .minChunkSizeChars(350)
                    .noSplitMaxChars(6000)
                    .defaultEmbeddingModel(configuredDefaultModel)
                    .minioEndpoint(minioConfig.getEndpoint())
                    .minioAccessKey(minioConfig.getAccessKey())
                    .minioSecretKey(minioConfig.getSecretKey())
                    .minioBucketName(minioConfig.getBucketName())
                    .build();
            configRepository.save(config);
            log.info("Initialized default knowledge config with embedding model {}", configuredDefaultModel);
        } else {
            KnowledgeConfig config = configRepository.findById(1L).orElse(null);
            if (config != null && shouldMigrateDefaultModel(config.getDefaultEmbeddingModel(), configuredDefaultModel)) {
                config.setDefaultEmbeddingModel(configuredDefaultModel);
                configRepository.save(config);
                log.info("Updated default knowledge embedding model to {}", configuredDefaultModel);
            }
        }

        try {
            storageService.ensureBucketExists();
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket - is MinIO running?", e);
        }
    }

    private String resolveConfiguredDefaultModel() {
        String defaultModel = embeddingModelConfig.getDefaultModel();
        if (defaultModel == null || defaultModel.isBlank()) {
            return "ollama:nomic-embed-text";
        }
        return defaultModel.trim();
    }

    private boolean shouldMigrateDefaultModel(String currentDefaultModel, String configuredDefaultModel) {
        if (currentDefaultModel == null || currentDefaultModel.isBlank()) {
            return true;
        }
        if (configuredDefaultModel.equals(currentDefaultModel)) {
            return false;
        }
        return LEGACY_DEFAULT_MODELS.contains(currentDefaultModel);
    }
}
