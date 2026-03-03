package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.config.MinioConfig;
import com.shandong.policyagent.entity.*;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeFolderRepository folderRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeConfigRepository configRepository;

    private final StorageService storageService;
    private final DocumentLoaderService documentLoaderService;
    private final TextSplitterService textSplitterService;
    private final EmbeddingService embeddingService;
    private final MultiVectorStoreService multiVectorStoreService;
    private final MinioConfig minioConfig;

    @Transactional
    public KnowledgeFolder createFolder(Long parentId, String name, String description, User createdBy) {
        KnowledgeFolder parent = null;
        String path;
        int depth = 1;

        if (parentId != null) {
            parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
            path = parent.getPath() + "/" + name;
            depth = parent.getDepth() + 1;
        } else {
            path = "/" + name;
        }

        if (folderRepository.findByPath(path).isPresent()) {
            throw new IllegalArgumentException("Folder path already exists: " + path);
        }

        KnowledgeFolder folder = KnowledgeFolder.builder()
                .parent(parent)
                .name(name)
                .description(description)
                .path(path)
                .depth(depth)
                .createdBy(createdBy)
                .build();

        return folderRepository.save(folder);
    }

    public List<KnowledgeFolder> getFolderTree() {
        return folderRepository.findAllRootFoldersWithChildren();
    }

    @Transactional
    public KnowledgeFolder updateFolder(Long id, String name, String description) {
        KnowledgeFolder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        folder.setName(name);
        folder.setDescription(description);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(Long id) {
        folderRepository.deleteById(id);
    }

    @Transactional
    public KnowledgeDocument uploadDocument(
            MultipartFile file,
            Long folderId,
            String title,
            String embeddingModelId,
            String category,
            List<String> tags,
            LocalDate publishDate,
            String source,
            LocalDate validFrom,
            LocalDate validTo,
            String summary,
            User createdBy) {

        KnowledgeFolder folder = folderId != null ? folderRepository.findById(folderId).orElse(null) : null;
        String folderPath = folder != null ? folder.getPath() : "/";

        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(embeddingModelId);

        String storagePath = storageService.storeFile(file, folderPath);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .folder(folder)
                .title(title != null ? title : file.getOriginalFilename())
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .storagePath(storagePath)
                .storageBucket(minioConfig.getBucketName())
                .embeddingModel(embeddingModelId)
                .vectorTableName(modelConfig.getVectorTable())
                .category(category)
                .tags(tags)
                .publishDate(publishDate)
                .source(source)
                .validFrom(validFrom)
                .validTo(validTo)
                .summary(summary)
                .status(DocumentStatus.PENDING)
                .createdBy(createdBy)
                .build();

        document = documentRepository.save(document);

        processDocumentAsync(document.getId());

        return document;
    }

    @Transactional
    public void processDocumentAsync(Long documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        try {
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            InputStream fileStream = storageService.getFile(document.getStoragePath());
            org.springframework.core.io.Resource resource = new InputStreamResource(fileStream) {
                @Override
                public String getFilename() {
                    return document.getFileName();
                }
            };

            List<Document> loadedDocs = documentLoaderService.loadDocumentFromResource(resource, document.getFileName());

            List<Document> splitDocs = textSplitterService.splitDocuments(loadedDocs);

            for (Document doc : splitDocs) {
                doc.getMetadata().put("knowledgeDocumentId", documentId);
                doc.getMetadata().put("sourceTitle", document.getTitle());
                if (document.getFolder() != null) {
                    doc.getMetadata().put("folderPath", document.getFolder().getPath());
                }
            }

            multiVectorStoreService.addDocuments(document.getEmbeddingModel(), splitDocs);

            document.setStatus(DocumentStatus.COMPLETED);
            document.setChunkCount(splitDocs.size());
            documentRepository.save(document);

            log.info("Document processed successfully: {} ({} chunks)", documentId, splitDocs.size());

        } catch (Exception e) {
            log.error("Failed to process document: {}", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        }
    }

    public Page<KnowledgeDocument> listDocuments(Long folderId, String category, String tag, DocumentStatus status, Pageable pageable) {
        if (folderId != null) {
            return documentRepository.findByFolderId(folderId, pageable);
        } else if (status != null) {
            return documentRepository.findByStatus(status, pageable);
        } else if (category != null) {
            return documentRepository.findByCategory(category, pageable);
        } else if (tag != null) {
            return documentRepository.findByTag(tag, pageable);
        }
        return documentRepository.findAll(pageable);
    }

    public Optional<KnowledgeDocument> getDocument(Long id) {
        return documentRepository.findById(id);
    }

    public InputStream downloadDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return storageService.getFile(document.getStoragePath());
    }

    public String getDocumentPreviewUrl(Long id, int expirationMinutes) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return storageService.getPresignedUrl(document.getStoragePath(), expirationMinutes);
    }

    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        List<String> vectorIds = new ArrayList<>();
        multiVectorStoreService.deleteDocuments(document.getEmbeddingModel(), vectorIds);

        storageService.deleteFile(document.getStoragePath());

        documentRepository.deleteById(id);
    }

    @Transactional
    public void reingestDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        document.setStatus(DocumentStatus.PENDING);
        document.setErrorMessage(null);
        documentRepository.save(document);
        processDocumentAsync(id);
    }

    public KnowledgeConfig getConfig() {
        return configRepository.findById(1L)
                .orElseGet(() -> configRepository.save(KnowledgeConfig.builder().build()));
    }

    @Transactional
    public KnowledgeConfig updateConfig(KnowledgeConfig config) {
        config.setId(1L);
        return configRepository.save(config);
    }
}
