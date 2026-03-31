package com.shandong.policyagent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.entity.KnowledgeDocumentSource;
import com.shandong.policyagent.entity.KnowledgeBaseInitStatus;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.entity.UrlImportItem;
import com.shandong.policyagent.entity.UrlImportItemType;
import com.shandong.policyagent.entity.UrlImportJob;
import com.shandong.policyagent.entity.UrlImportJobStatus;
import com.shandong.policyagent.entity.UrlImportParseStatus;
import com.shandong.policyagent.entity.UrlImportReviewStatus;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.KnowledgeArchiveImportResponse;
import com.shandong.policyagent.rag.EmbeddingService;
import com.shandong.policyagent.rag.KnowledgeService;
import com.shandong.policyagent.rag.StorageService;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.repository.UrlImportItemRepository;
import com.shandong.policyagent.repository.UrlImportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeArchiveService {

    private static final String ARCHIVE_VERSION = "1";
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String FILES_PREFIX = "files/";
    private static final String ROOT_IMPORT_KNOWLEDGE_BASE_PATH = "/导入知识库";

    private final KnowledgeService knowledgeService;
    private final StorageService storageService;
    private final KnowledgeFolderRepository knowledgeFolderRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;
    private final UrlImportJobRepository urlImportJobRepository;
    private final UrlImportItemRepository urlImportItemRepository;
    private final EmbeddingService embeddingService;
    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;

    public byte[] exportArchive() {
        List<KnowledgeFolder> folders = knowledgeFolderRepository.findAll(
                Sort.by(Sort.Order.asc("depth"), Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"))
        );
        List<KnowledgeDocument> documents = knowledgeDocumentRepository.findAll(
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        );
        Map<Long, KnowledgeDocumentSource> sourceMapping = buildSourceMapping(documents.stream()
                .map(KnowledgeDocument::getId)
                .toList());

        KnowledgeArchiveManifest manifest = new KnowledgeArchiveManifest();
        manifest.setVersion(ARCHIVE_VERSION);
        manifest.setExportedAt(LocalDateTime.now().toString());
        manifest.setDefaultEmbeddingModel(knowledgeService.getConfig().getDefaultEmbeddingModel());
        manifest.setFolders(folders.stream()
                .map(folder -> FolderEntry.builder()
                        .path(folder.getPath())
                        .name(folder.getName())
                        .description(folder.getDescription())
                        .depth(folder.getDepth())
                        .build())
                .toList());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {

            List<DocumentEntry> manifestDocuments = new ArrayList<>();
            int index = 1;
            for (KnowledgeDocument document : documents) {
                String fileEntryPath = FILES_PREFIX + String.format(Locale.ROOT, "%04d-%s",
                        index++, sanitizeEntryName(document.getFileName()));
                writeDocumentEntry(zipOutputStream, fileEntryPath, document);

                KnowledgeDocumentSource source = sourceMapping.get(document.getId());
                manifestDocuments.add(DocumentEntry.builder()
                        .folderPath(document.getFolder() != null ? document.getFolder().getPath() : "/")
                        .title(document.getTitle())
                        .fileName(document.getFileName())
                        .fileType(document.getFileType())
                        .embeddingModel(document.getEmbeddingModel())
                        .category(document.getCategory())
                        .tags(document.getTags())
                        .publishDate(document.getPublishDate())
                        .source(document.getSource())
                        .validFrom(document.getValidFrom())
                        .validTo(document.getValidTo())
                        .summary(document.getSummary())
                        .fileEntryPath(fileEntryPath)
                        .externalSource(source == null ? null : ExternalSourceEntry.builder()
                                .sourceUrl(source.getSourceUrl())
                                .sourcePage(source.getSourcePage())
                                .sourceSite(source.getSourceSite())
                                .build())
                        .build());
            }

            manifest.setDocuments(manifestDocuments);

            ZipEntry manifestEntry = new ZipEntry(MANIFEST_ENTRY);
            zipOutputStream.putNextEntry(manifestEntry);
            zipOutputStream.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("导出知识库归档失败", exception);
        }
    }

    public KnowledgeArchiveImportResponse importArchive(MultipartFile archive, User currentUser) {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }

        ArchiveBundle bundle = readArchive(archive);
        KnowledgeArchiveManifest manifest = parseManifest(bundle.manifestBytes());
        Map<String, KnowledgeFolder> folderMapping = loadExistingFolders();
        List<String> messages = new ArrayList<>();
        String archiveDefaultEmbeddingModel = resolveImportEmbeddingModel(manifest.getDefaultEmbeddingModel(), messages);

        if (manifest.getFolders() != null) {
            manifest.getFolders().stream()
                    .sorted(Comparator.comparingInt(folder -> folder.getDepth() == null ? 0 : folder.getDepth()))
                    .forEach(folder -> ensureFolder(folder.getPath(), folder.getDescription(), archiveDefaultEmbeddingModel, currentUser, folderMapping, messages));
        }

        int importedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        ArchiveImportSourceContext sourceContext = new ArchiveImportSourceContext();

        for (DocumentEntry entry : manifest.getDocuments()) {
            try {
                String folderPath = normalizeFolderPath(entry.getFolderPath());
                byte[] fileBytes = bundle.files().get(entry.getFileEntryPath());
                if (fileBytes == null) {
                    throw new IllegalArgumentException("归档中缺少文件内容: " + entry.getFileEntryPath());
                }

                KnowledgeFolder folder = ensureFolder(folderPath, null, archiveDefaultEmbeddingModel, currentUser, folderMapping, messages);

                if (isDuplicate(entry, folder.getPath())) {
                    skippedCount++;
                    messages.add("跳过重复文档: " + safeTitle(entry));
                    continue;
                }

                String resolvedEmbeddingModel = resolveImportEmbeddingModel(entry.getEmbeddingModel(), messages);
                if (!resolvedEmbeddingModel.equals(folder.getEmbeddingModel())) {
                    messages.add("归档文档嵌入模型与目标知识库不一致，已按知识库模型导入: "
                            + safeTitle(entry)
                            + " | docModel=" + resolvedEmbeddingModel
                            + " -> knowledgeBaseModel=" + folder.getEmbeddingModel());
                }
                KnowledgeDocument importedDocument = knowledgeService.importArchivedDocument(
                        fileBytes,
                        folder.getId(),
                        entry.getTitle(),
                        entry.getFileName(),
                        entry.getFileType(),
                        null,
                        entry.getCategory(),
                        entry.getTags(),
                        entry.getPublishDate(),
                        entry.getSource(),
                        entry.getValidFrom(),
                        entry.getValidTo(),
                        entry.getSummary(),
                        currentUser
                );

                if (entry.getExternalSource() != null && entry.getExternalSource().hasSourceUrl()) {
                    bindImportedSource(importedDocument, entry, folder, currentUser, sourceContext);
                }

                importedCount++;
            } catch (Exception exception) {
                failedCount++;
                messages.add("导入失败: " + safeTitle(entry) + " | " + exception.getMessage());
                log.error("导入知识库归档文档失败: {}", safeTitle(entry), exception);
            }
        }

        finalizeSourceImportJob(sourceContext);

        return KnowledgeArchiveImportResponse.builder()
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .messages(messages)
                .build();
    }

    private Map<Long, KnowledgeDocumentSource> buildSourceMapping(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        return knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(documentIds).stream()
                .filter(item -> item.getKnowledgeDocument() != null && item.getKnowledgeDocument().getId() != null)
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getKnowledgeDocument().getId(), item), Map::putAll);
    }

    private void writeDocumentEntry(ZipOutputStream zipOutputStream,
                                    String fileEntryPath,
                                    KnowledgeDocument document) throws IOException {
        ZipEntry zipEntry = new ZipEntry(fileEntryPath);
        zipOutputStream.putNextEntry(zipEntry);
        try (InputStream inputStream = storageService.getFile(document.getStoragePath())) {
            copy(inputStream, zipOutputStream);
        }
        zipOutputStream.closeEntry();
    }

    private ArchiveBundle readArchive(MultipartFile archive) {
        Map<String, byte[]> files = new HashMap<>();
        byte[] manifestBytes = null;

        try (ZipInputStream zipInputStream = new ZipInputStream(archive.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                byte[] entryBytes = zipInputStream.readAllBytes();
                if (MANIFEST_ENTRY.equals(zipEntry.getName())) {
                    manifestBytes = entryBytes;
                } else {
                    files.put(zipEntry.getName(), entryBytes);
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取知识库归档失败", exception);
        }

        if (manifestBytes == null) {
            throw new IllegalArgumentException("归档中缺少 manifest.json");
        }
        return new ArchiveBundle(manifestBytes, files);
    }

    private KnowledgeArchiveManifest parseManifest(byte[] manifestBytes) {
        try {
            KnowledgeArchiveManifest manifest = objectMapper.readValue(manifestBytes, KnowledgeArchiveManifest.class);
            if (manifest.getDocuments() == null) {
                manifest.setDocuments(List.of());
            }
            if (manifest.getFolders() == null) {
                manifest.setFolders(List.of());
            }
            return manifest;
        } catch (IOException exception) {
            throw new IllegalArgumentException("解析知识库归档清单失败", exception);
        }
    }

    private Map<String, KnowledgeFolder> loadExistingFolders() {
        Map<String, KnowledgeFolder> folderMapping = new HashMap<>();
        for (KnowledgeFolder folder : knowledgeFolderRepository.findAll()) {
            folderMapping.put(folder.getPath(), folder);
        }
        return folderMapping;
    }

    private KnowledgeFolder ensureFolder(String rawPath,
                                         String description,
                                         String defaultEmbeddingModel,
                                         User currentUser,
                                         Map<String, KnowledgeFolder> folderMapping,
                                         List<String> messages) {
        String path = normalizeFolderPath(rawPath);
        if ("/".equals(path)) {
            path = ROOT_IMPORT_KNOWLEDGE_BASE_PATH;
        }

        String resolvedEmbeddingModel = resolveImportEmbeddingModel(defaultEmbeddingModel, messages);
        String vectorTableName = embeddingService.getModelConfig(resolvedEmbeddingModel).getVectorTable();
        ModelProvider defaultRerankModel = modelProviderService.getDefaultModel(ModelType.RERANK);

        if (folderMapping.containsKey(path)) {
            KnowledgeFolder existing = folderMapping.get(path);
            boolean changed = false;
            if ((existing.getDescription() == null || existing.getDescription().isBlank())
                    && description != null
                    && !description.isBlank()) {
                existing.setDescription(description);
                changed = true;
            }
            if (existing.getEmbeddingModel() == null || existing.getEmbeddingModel().isBlank()) {
                existing.setEmbeddingModel(resolvedEmbeddingModel);
                changed = true;
            }
            if (existing.getVectorTableName() == null || existing.getVectorTableName().isBlank()) {
                existing.setVectorTableName(vectorTableName);
                changed = true;
            }
            if (existing.getRerankModelId() == null && defaultRerankModel != null) {
                existing.setRerankModelId(defaultRerankModel.getId());
                existing.setRerankModelName(defaultRerankModel.getModelName());
                changed = true;
            }
            if (existing.getInitStatus() == null) {
                existing.setInitStatus(KnowledgeBaseInitStatus.READY);
                changed = true;
            }
            if (changed) {
                existing = knowledgeFolderRepository.save(existing);
                folderMapping.put(path, existing);
            }
            return existing;
        }

        String name = path.substring(1).replace('/', '·');
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }

        KnowledgeFolder created = KnowledgeFolder.builder()
                .parent(null)
                .name(name)
                .description(description)
                .path(path)
                .depth(1)
                .sortOrder(0)
                .embeddingModel(resolvedEmbeddingModel)
                .vectorTableName(vectorTableName)
                .rerankModelId(defaultRerankModel != null ? defaultRerankModel.getId() : null)
                .rerankModelName(defaultRerankModel != null ? defaultRerankModel.getModelName() : null)
                .initStatus(KnowledgeBaseInitStatus.READY)
                .initializedAt(LocalDateTime.now())
                .createdBy(currentUser)
                .build();
        created = knowledgeFolderRepository.save(created);
        folderMapping.put(path, created);
        return created;
    }

    private boolean isDuplicate(DocumentEntry entry, String folderPath) {
        if (entry.getExternalSource() != null && entry.getExternalSource().hasSourceUrl()) {
            Optional<KnowledgeDocumentSource> source = knowledgeDocumentSourceRepository
                    .findFirstBySourceUrlOrderByCreatedAtDesc(entry.getExternalSource().getSourceUrl());
            if (source.isPresent()) {
                return true;
            }
        }
        return knowledgeDocumentRepository.findFirstByFolderPathAndTitleAndFileName(
                folderPath,
                defaultString(entry.getTitle(), entry.getFileName()),
                defaultString(entry.getFileName(), defaultString(entry.getTitle(), "document.bin"))
        ).isPresent();
    }

    private String resolveImportEmbeddingModel(String requestedEmbeddingModel, List<String> messages) {
        try {
            return embeddingService.validateOrDefaultModelId(requestedEmbeddingModel);
        } catch (Exception exception) {
            String resolvedDefault = knowledgeService.getConfig().getDefaultEmbeddingModel();
            messages.add("嵌入模型不可用，已回退为默认模型: "
                    + defaultString(requestedEmbeddingModel, "未指定")
                    + " -> " + resolvedDefault);
            return resolvedDefault;
        }
    }

    private void bindImportedSource(KnowledgeDocument document,
                                    DocumentEntry entry,
                                    KnowledgeFolder folder,
                                    User currentUser,
                                    ArchiveImportSourceContext sourceContext) {
        UrlImportJob job = getOrCreateArchiveImportJob(folder, document.getEmbeddingModel(), currentUser, sourceContext);
        ExternalSourceEntry externalSource = entry.getExternalSource();
        UrlImportItem item = UrlImportItem.builder()
                .job(job)
                .sourceUrl(externalSource.getSourceUrl())
                .sourcePage(externalSource.getSourcePage())
                .sourceSite(defaultString(externalSource.getSourceSite(), defaultString(entry.getSource(), "知识库导入包")))
                .itemType(UrlImportItemType.ARTICLE)
                .sourceTitle(entry.getTitle())
                .publishDate(entry.getPublishDate())
                .cleanedText(null)
                .qualityScore(100)
                .parseStatus(UrlImportParseStatus.PARSED)
                .reviewStatus(UrlImportReviewStatus.CONFIRMED)
                .suspectedDuplicate(false)
                .category(entry.getCategory())
                .tags(entry.getTags())
                .summary(entry.getSummary())
                .knowledgeDocument(document)
                .build();
        item = urlImportItemRepository.save(item);

        knowledgeDocumentSourceRepository.save(KnowledgeDocumentSource.builder()
                .knowledgeDocument(document)
                .importItem(item)
                .sourceSite(item.getSourceSite())
                .sourceUrl(item.getSourceUrl())
                .sourcePage(item.getSourcePage())
                .build());

        sourceContext.importedSourceCount++;
    }

    private UrlImportJob getOrCreateArchiveImportJob(KnowledgeFolder folder,
                                                     String embeddingModel,
                                                     User currentUser,
                                                     ArchiveImportSourceContext sourceContext) {
        if (sourceContext.job != null) {
            return sourceContext.job;
        }
        UrlImportJob job = UrlImportJob.builder()
                .sourceUrl("archive-import://" + UUID.randomUUID())
                .sourceSite("知识库导入包")
                .targetFolder(folder)
                .embeddingModel(embeddingModel)
                .titleOverride("知识库导入包恢复")
                .remark("由知识库导出包恢复，仅用于保留原始网页来源与预览链接")
                .status(UrlImportJobStatus.COMPLETED)
                .discoveredCount(0)
                .candidateCount(0)
                .importedCount(0)
                .rejectedCount(0)
                .deletedAt(LocalDateTime.now())
                .createdBy(currentUser)
                .build();
        sourceContext.job = urlImportJobRepository.save(job);
        return sourceContext.job;
    }

    private void finalizeSourceImportJob(ArchiveImportSourceContext sourceContext) {
        if (sourceContext.job == null) {
            return;
        }
        sourceContext.job.setDiscoveredCount(sourceContext.importedSourceCount);
        sourceContext.job.setCandidateCount(sourceContext.importedSourceCount);
        sourceContext.job.setImportedCount(sourceContext.importedSourceCount);
        urlImportJobRepository.save(sourceContext.job);
    }

    private void copy(InputStream inputStream, ZipOutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, read);
        }
    }

    private String sanitizeEntryName(String fileName) {
        String normalized = defaultString(fileName, "document.bin");
        normalized = normalized.replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_");
        return normalized.isBlank() ? "document.bin" : normalized;
    }

    private String normalizeFolderPath(String folderPath) {
        if (folderPath == null || folderPath.isBlank() || "/".equals(folderPath.trim())) {
            return "/";
        }
        String normalized = folderPath.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String safeTitle(DocumentEntry entry) {
        return defaultString(entry.getTitle(), entry.getFileName());
    }

    private String defaultString(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private record ArchiveBundle(byte[] manifestBytes, Map<String, byte[]> files) {
    }

    private static final class ArchiveImportSourceContext {
        private UrlImportJob job;
        private int importedSourceCount;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KnowledgeArchiveManifest {
        private String version;
        private String exportedAt;
        private String defaultEmbeddingModel;
        private List<FolderEntry> folders = List.of();
        private List<DocumentEntry> documents = List.of();
    }

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FolderEntry {
        private String path;
        private String name;
        private String description;
        private Integer depth;
    }

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class DocumentEntry {
        private String folderPath;
        private String title;
        private String fileName;
        private String fileType;
        private String embeddingModel;
        private String category;
        private List<String> tags;
        private LocalDate publishDate;
        private String source;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String summary;
        private String fileEntryPath;
        private ExternalSourceEntry externalSource;
    }

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ExternalSourceEntry {
        private String sourceUrl;
        private String sourcePage;
        private String sourceSite;

        private boolean hasSourceUrl() {
            return sourceUrl != null && !sourceUrl.isBlank();
        }
    }
}
