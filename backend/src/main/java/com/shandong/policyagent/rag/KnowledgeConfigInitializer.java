package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.MinioConfig;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeConfigInitializer implements CommandLineRunner {

    private final KnowledgeConfigRepository configRepository;
    private final StorageService storageService;
    private final MinioConfig minioConfig;

    @Override
    public void run(String... args) {
        if (configRepository.findById(1L).isEmpty()) {
            KnowledgeConfig config = KnowledgeConfig.builder()
                    .chunkSize(1600)
                    .chunkOverlap(300)
                    .minChunkSizeChars(350)
                    .noSplitMaxChars(6000)
                    .defaultEmbeddingModel("ollama:qwen3-embedding")
                    .minioEndpoint(minioConfig.getEndpoint())
                    .minioAccessKey(minioConfig.getAccessKey())
                    .minioSecretKey(minioConfig.getSecretKey())
                    .minioBucketName(minioConfig.getBucketName())
                    .build();
            configRepository.save(config);
            log.info("Initialized default knowledge config");
        } else {
            KnowledgeConfig config = configRepository.findById(1L).orElse(null);
            if (config != null && "dashscope:text-embedding-v3".equals(config.getDefaultEmbeddingModel())) {
                config.setDefaultEmbeddingModel("ollama:qwen3-embedding");
                configRepository.save(config);
                log.info("Updated default embedding model to ollama:qwen3-embedding");
            }
        }

        try {
            storageService.ensureBucketExists();
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket - is MinIO running?", e);
        }
    }
}
