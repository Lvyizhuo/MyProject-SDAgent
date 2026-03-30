package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.KnowledgeMigrationProperties;
import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class KnowledgeEmbeddingMigrationRunner implements ApplicationRunner {

    private final KnowledgeMigrationProperties migrationProperties;
    private final KnowledgeMigrationState migrationState;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeService knowledgeService;
    private final EmbeddingService embeddingService;

    @PostConstruct
    void initializeMigrationState() {
        migrationState.markPending(migrationProperties.isEnabled());
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationProperties.isEnabled()) {
            return;
        }

        String targetModel = resolveTargetModel();
        String targetVectorTable = embeddingService.getModelConfig(targetModel).getVectorTable();

        List<KnowledgeDocument> documentsToMigrate = knowledgeDocumentRepository.findAll().stream()
                .filter(document -> needsMigration(document, targetModel, targetVectorTable))
                .toList();

        if (documentsToMigrate.isEmpty()) {
            log.info("Knowledge migration skipped: all documents already use {}", targetModel);
            migrationState.markCompleted("no migration needed");
            return;
        }

        log.info("Starting knowledge embedding migration to {} for {} documents",
                targetModel, documentsToMigrate.size());

        int successCount = 0;
        int failureCount = 0;
        for (KnowledgeDocument document : documentsToMigrate) {
            try {
                knowledgeService.reingestDocument(document.getId(), targetModel);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to migrate knowledge document {} to {}",
                        document.getId(), targetModel, e);
            }
        }

        String summary = String.format("migrated=%d failed=%d target=%s", successCount, failureCount, targetModel);

        if (failureCount > 0) {
            migrationState.markFailed(summary);
            log.warn("Knowledge embedding migration completed with failures: {}", summary);
        } else {
            migrationState.markCompleted(summary);
            log.info("Knowledge embedding migration completed successfully: {}", summary);
        }
    }

    private String resolveTargetModel() {
        String targetModel = migrationProperties.getTargetModel();
        if (targetModel == null || targetModel.isBlank()) {
            return "ollama:nomic-embed-text";
        }
        return targetModel.trim();
    }

    private boolean needsMigration(KnowledgeDocument document, String targetModel, String targetVectorTable) {
        return document != null
                && document.getId() != null
                && (!targetModel.equals(document.getEmbeddingModel())
                || !targetVectorTable.equals(document.getVectorTableName())
                || document.getStatus() != DocumentStatus.COMPLETED
                || document.getChunkCount() == null
                || document.getChunkCount() <= 0);
    }
}
