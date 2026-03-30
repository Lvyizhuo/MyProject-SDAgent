package com.shandong.policyagent.rag;

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
    private static final int DEFAULT_OLLAMA_SAFE_CHUNK_SIZE = 900;
    private static final int DEFAULT_CHUNK_OVERLAP = 150;
    private static final int DEFAULT_MIN_CHUNK_SIZE = 250;
    private static final Set<String> LEGACY_DEFAULT_MODELS = Set.of(
            "dashscope:text-embedding-v3",
            "ollama:qwen3-embedding"
    );

    private final KnowledgeConfigRepository configRepository;
    private final StorageService storageService;
    private final MinioConfig minioConfig;
    private final EmbeddingService embeddingService;

    @Override
    public void run(String... args) {
        String configuredDefaultModel = resolveConfiguredDefaultModel();
        int safeMaxInputChars = embeddingService.resolveMaxInputChars(configuredDefaultModel);
        int safeChunkSize = Math.min(DEFAULT_OLLAMA_SAFE_CHUNK_SIZE, safeMaxInputChars);
        int safeOverlap = Math.min(DEFAULT_CHUNK_OVERLAP, Math.max(0, safeChunkSize / 3));
        int safeMinChunkSize = Math.min(DEFAULT_MIN_CHUNK_SIZE, safeChunkSize);

        if (configRepository.findById(1L).isEmpty()) {
            KnowledgeConfig config = KnowledgeConfig.builder()
                    .chunkSize(safeChunkSize)
                    .chunkOverlap(safeOverlap)
                    .minChunkSizeChars(safeMinChunkSize)
                    .noSplitMaxChars(safeChunkSize)
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
            if (config != null) {
                boolean updated = false;
                if (shouldMigrateDefaultModel(config.getDefaultEmbeddingModel(), configuredDefaultModel)) {
                    config.setDefaultEmbeddingModel(configuredDefaultModel);
                    updated = true;
                    log.info("Updated default knowledge embedding model to {}", configuredDefaultModel);
                }
                if (shouldNormalizeChunking(config, safeChunkSize)) {
                    config.setChunkSize(safeChunkSize);
                    config.setChunkOverlap(safeOverlap);
                    config.setMinChunkSizeChars(safeMinChunkSize);
                    config.setNoSplitMaxChars(safeChunkSize);
                    updated = true;
                    log.info("Updated knowledge chunking defaults for embedding-safe ingestion | chunkSize={} | overlap={} | noSplitMaxChars={}",
                            safeChunkSize, safeOverlap, safeChunkSize);
                }
                if (updated) {
                    configRepository.save(config);
                }
            }
        }

        try {
            storageService.ensureBucketExists();
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket - is MinIO running?", e);
        }
    }

    private String resolveConfiguredDefaultModel() {
        return embeddingService.resolveDefaultModelId(null);
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

    private boolean shouldNormalizeChunking(KnowledgeConfig config, int safeChunkSize) {
        if (config.getChunkSize() == null || config.getChunkSize() <= 0) {
            return true;
        }
        if (config.getNoSplitMaxChars() == null || config.getNoSplitMaxChars() <= 0) {
            return true;
        }
        return config.getChunkSize() > safeChunkSize
                || config.getNoSplitMaxChars() > safeChunkSize
                || Integer.valueOf(1600).equals(config.getChunkSize())
                || Integer.valueOf(6000).equals(config.getNoSplitMaxChars());
    }
}
