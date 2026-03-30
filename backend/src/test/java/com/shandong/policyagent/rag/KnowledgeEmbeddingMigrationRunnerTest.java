package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.config.KnowledgeMigrationProperties;
import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeEmbeddingMigrationRunnerTest {

    @Mock
    private KnowledgeMigrationProperties migrationProperties;

    @Mock
    private KnowledgeMigrationState migrationState;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private EmbeddingService embeddingService;

    @Test
    void shouldReingestOnlyDocumentsThatNeedMigration() throws Exception {
        EmbeddingModelConfig.EmbeddingModel targetModel = new EmbeddingModelConfig.EmbeddingModel();
        targetModel.setVectorTable("vector_store_ollama_nomic_768");

        KnowledgeDocument legacyDocument = KnowledgeDocument.builder()
                .id(1L)
                .embeddingModel("dashscope:text-embedding-v3")
                .vectorTableName("vector_store_dashscope_v3")
                .status(DocumentStatus.COMPLETED)
                .chunkCount(3)
                .build();
        KnowledgeDocument alreadyMigrated = KnowledgeDocument.builder()
                .id(2L)
                .embeddingModel("ollama:nomic-embed-text")
                .vectorTableName("vector_store_ollama_nomic_768")
                .status(DocumentStatus.COMPLETED)
                .chunkCount(2)
                .build();
        KnowledgeDocument tableMismatch = KnowledgeDocument.builder()
                .id(3L)
                .embeddingModel("ollama:nomic-embed-text")
                .vectorTableName("vector_store_legacy")
                .status(DocumentStatus.COMPLETED)
                .chunkCount(2)
                .build();
        KnowledgeDocument failedDocument = KnowledgeDocument.builder()
                .id(4L)
                .embeddingModel("ollama:nomic-embed-text")
                .vectorTableName("vector_store_ollama_nomic_768")
                .status(DocumentStatus.FAILED)
                .chunkCount(0)
                .build();

        when(migrationProperties.isEnabled()).thenReturn(true);
        when(migrationProperties.getTargetModel()).thenReturn("ollama:nomic-embed-text");
        when(embeddingService.getModelConfig("ollama:nomic-embed-text")).thenReturn(targetModel);
        when(knowledgeDocumentRepository.findAll()).thenReturn(List.of(legacyDocument, alreadyMigrated, tableMismatch, failedDocument));

        KnowledgeEmbeddingMigrationRunner runner = new KnowledgeEmbeddingMigrationRunner(
                migrationProperties,
                migrationState,
                knowledgeDocumentRepository,
                knowledgeService,
                embeddingService
        );

        runner.initializeMigrationState();
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(knowledgeService).reingestDocument(1L, "ollama:nomic-embed-text");
        verify(knowledgeService).reingestDocument(3L, "ollama:nomic-embed-text");
        verify(knowledgeService).reingestDocument(4L, "ollama:nomic-embed-text");
        verify(knowledgeService, never()).reingestDocument(2L, "ollama:nomic-embed-text");
    }

    @Test
    void shouldSkipWhenMigrationDisabled() throws Exception {
        when(migrationProperties.isEnabled()).thenReturn(false);

        KnowledgeEmbeddingMigrationRunner runner = new KnowledgeEmbeddingMigrationRunner(
                migrationProperties,
                migrationState,
                knowledgeDocumentRepository,
                knowledgeService,
                embeddingService
        );

        runner.initializeMigrationState();
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(knowledgeDocumentRepository, never()).findAll();
        verify(embeddingService, never()).getModelConfig(anyString());
    }
}
