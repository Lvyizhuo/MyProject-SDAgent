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
import com.shandong.policyagent.repository.UrlImportItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final UrlImportItemRepository urlImportItemRepository;

    private final StorageService storageService;
    private final DocumentLoaderService documentLoaderService;
    private final TextSplitterService textSplitterService;
    private final EmbeddingService embeddingService;
    private final MultiVectorStoreService multiVectorStoreService;
    private final MinioConfig minioConfig;
    private static final Pattern SOURCE_LABEL_PATTERN = Pattern.compile("(来源|发布单位|印发单位)[:：]\\s*([^\\n\\r]{2,50})");
    private static final Pattern REGION_PATTERN = Pattern.compile("(济南|青岛|淄博|枣庄|东营|烟台|潍坊|济宁|泰安|威海|日照|临沂|德州|聊城|滨州|菏泽)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("20\\d{2}");
    private static final String PROVINCE_TAG = "山东省";

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

        KnowledgeFolder folder = folderId != null ? folderRepository.findById(folderId).orElse(null) : null;
        String folderPath = folder != null ? folder.getPath() : "/";
        String documentTitle = title == null || title.isBlank() ? "网页导入文档" : title.trim();
        String fileName = documentTitle.replaceAll("[\\/:*?\"<>|\\s]+", "-") + ".txt";

        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(embeddingModelId);
        String storagePath = storageService.storeText(text, folderPath, fileName);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .folder(folder)
                .title(documentTitle)
                .fileName(fileName)
                .fileSize((long) text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .fileType("text/plain")
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

            for (int i = 0; i < splitDocs.size(); i++) {
                Document doc = splitDocs.get(i);
                Map<String, Object> metadata = doc.getMetadata() == null
                        ? new HashMap<>()
                        : new HashMap<>(doc.getMetadata());
                metadata.put("knowledgeDocumentId", documentId);
                metadata.put("sourceTitle", document.getTitle());
                metadata.put("chunkIndex", i + 1);
                if (document.getFolder() != null) {
                    metadata.put("folderPath", document.getFolder().getPath());
                }
                splitDocs.set(i, new Document(doc.getId(), doc.getText(), metadata));
            }

            if (splitDocs.isEmpty()) {
                throw new IllegalStateException("文档未提取到可入库文本，请检查文件内容或 OCR 配置");
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
            documentRepository.saveAndFlush(document);
        }
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
        return storageService.getPresignedUrl(document.getStoragePath(), expirationMinutes);
    }

    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

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
        List<KnowledgeDocument> documents = documentRepository.findAllById(ids);
        if (documents.size() != ids.size()) {
            throw new IllegalArgumentException("部分文档不存在，无法批量移动");
        }

        KnowledgeFolder targetFolder = targetFolderId != null
                ? folderRepository.findById(targetFolderId).orElseThrow(() -> new IllegalArgumentException("目标文件夹不存在"))
                : null;

        for (KnowledgeDocument document : documents) {
            document.setFolder(targetFolder);
            documentRepository.save(document);
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
        String resolvedEmbeddingModelId = targetEmbeddingModelId == null || targetEmbeddingModelId.isBlank()
                ? document.getEmbeddingModel()
                : targetEmbeddingModelId.trim();
        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(resolvedEmbeddingModelId);

        if (document.getVectorTableName() != null && !document.getVectorTableName().isBlank()) {
            multiVectorStoreService.deleteDocumentChunks(document.getVectorTableName(), document.getId());
        }

        document.setEmbeddingModel(resolvedEmbeddingModelId);
        document.setVectorTableName(modelConfig.getVectorTable());
        document.setStatus(DocumentStatus.PENDING);
        document.setErrorMessage(null);
        document.setChunkCount(0);
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
