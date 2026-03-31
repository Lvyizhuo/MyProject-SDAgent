package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.config.MinioConfig;
import com.shandong.policyagent.entity.*;
import com.shandong.policyagent.model.dto.DocumentMetadataExtractResponse;
import com.shandong.policyagent.model.dto.DocumentChunksPageResponse;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.repository.UrlImportJobRepository;
import com.shandong.policyagent.repository.UrlImportItemRepository;
import com.shandong.policyagent.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeFolderRepository folderRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeConfigRepository configRepository;
    private final KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;
    private final UrlImportJobRepository urlImportJobRepository;
    private final UrlImportItemRepository urlImportItemRepository;
    @Qualifier("knowledgeIngestTaskExecutor")
    private final Executor knowledgeIngestTaskExecutor;

    private final StorageService storageService;
    private final DocumentLoaderService documentLoaderService;
    private final EmbeddingService embeddingService;
    private final MultiVectorStoreService multiVectorStoreService;
    private final ModelProviderService modelProviderService;
    private final RagConfig ragConfig;
    private final MinioConfig minioConfig;
    private static final Pattern SOURCE_LABEL_PATTERN = Pattern.compile("(来源|发布单位|印发单位)[:：]\\s*([^\\n\\r]{2,50})");
    private static final Pattern REGION_PATTERN = Pattern.compile("(济南|青岛|淄博|枣庄|东营|烟台|潍坊|济宁|泰安|威海|日照|临沂|德州|聊城|滨州|菏泽)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("20\\d{2}");
    private static final String PROVINCE_TAG = "山东省";
    private static final int PARENT_CHUNK_MAX_CHARS = 1024;
    private static final int CHILD_CHUNK_MAX_CHARS = 512;
    private static final double DEFAULT_RERANK_SCORE_THRESHOLD = 0.5D;

    @Transactional
    public KnowledgeFolder createFolder(Long parentId,
                                        String name,
                                        String description,
                                        String embeddingModelId,
                                        Long rerankModelId,
                                        User createdBy) {
        if (parentId != null) {
            throw new IllegalArgumentException("当前版本仅支持独立知识库，不支持父子目录");
        }

        String path = "/" + name;
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(embeddingModelId);

        if (folderRepository.findByPath(path).isPresent()) {
            throw new IllegalArgumentException("Folder path already exists: " + path);
        }

        modelProviderService.validateRuntimeModelBinding(rerankModelId, ModelType.RERANK);
        ModelProvider rerankModel = modelProviderService.getModelEntity(rerankModelId);

        KnowledgeFolder folder = KnowledgeFolder.builder()
                .parent(null)
                .name(name)
                .description(description)
                .path(path)
                .depth(1)
                .embeddingModel(modelConfig.getId())
                .vectorTableName(modelConfig.getVectorTable())
                .rerankModelId(rerankModel.getId())
                .rerankModelName(rerankModel.getModelName())
                .initStatus(KnowledgeBaseInitStatus.INITIALIZING)
                .createdBy(createdBy)
                .build();

            KnowledgeFolder saved = folderRepository.save(folder);
            scheduleKnowledgeBaseInitialization(saved.getId());
            return saved;
    }

    public List<KnowledgeFolder> getFolderTree() {
        return folderRepository.findAllKnowledgeBases();
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
        KnowledgeFolder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        if (folderRepository.existsByParentId(id)) {
            throw new IllegalArgumentException("文件夹下仍有子文件夹，请先删除或移动后再删除");
        }
        if (documentRepository.existsByFolderId(id)) {
            throw new IllegalArgumentException("文件夹下仍有文档，请先删除或移动后再删除");
        }
        if (urlImportJobRepository.existsByTargetFolderIdAndDeletedAtIsNull(id)) {
            throw new IllegalArgumentException("文件夹仍被网站导入任务引用，请先删除或调整相关任务后再删除");
        }
        urlImportJobRepository.clearTargetFolderReferences(id);
        folderRepository.delete(folder);
    }

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

        if (folderId == null) {
            throw new IllegalArgumentException("请选择知识库后再上传文档");
        }
        KnowledgeFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        assertKnowledgeBaseReady(folder);
        String folderPath = folder != null ? folder.getPath() : "/";
        String resolvedFileType = storageService.resolveContentType(file.getOriginalFilename(), file.getContentType());

        if (embeddingModelId != null && !embeddingModelId.isBlank() && !embeddingModelId.equals(folder.getEmbeddingModel())) {
            throw new IllegalArgumentException("文档嵌入模型由知识库固定绑定，不能单独指定");
        }
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(folder.getEmbeddingModel());
        String vectorTableName = folder.getVectorTableName() != null && !folder.getVectorTableName().isBlank()
                ? folder.getVectorTableName()
                : modelConfig.getVectorTable();

        String storagePath = storageService.storeFile(file, folderPath);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .folder(folder)
                .title(title != null ? title : file.getOriginalFilename())
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(resolvedFileType)
                .storagePath(storagePath)
                .storageBucket(minioConfig.getBucketName())
                .embeddingModel(folder.getEmbeddingModel())
                .vectorTableName(vectorTableName)
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

        scheduleDocumentProcessing(document.getId());

        return document;
    }

    public KnowledgeDocument importTextDocument(
            String text,
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

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待入库文本不能为空");
        }

        if (folderId == null) {
            throw new IllegalArgumentException("请选择知识库后再导入文本");
        }
        KnowledgeFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        assertKnowledgeBaseReady(folder);
        String folderPath = folder != null ? folder.getPath() : "/";
        String documentTitle = title == null || title.isBlank() ? "网页导入文档" : title.trim();
        String fileName = documentTitle.replaceAll("[\\/:*?\"<>|\\s]+", "-") + ".txt";

        if (embeddingModelId != null && !embeddingModelId.isBlank() && !embeddingModelId.equals(folder.getEmbeddingModel())) {
            throw new IllegalArgumentException("文档嵌入模型由知识库固定绑定，不能单独指定");
        }
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(folder.getEmbeddingModel());
        String vectorTableName = folder.getVectorTableName() != null && !folder.getVectorTableName().isBlank()
            ? folder.getVectorTableName()
            : modelConfig.getVectorTable();
        String storagePath = storageService.storeText(text, folderPath, fileName);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .folder(folder)
                .title(documentTitle)
                .fileName(fileName)
                .fileSize((long) text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .fileType("text/plain; charset=UTF-8")
                .storagePath(storagePath)
                .storageBucket(minioConfig.getBucketName())
                .embeddingModel(folder.getEmbeddingModel())
                .vectorTableName(vectorTableName)
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
        scheduleDocumentProcessing(document.getId());
        return document;
    }

    public KnowledgeDocument importArchivedDocument(
            byte[] fileBytes,
            Long folderId,
            String title,
            String fileName,
            String fileType,
            String embeddingModelId,
            String category,
            List<String> tags,
            LocalDate publishDate,
            String source,
            LocalDate validFrom,
            LocalDate validTo,
            String summary,
            User createdBy) {

        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("导入文件内容不能为空");
        }

        if (folderId == null) {
            throw new IllegalArgumentException("请选择知识库后再导入归档文档");
        }
        KnowledgeFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        assertKnowledgeBaseReady(folder);
        String folderPath = folder != null ? folder.getPath() : "/";
        String safeFileName = fileName == null || fileName.isBlank() ? "imported-document.bin" : fileName.trim();
        String safeTitle = title == null || title.isBlank() ? safeFileName : title.trim();
        String safeFileType = storageService.resolveContentType(safeFileName, fileType);

        if (embeddingModelId != null && !embeddingModelId.isBlank() && !embeddingModelId.equals(folder.getEmbeddingModel())) {
            throw new IllegalArgumentException("文档嵌入模型由知识库固定绑定，不能单独指定");
        }
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(folder.getEmbeddingModel());
        String vectorTableName = folder.getVectorTableName() != null && !folder.getVectorTableName().isBlank()
            ? folder.getVectorTableName()
            : modelConfig.getVectorTable();
        String storagePath = storageService.storeBytes(fileBytes, folderPath, safeFileName, safeFileType);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .folder(folder)
                .title(safeTitle)
                .fileName(safeFileName)
                .fileSize((long) fileBytes.length)
                .fileType(safeFileType)
                .storagePath(storagePath)
                .storageBucket(minioConfig.getBucketName())
                .embeddingModel(folder.getEmbeddingModel())
                .vectorTableName(vectorTableName)
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
        scheduleDocumentProcessing(document.getId());
        return document;
    }

    public DocumentMetadataExtractResponse extractDocumentMetadata(MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "未命名文档" : file.getOriginalFilename();
        String normalizedTitle = removeExtension(originalName).trim();
        String text = "";

        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return originalName;
                }
            };
            List<Document> docs = documentLoaderService.loadDocumentFromResource(resource, originalName);
            text = docs.stream()
                    .map(Document::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse("");
        } catch (Exception e) {
            log.warn("提取文档元数据时读取内容失败，将降级为文件名规则提取: {}", originalName, e);
        }

        String category = inferCategory(normalizedTitle, text);
        List<String> tags = inferTags(normalizedTitle, text);
        String source = inferSource(text);
        String summary = inferSummary(text);

        return DocumentMetadataExtractResponse.builder()
                .title(normalizedTitle)
                .category(category)
                .tags(tags)
                .source(source)
                .summary(summary)
                .build();
    }

    public void processDocumentAsync(Long documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String folderPath = resolveFolderPath(document);

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

                List<Document> splitDocs = buildParentChunks(loadedDocs);

            List<Document> indexedDocs = buildParentChildIndexDocuments(
                    splitDocs,
                    document,
                    documentId,
                    folderPath
            );

            if (indexedDocs.isEmpty()) {
                throw new IllegalStateException("文档未提取到可入库文本，请检查文件内容或 OCR 配置");
            }

            multiVectorStoreService.addDocuments(document.getEmbeddingModel(), indexedDocs);

            document.setStatus(DocumentStatus.COMPLETED);
            document.setChunkCount(indexedDocs.size());
            documentRepository.save(document);

            log.info("Document processed successfully: {} ({} chunks)", documentId, indexedDocs.size());

        } catch (Exception e) {
            log.error("Failed to process document: {}", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.saveAndFlush(document);
        }
    }

    private String resolveFolderPath(KnowledgeDocument document) {
        if (document.getFolder() == null || document.getFolder().getId() == null) {
            return null;
        }
        return folderRepository.findById(document.getFolder().getId())
                .map(KnowledgeFolder::getPath)
                .orElse(null);
    }

    private List<Document> buildParentChildIndexDocuments(List<Document> parentChunks,
                                                          KnowledgeDocument document,
                                                          Long documentId,
                                                          String folderPath) {
        List<Document> result = new ArrayList<>();
        if (parentChunks == null || parentChunks.isEmpty()) {
            return result;
        }

        int chunkIndex = 1;
        for (int i = 0; i < parentChunks.size(); i++) {
            Document parentChunk = parentChunks.get(i);
            if (parentChunk == null || parentChunk.getText() == null || parentChunk.getText().isBlank()) {
                continue;
            }

            String parentChunkId = UUID.randomUUID().toString();
            Map<String, Object> parentMetadata = parentChunk.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(parentChunk.getMetadata());
            parentMetadata.put("knowledgeDocumentId", documentId);
            parentMetadata.put("sourceTitle", document.getTitle());
            parentMetadata.put("title", document.getTitle());
            parentMetadata.put("documentName", document.getFileName());
            parentMetadata.put("source", document.getSource() == null ? "" : document.getSource());
            parentMetadata.put("chunkLevel", "parent");
            parentMetadata.put("parentChunkId", parentChunkId);
            parentMetadata.put("parentChunkIndex", i + 1);
            parentMetadata.put("chunkIndex", chunkIndex++);
            parentMetadata.put("chunkChars", parentChunk.getText().length());
            if (folderPath != null) {
                parentMetadata.put("folderPath", folderPath);
            }

            result.add(new Document(parentChunkId, parentChunk.getText(), parentMetadata));

            List<String> childChunks = splitIntoChildChunks(parentChunk.getText());
            for (int childIndex = 0; childIndex < childChunks.size(); childIndex++) {
                String childText = childChunks.get(childIndex);
                if (childText == null || childText.isBlank()) {
                    continue;
                }

                Map<String, Object> childMetadata = new HashMap<>(parentMetadata);
                childMetadata.put("chunkLevel", "child");
                childMetadata.put("childChunkIndex", childIndex + 1);
                childMetadata.put("chunkIndex", chunkIndex++);
                childMetadata.put("chunkChars", childText.length());
                Object splitStrategy = childMetadata.get("splitStrategy");
                if (splitStrategy != null && !String.valueOf(splitStrategy).isBlank()) {
                    childMetadata.put("splitStrategy", splitStrategy + "_CHILD");
                } else {
                    childMetadata.put("splitStrategy", "PARENT_CHILD_CHILD");
                }

                result.add(new Document(UUID.randomUUID().toString(), childText, childMetadata));
            }
        }

        return result;
    }

    private List<Document> buildParentChunks(List<Document> loadedDocs) {
        List<Document> parentChunks = new ArrayList<>();
        if (loadedDocs == null || loadedDocs.isEmpty()) {
            return parentChunks;
        }

        for (Document loadedDoc : loadedDocs) {
            if (loadedDoc == null || loadedDoc.getText() == null || loadedDoc.getText().isBlank()) {
                continue;
            }

            String normalized = normalizeTextForChunking(loadedDoc.getText());
            if (normalized.isBlank()) {
                continue;
            }

            List<String> segments = splitBySeparator(normalized, "\\n\\n", PARENT_CHUNK_MAX_CHARS);
            Map<String, Object> sourceMetadata = loadedDoc.getMetadata() == null
                    ? Map.of()
                    : loadedDoc.getMetadata();

            for (String segment : segments) {
                if (segment == null || segment.isBlank()) {
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>(sourceMetadata);
                metadata.put("splitStrategy", "PARENT_CHILD_PARAGRAPH");
                metadata.put("chunkChars", segment.length());
                parentChunks.add(new Document(UUID.randomUUID().toString(), segment, metadata));
            }
        }

        return parentChunks;
    }

    private List<String> splitIntoChildChunks(String text) {
        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        if (normalized.length() <= CHILD_CHUNK_MAX_CHARS) {
            return List.of(normalized);
        }

        return splitBySeparator(normalized, "\\n", CHILD_CHUNK_MAX_CHARS);
    }

    private List<String> splitBySeparator(String text, String separatorRegex, int maxChars) {
        List<String> chunks = new ArrayList<>();
        String[] parts = text.split(separatorRegex);
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            if (current.isEmpty()) {
                current.append(trimmed);
                continue;
            }

            int joinedLength = current.length() + trimmed.length() + 1;
            if (joinedLength <= maxChars) {
                current.append("\n").append(trimmed);
            } else {
                appendWithHardLimit(chunks, current.toString().trim(), maxChars);
                current.setLength(0);
                current.append(trimmed);
            }
        }

        if (!current.isEmpty()) {
            appendWithHardLimit(chunks, current.toString().trim(), maxChars);
        }

        if (chunks.isEmpty()) {
            appendWithHardLimit(chunks, text, maxChars);
        }
        return chunks;
    }

    private void appendWithHardLimit(List<String> chunks, String content, int maxChars) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return;
        }

        if (normalized.length() <= maxChars) {
            chunks.add(normalized);
            return;
        }

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + maxChars);
            String section = normalized.substring(start, end).trim();
            if (!section.isBlank()) {
                chunks.add(section);
            }
            start = end;
        }
    }

    private String normalizeTextForChunking(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }

    public Page<KnowledgeDocument> listDocuments(Long folderId, Long importJobId, String category, String tag, DocumentStatus status, String keyword, Pageable pageable) {
        if (importJobId != null) {
            if (keyword != null && !keyword.isBlank()) {
                return documentRepository.searchDocumentsByImportJobId(importJobId, status, keyword.trim(), pageable);
            }
            return documentRepository.findByImportJobId(importJobId, status, pageable);
        }
        if (keyword != null && !keyword.isBlank()) {
            return documentRepository.searchDocuments(folderId, status, keyword.trim(), pageable);
        }
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

    public List<Long> listDocumentIds(Long folderId, Long importJobId, DocumentStatus status, String keyword) {
        String normalizedKeyword = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        if (importJobId != null) {
            if (normalizedKeyword == null) {
                return documentRepository.findIdsByImportJobId(importJobId, status);
            }
            return documentRepository.searchIdsByImportJobId(importJobId, status, normalizedKeyword);
        }
        if (normalizedKeyword == null) {
            return documentRepository.findIdsBySelection(folderId, status);
        }
        return documentRepository.searchIdsBySelection(folderId, status, normalizedKeyword);
    }

    public Optional<KnowledgeDocument> getDocument(Long id) {
        return documentRepository.findById(id);
    }

    public DocumentChunksPageResponse listDocumentChunks(Long id, int page, int size) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        DocumentChunksPageResponse chunksPage = multiVectorStoreService.listDocumentChunks(
                document.getVectorTableName(), id, page, size
        );
        chunksPage.setDocumentId(document.getId());
        chunksPage.setTitle(document.getTitle());
        chunksPage.setVectorTableName(document.getVectorTableName());
        chunksPage.setChunkCount(document.getChunkCount());
        return chunksPage;
    }

    public InputStream downloadDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return storageService.getFile(document.getStoragePath());
    }

    public String getDocumentPreviewUrl(Long id, int expirationMinutes) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return storageService.getPresignedPreviewUrl(document.getStoragePath(), document.getFileType(), expirationMinutes);
    }

    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        clearDocumentReferences(List.of(id));
        deleteDocument(document);
    }

    @Transactional
    public void batchDeleteDocuments(List<Long> ids) {
        List<Long> normalizedIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("未选择任何文档");
        }

        List<KnowledgeDocument> documents = documentRepository.findAllById(normalizedIds);
        if (documents.size() != normalizedIds.size()) {
            throw new IllegalArgumentException("部分文档不存在，无法批量删除");
        }
        clearDocumentReferences(normalizedIds);
        for (KnowledgeDocument document : documents) {
            deleteDocument(document);
        }
    }

    @Transactional
    public void batchMoveDocuments(List<Long> ids, Long targetFolderId) {
        if (targetFolderId == null) {
            throw new IllegalArgumentException("请选择目标知识库");
        }
        List<KnowledgeDocument> documents = documentRepository.findAllById(ids);
        if (documents.size() != ids.size()) {
            throw new IllegalArgumentException("部分文档不存在，无法批量移动");
        }

        KnowledgeFolder targetFolder = targetFolderId != null
                ? folderRepository.findById(targetFolderId).orElseThrow(() -> new IllegalArgumentException("目标文件夹不存在"))
                : null;
        assertKnowledgeBaseReady(targetFolder);

        EmbeddingModelConfig.EmbeddingModel targetModelConfig = embeddingService.getModelConfig(targetFolder.getEmbeddingModel());
        String targetVectorTable = targetFolder.getVectorTableName() != null && !targetFolder.getVectorTableName().isBlank()
                ? targetFolder.getVectorTableName()
                : targetModelConfig.getVectorTable();

        for (KnowledgeDocument document : documents) {
            Long currentFolderId = document.getFolder() != null ? document.getFolder().getId() : null;
            if (Objects.equals(currentFolderId, targetFolder.getId())) {
                continue;
            }

            if (document.getVectorTableName() != null && !document.getVectorTableName().isBlank()) {
                multiVectorStoreService.deleteDocumentChunks(document.getVectorTableName(), document.getId());
            }

            document.setFolder(targetFolder);
            document.setEmbeddingModel(targetFolder.getEmbeddingModel());
            document.setVectorTableName(targetVectorTable);
            document.setStatus(DocumentStatus.PENDING);
            document.setErrorMessage(null);
            document.setChunkCount(0);
            documentRepository.save(document);
            scheduleDocumentProcessing(document.getId());
        }
    }

    private void deleteDocument(KnowledgeDocument document) {
        if (document.getVectorTableName() != null && !document.getVectorTableName().isBlank()) {
            multiVectorStoreService.deleteDocumentChunks(document.getVectorTableName(), document.getId());
        }

        storageService.deleteFile(document.getStoragePath());

        documentRepository.delete(document);
    }

    private void clearDocumentReferences(List<Long> knowledgeDocumentIds) {
        if (knowledgeDocumentIds == null || knowledgeDocumentIds.isEmpty()) {
            return;
        }
        urlImportItemRepository.clearKnowledgeDocumentReferences(knowledgeDocumentIds);
        knowledgeDocumentSourceRepository.deleteByKnowledgeDocumentIdIn(knowledgeDocumentIds);
    }

    @Transactional
    public void reingestDocument(Long id) {
        reingestDocument(id, null);
    }

    @Transactional
    public void reingestDocument(Long id, String targetEmbeddingModelId) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (document.getFolder() == null || document.getFolder().getId() == null) {
            throw new IllegalArgumentException("文档未绑定知识库，无法重新入库，请先迁移到目标知识库");
        }
        KnowledgeFolder knowledgeBase = folderRepository.findById(document.getFolder().getId())
            .orElseThrow(() -> new IllegalArgumentException("知识库不存在，无法重新入库"));
        assertKnowledgeBaseReady(knowledgeBase);
        String resolvedEmbeddingModelId = knowledgeBase.getEmbeddingModel();
        if (targetEmbeddingModelId != null && !targetEmbeddingModelId.isBlank()
            && !resolvedEmbeddingModelId.equals(targetEmbeddingModelId.trim())) {
            throw new IllegalArgumentException("文档嵌入模型由知识库固定绑定，不能单独指定");
        }
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(resolvedEmbeddingModelId);
        String vectorTableName = knowledgeBase.getVectorTableName() != null && !knowledgeBase.getVectorTableName().isBlank()
            ? knowledgeBase.getVectorTableName()
            : modelConfig.getVectorTable();

        if (document.getVectorTableName() != null && !document.getVectorTableName().isBlank()) {
            multiVectorStoreService.deleteDocumentChunks(document.getVectorTableName(), document.getId());
        }

        document.setEmbeddingModel(resolvedEmbeddingModelId);
        document.setVectorTableName(vectorTableName);
        document.setStatus(DocumentStatus.PENDING);
        document.setErrorMessage(null);
        document.setChunkCount(0);
        documentRepository.save(document);
        scheduleDocumentProcessing(id);
    }

    private void scheduleDocumentProcessing(Long documentId) {
        Runnable task = () -> {
            try {
                processDocumentAsync(documentId);
            } catch (Exception exception) {
                log.error("异步处理知识库文档失败: documentId={}", documentId, exception);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    knowledgeIngestTaskExecutor.execute(task);
                }
            });
            return;
        }

        knowledgeIngestTaskExecutor.execute(task);
    }

    private void scheduleKnowledgeBaseInitialization(Long knowledgeBaseId) {
        Runnable task = () -> {
            try {
                initializeKnowledgeBaseAsync(knowledgeBaseId);
            } catch (Exception exception) {
                log.error("知识库初始化失败: knowledgeBaseId={}", knowledgeBaseId, exception);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    knowledgeIngestTaskExecutor.execute(task);
                }
            });
            return;
        }

        knowledgeIngestTaskExecutor.execute(task);
    }

    @Transactional
    public void initializeKnowledgeBaseAsync(Long knowledgeBaseId) {
        KnowledgeFolder folder = folderRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
        try {
            folder.setInitStatus(KnowledgeBaseInitStatus.INITIALIZING);
            folder.setInitError(null);
            folderRepository.save(folder);

            multiVectorStoreService.getVectorStore(folder.getEmbeddingModel());

            folder.setInitStatus(KnowledgeBaseInitStatus.READY);
            folder.setInitError(null);
            folder.setInitializedAt(java.time.LocalDateTime.now());
            folderRepository.save(folder);
            log.info("知识库初始化完成: knowledgeBaseId={} | embeddingModel={} | vectorTable={}",
                    folder.getId(), folder.getEmbeddingModel(), folder.getVectorTableName());
        } catch (Exception exception) {
            folder.setInitStatus(KnowledgeBaseInitStatus.FAILED);
            folder.setInitError(exception.getMessage());
            folderRepository.save(folder);
            throw exception;
        }
    }

    public void assertKnowledgeBaseReady(KnowledgeFolder folder) {
        if (folder == null) {
            throw new IllegalArgumentException("知识库不存在");
        }

        KnowledgeBaseInitStatus status = folder.getInitStatus();
        if (status == null || status == KnowledgeBaseInitStatus.READY) {
            return;
        }
        if (status == KnowledgeBaseInitStatus.INITIALIZING) {
            throw new IllegalArgumentException("知识库正在初始化，请稍后再试");
        }
        throw new IllegalArgumentException("知识库初始化失败，请修复后重试: " + (folder.getInitError() == null ? "未知错误" : folder.getInitError()));
    }

    public double resolveRerankScoreThreshold() {
        double configured = ragConfig.getRetrieval().getSimilarityThreshold();
        if (configured <= 0 || configured >= 1) {
            return DEFAULT_RERANK_SCORE_THRESHOLD;
        }
        return configured;
    }

    public KnowledgeConfig getConfig() {
        KnowledgeConfig config = configRepository.findById(1L)
                .orElseGet(() -> configRepository.save(KnowledgeConfig.builder().build()));
        KnowledgeConfig originalSnapshot = copyConfig(config);
        KnowledgeConfig normalizedConfig = normalizeConfig(config);
        if (configChanged(originalSnapshot, normalizedConfig)) {
            config = configRepository.save(normalizedConfig);
        } else {
            config = normalizedConfig;
        }
        return config;
    }

    @Transactional
    public KnowledgeConfig updateConfig(KnowledgeConfig config) {
        config.setId(1L);
        return configRepository.save(normalizeConfig(config));
    }

    private KnowledgeConfig normalizeConfig(KnowledgeConfig source) {
        KnowledgeConfig config = source == null ? KnowledgeConfig.builder().build() : source;

        String resolvedDefaultModelId = embeddingService.resolveDefaultModelId(config.getDefaultEmbeddingModel());
        int maxInputChars = embeddingService.resolveMaxInputChars(resolvedDefaultModelId);
        int recommendedChunkSize = Math.min(900, maxInputChars);
        int recommendedOverlap = Math.min(150, Math.max(0, recommendedChunkSize / 3));
        int recommendedMinChunkSize = Math.min(250, recommendedChunkSize);

        config.setDefaultEmbeddingModel(resolvedDefaultModelId);
        config.setChunkSize(normalizeChunkSize(config.getChunkSize(), recommendedChunkSize, maxInputChars));
        config.setChunkOverlap(normalizeChunkOverlap(config.getChunkOverlap(), config.getChunkSize(), recommendedOverlap));
        config.setMinChunkSizeChars(normalizeMinChunkSize(config.getMinChunkSizeChars(), config.getChunkSize(), recommendedMinChunkSize));
        config.setNoSplitMaxChars(normalizeNoSplitMaxChars(config.getNoSplitMaxChars(), config.getChunkSize(), maxInputChars));
        return config;
    }

    private KnowledgeConfig copyConfig(KnowledgeConfig source) {
        return KnowledgeConfig.builder()
                .id(source.getId())
                .chunkSize(source.getChunkSize())
                .chunkOverlap(source.getChunkOverlap())
                .minChunkSizeChars(source.getMinChunkSizeChars())
                .noSplitMaxChars(source.getNoSplitMaxChars())
                .defaultEmbeddingModel(source.getDefaultEmbeddingModel())
                .minioEndpoint(source.getMinioEndpoint())
                .minioAccessKey(source.getMinioAccessKey())
                .minioSecretKey(source.getMinioSecretKey())
                .minioBucketName(source.getMinioBucketName())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }

    private boolean configChanged(KnowledgeConfig before, KnowledgeConfig after) {
        return !Objects.equals(before.getDefaultEmbeddingModel(), after.getDefaultEmbeddingModel())
                || !Objects.equals(before.getChunkSize(), after.getChunkSize())
                || !Objects.equals(before.getChunkOverlap(), after.getChunkOverlap())
                || !Objects.equals(before.getMinChunkSizeChars(), after.getMinChunkSizeChars())
                || !Objects.equals(before.getNoSplitMaxChars(), after.getNoSplitMaxChars());
    }

    private Integer normalizeChunkSize(Integer currentValue, int recommendedValue, int maxInputChars) {
        if (currentValue == null || currentValue <= 0) {
            return recommendedValue;
        }
        return Math.min(currentValue, maxInputChars);
    }

    private Integer normalizeChunkOverlap(Integer currentValue, int chunkSize, int recommendedValue) {
        if (currentValue == null || currentValue < 0) {
            return recommendedValue;
        }
        return Math.min(currentValue, Math.max(0, chunkSize / 3));
    }

    private Integer normalizeMinChunkSize(Integer currentValue, int chunkSize, int recommendedValue) {
        if (currentValue == null || currentValue <= 0) {
            return recommendedValue;
        }
        return Math.min(currentValue, chunkSize);
    }

    private Integer normalizeNoSplitMaxChars(Integer currentValue, int chunkSize, int maxInputChars) {
        if (currentValue == null || currentValue <= 0) {
            return Math.min(maxInputChars, chunkSize);
        }
        return Math.max(chunkSize, Math.min(currentValue, maxInputChars));
    }

    private String removeExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) {
            return fileName;
        }
        return fileName.substring(0, lastDot);
    }

    private String inferCategory(String title, String text) {
        String haystack = (title + " " + text).toLowerCase();
        if (containsAny(haystack, "补贴", "以旧换新", "家电", "国补", "消费券")) {
            return "补贴政策";
        }
        if (containsAny(haystack, "实施细则", "细则", "办法", "规程")) {
            return "实施细则";
        }
        if (containsAny(haystack, "通知", "公告", "通告")) {
            return "通知公告";
        }
        if (containsAny(haystack, "解读", "问答", "答疑")) {
            return "政策解读";
        }
        return "";
    }

    private List<String> inferTags(String title, String text) {
        Set<String> tags = new LinkedHashSet<>();
        String haystack = title + " " + text;
        if (containsAny(haystack, "山东", "山东省", "山东省商务厅")) {
            tags.add(PROVINCE_TAG);
        }
        if (containsAny(haystack, "以旧换新")) {
            tags.add("以旧换新");
        }
        if (containsAny(haystack, "家电")) {
            tags.add("家电");
        }
        if (containsAny(haystack, "汽车")) {
            tags.add("汽车");
        }
        if (containsAny(haystack, "手机", "数码")) {
            tags.add("数码产品");
        }
        Matcher regionMatcher = REGION_PATTERN.matcher(haystack);
        if (regionMatcher.find()) {
            tags.add(regionMatcher.group(1) + "市");
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(haystack);
        if (yearMatcher.find()) {
            tags.add(yearMatcher.group());
        }
        return new ArrayList<>(tags);
    }

    private String inferSource(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String head = text.substring(0, Math.min(2200, text.length()));
        Matcher labeledMatcher = SOURCE_LABEL_PATTERN.matcher(head);
        if (labeledMatcher.find()) {
            return labeledMatcher.group(2).trim();
        }
        return Arrays.stream(head.split("\\R"))
                .map(String::trim)
                .filter(line -> line.length() >= 4 && line.length() <= 40)
                .filter(line -> line.contains("人民政府") || line.contains("发改") || line.contains("委员会")
                        || line.contains("商务厅") || line.contains("商务局") || line.contains("财政厅")
                        || line.contains("财政局") || line.contains("工业和信息化"))
                .findFirst()
                .orElse("");
    }

    private String inferSummary(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String condensed = text.replaceAll("\\s+", " ").trim();
        if (condensed.length() <= 140) {
            return condensed;
        }
        return condensed.substring(0, 140) + "...";
    }

    private boolean containsAny(String text, String... patterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String pattern : patterns) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
