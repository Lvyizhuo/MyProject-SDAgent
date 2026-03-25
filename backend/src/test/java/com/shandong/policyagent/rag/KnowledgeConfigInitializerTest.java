package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.MinioConfig;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeConfigInitializerTest {

    @Mock
    private KnowledgeConfigRepository configRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private MinioConfig minioConfig;

    @Mock
    private EmbeddingService embeddingService;

    @Test
    void shouldMigrateLegacyDefaultEmbeddingModelToConfiguredTarget() throws Exception {
        KnowledgeConfig existingConfig = KnowledgeConfig.builder()
                .defaultEmbeddingModel("dashscope:text-embedding-v3")
                .build();

        when(configRepository.findById(1L)).thenReturn(Optional.of(existingConfig));
        when(embeddingService.resolveDefaultModelId(null)).thenReturn("ollama:nomic-embed-text");

        KnowledgeConfigInitializer initializer = new KnowledgeConfigInitializer(
                configRepository,
                storageService,
                minioConfig,
                embeddingService
        );

        initializer.run();

        ArgumentCaptor<KnowledgeConfig> captor = ArgumentCaptor.forClass(KnowledgeConfig.class);
        verify(configRepository).save(captor.capture());
        assertEquals("ollama:nomic-embed-text", captor.getValue().getDefaultEmbeddingModel());
        verify(storageService).ensureBucketExists();
    }

    @Test
    void shouldPreserveNonLegacyDefaultEmbeddingModel() throws Exception {
        KnowledgeConfig existingConfig = KnowledgeConfig.builder()
                .defaultEmbeddingModel("dashscope:custom-embedding")
                .build();

        when(configRepository.findById(1L)).thenReturn(Optional.of(existingConfig));
        when(embeddingService.resolveDefaultModelId(null)).thenReturn("ollama:nomic-embed-text");

        KnowledgeConfigInitializer initializer = new KnowledgeConfigInitializer(
                configRepository,
                storageService,
                minioConfig,
                embeddingService
        );

        initializer.run();

        verify(storageService).ensureBucketExists();
    }
}
