package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.entity.KnowledgeDocumentSource;
import com.shandong.policyagent.entity.UrlImportItem;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.UrlImportConfirmRequest;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import com.shandong.policyagent.rag.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeImportBridgeService {

    private final KnowledgeService knowledgeService;
    private final KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;

    public KnowledgeDocument importCandidate(UrlImportItem item, UrlImportConfirmRequest request, User currentUser) {
        String title = request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle().trim()
                : item.getSourceTitle();
        String source = request.getSource() != null && !request.getSource().isBlank()
                ? request.getSource().trim()
                : item.getSourceSite();

        Long targetFolderId = request.getFolderId() != null
                ? request.getFolderId()
                : (item.getJob().getTargetFolder() != null ? item.getJob().getTargetFolder().getId() : null);

        KnowledgeDocument document = knowledgeService.importTextDocument(
                item.getCleanedText(),
                targetFolderId,
                title,
                null,
                request.getCategory() != null ? request.getCategory() : item.getCategory(),
                request.getTags() != null && !request.getTags().isEmpty() ? request.getTags() : item.getTags(),
                request.getPublishDate() != null ? request.getPublishDate() : item.getPublishDate(),
                source,
                request.getValidFrom(),
                request.getValidTo(),
                request.getSummary() != null && !request.getSummary().isBlank() ? request.getSummary() : item.getSummary(),
                currentUser
        );

        knowledgeDocumentSourceRepository.save(KnowledgeDocumentSource.builder()
                .knowledgeDocument(document)
                .importItem(item)
                .sourceSite(item.getSourceSite())
                .sourceUrl(item.getSourceUrl())
                .sourcePage(item.getSourcePage())
                .build());

        return document;
    }
}