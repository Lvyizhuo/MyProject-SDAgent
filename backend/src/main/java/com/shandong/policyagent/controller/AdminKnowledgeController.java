package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.entity.KnowledgeDocumentSource;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.*;
import com.shandong.policyagent.rag.EmbeddingService;
import com.shandong.policyagent.rag.KnowledgeService;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import com.shandong.policyagent.service.KnowledgeArchiveService;
import com.shandong.policyagent.service.UrlImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;
    private final EmbeddingService embeddingService;
    private final KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;
    private final KnowledgeArchiveService knowledgeArchiveService;
    private final UrlImportService urlImportService;

    @PostMapping("/folders")
    public ResponseEntity<FolderTreeResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal User currentUser) {
        KnowledgeFolder folder = knowledgeService.createFolder(
                request.getParentId(),
                request.getName(),
                request.getDescription(),
                currentUser
        );
        return ResponseEntity.ok(toFolderTreeResponse(folder));
    }

    @GetMapping("/folders")
    public ResponseEntity<Map<String, Object>> getFolderTree() {
        List<KnowledgeFolder> folders = knowledgeService.getFolderTree();
        List<FolderTreeResponse> responses = folders.stream()
                .map(this::toFolderTreeResponse)
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("folders", responses);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<FolderTreeResponse> updateFolder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFolderRequest request) {
        KnowledgeFolder folder = knowledgeService.updateFolder(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(toFolderTreeResponse(folder));
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable Long id) {
        knowledgeService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "publishDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishDate,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "validFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @RequestParam(value = "validTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
            @RequestParam(value = "summary", required = false) String summary,
            @AuthenticationPrincipal User currentUser) {

        if (embeddingModel == null) {
            embeddingModel = knowledgeService.getConfig().getDefaultEmbeddingModel();
        }

        KnowledgeDocument document = knowledgeService.uploadDocument(
                file, folderId, title, embeddingModel, category, tags,
                publishDate, source, validFrom, validTo, summary, currentUser
        );

        return ResponseEntity.ok(toDocumentResponse(document, null));
    }

    @PostMapping(value = "/documents/extract-metadata", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentMetadataExtractResponse> extractDocumentMetadata(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(knowledgeService.extractDocumentMetadata(file));
    }

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "importJobId", required = false) Long importJobId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
        Page<KnowledgeDocument> documentPage = knowledgeService.listDocuments(
            folderId, importJobId, category, tag, status, keyword, pageable);
        Map<Long, KnowledgeDocumentSource> sourceMapping = knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(
                documentPage.getContent().stream().map(KnowledgeDocument::getId).toList())
            .stream()
            .collect(Collectors.toMap(item -> item.getKnowledgeDocument().getId(), item -> item, (left, right) -> right));

        Map<String, Object> result = new HashMap<>();
        result.put("content", documentPage.getContent().stream()
            .map(doc -> toDocumentResponse(doc, sourceMapping.get(doc.getId()))).toList());
        result.put("page", documentPage.getNumber());
        result.put("size", documentPage.getSize());
        result.put("totalElements", documentPage.getTotalElements());
        result.put("totalPages", documentPage.getTotalPages());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/documents/selection")
    public ResponseEntity<Map<String, Object>> listDocumentSelection(
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "importJobId", required = false) Long importJobId,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(value = "q", required = false) String keyword) {
        List<Long> ids = knowledgeService.listDocumentIds(folderId, importJobId, status, keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("ids", ids);
        result.put("count", ids.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        return knowledgeService.getDocument(id)
            .map(doc -> ResponseEntity.ok(toDocumentResponse(doc,
                knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(List.of(doc.getId())).stream().findFirst().orElse(null))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/documents/{id}/chunks")
    public ResponseEntity<DocumentChunksPageResponse> listDocumentChunks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(knowledgeService.listDocumentChunks(id, page, size));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long id) {
        KnowledgeDocument document = knowledgeService.getDocument(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        InputStream inputStream = knowledgeService.downloadDocument(id);
        InputStreamResource resource = new InputStreamResource(inputStream);

        String encodedFileName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .contentLength(document.getFileSize())
                .body(resource);
    }

    @GetMapping("/documents/{id}/preview")
    public ResponseEntity<Map<String, String>> getDocumentPreview(@PathVariable Long id) {
        KnowledgeDocumentSource sourceMapping = knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(List.of(id))
            .stream()
            .findFirst()
            .orElse(null);
        String previewUrl = sourceMapping != null && sourceMapping.getSourceUrl() != null && !sourceMapping.getSourceUrl().isBlank()
            ? sourceMapping.getSourceUrl()
            : knowledgeService.getDocumentPreviewUrl(id, 60);
        Map<String, String> result = new HashMap<>();
        result.put("previewUrl", previewUrl);
        result.put("previewMode", sourceMapping != null ? "external" : "minio");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/archive/export")
    public ResponseEntity<InputStreamResource> exportKnowledgeArchive() {
        byte[] archiveBytes = knowledgeArchiveService.exportArchive();
        InputStreamResource resource = new InputStreamResource(new java.io.ByteArrayInputStream(archiveBytes));
        String fileName = "knowledge-archive-" + java.time.LocalDate.now() + ".zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(archiveBytes.length)
                .body(resource);
    }

    @PostMapping(value = "/archive/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeArchiveImportResponse> importKnowledgeArchive(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(knowledgeArchiveService.importArchive(file, currentUser));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/{id}/reingest")
    public ResponseEntity<DocumentResponse> reingestDocument(@PathVariable Long id) {
        knowledgeService.reingestDocument(id);
        return knowledgeService.getDocument(id)
            .map(doc -> ResponseEntity.ok(toDocumentResponse(doc,
                knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(List.of(doc.getId())).stream().findFirst().orElse(null))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/documents/batch-delete")
    public ResponseEntity<Void> batchDeleteDocuments(@Valid @RequestBody BatchDocumentOperationRequest request) {
        knowledgeService.batchDeleteDocuments(request.getIds());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/batch-move")
    public ResponseEntity<Void> batchMoveDocuments(@Valid @RequestBody BatchDocumentOperationRequest request) {
        knowledgeService.batchMoveDocuments(request.getIds(), request.getTargetFolderId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config")
    public ResponseEntity<KnowledgeConfig> getConfig() {
        return ResponseEntity.ok(knowledgeService.getConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<KnowledgeConfig> updateConfig(@Valid @RequestBody UpdateKnowledgeConfigRequest request) {
        KnowledgeConfig config = knowledgeService.getConfig();
        if (request.getChunkSize() != null) config.setChunkSize(request.getChunkSize());
        if (request.getChunkOverlap() != null) config.setChunkOverlap(request.getChunkOverlap());
        if (request.getMinChunkSizeChars() != null) config.setMinChunkSizeChars(request.getMinChunkSizeChars());
        if (request.getNoSplitMaxChars() != null) config.setNoSplitMaxChars(request.getNoSplitMaxChars());
        if (request.getDefaultEmbeddingModel() != null) {
            config.setDefaultEmbeddingModel(embeddingService.validateOrDefaultModelId(request.getDefaultEmbeddingModel()));
        }
        if (request.getMinioEndpoint() != null) config.setMinioEndpoint(request.getMinioEndpoint());
        if (request.getMinioAccessKey() != null) config.setMinioAccessKey(request.getMinioAccessKey());
        if (request.getMinioSecretKey() != null) config.setMinioSecretKey(request.getMinioSecretKey());
        if (request.getMinioBucketName() != null) config.setMinioBucketName(request.getMinioBucketName());
        return ResponseEntity.ok(knowledgeService.updateConfig(config));
    }

    @GetMapping("/embedding-models")
    public ResponseEntity<Map<String, Object>> getEmbeddingModels() {
        String defaultModelId = embeddingService.resolveDefaultModelId(knowledgeService.getConfig().getDefaultEmbeddingModel());
        final String resolvedDefaultModelId = defaultModelId;
        List<EmbeddingModelResponse> models = embeddingService.getAvailableModels().stream()
                .map(m -> EmbeddingModelResponse.builder()
                        .id(m.getId())
                        .name(m.getProvider() + " - " + m.getModelName())
                        .provider(m.getProvider())
                        .dimensions(m.getDimensions())
                        .isDefault(m.getId().equals(resolvedDefaultModelId))
                        .build())
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("models", models);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/url-imports")
    public ResponseEntity<UrlImportJobResponse> createUrlImport(
            @Valid @RequestBody UrlImportCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlImportService.createImport(request, currentUser));
    }

    @GetMapping("/url-imports")
    public ResponseEntity<UrlImportListResponse> listUrlImports() {
        return ResponseEntity.ok(urlImportService.listImports());
    }

    @GetMapping("/url-imports/{id}")
    public ResponseEntity<UrlImportItemResponse> getUrlImportItem(@PathVariable Long id) {
        return ResponseEntity.ok(urlImportService.getImportItem(id));
    }

    @PostMapping("/url-imports/{id}/confirm")
    public ResponseEntity<DocumentResponse> confirmUrlImport(
            @PathVariable Long id,
            @RequestBody UrlImportConfirmRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlImportService.confirmImport(id, request, currentUser));
    }

    @PostMapping("/url-imports/batch-confirm")
    public ResponseEntity<BatchUrlImportConfirmResponse> batchConfirmUrlImports(
            @RequestBody BatchUrlImportConfirmRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlImportService.batchConfirmImports(request, currentUser));
    }

    @PostMapping("/url-imports/{id}/reject")
    public ResponseEntity<Void> rejectUrlImport(
            @PathVariable Long id,
            @Valid @RequestBody UrlImportRejectRequest request) {
        urlImportService.rejectImport(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/url-imports/{id}/cancel")
    public ResponseEntity<Void> cancelUrlImport(@PathVariable Long id) {
        urlImportService.cancelImportJob(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/url-imports/{id}")
    public ResponseEntity<Void> deleteUrlImport(@PathVariable Long id) {
        urlImportService.deleteImportJob(id);
        return ResponseEntity.noContent().build();
    }

    private FolderTreeResponse toFolderTreeResponse(KnowledgeFolder folder) {
        return FolderTreeResponse.builder()
                .id(folder.getId())
                .parentId(folder.getParent() != null ? folder.getParent().getId() : null)
                .name(folder.getName())
                .description(folder.getDescription())
                .path(folder.getPath())
                .depth(folder.getDepth())
                .children(folder.getChildren() != null
                        ? folder.getChildren().stream().map(this::toFolderTreeResponse).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private DocumentResponse toDocumentResponse(KnowledgeDocument doc, KnowledgeDocumentSource sourceMapping) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .folderId(doc.getFolder() != null ? doc.getFolder().getId() : null)
                .folderPath(doc.getFolder() != null ? doc.getFolder().getPath() : "/")
                .title(doc.getTitle())
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .fileType(doc.getFileType())
                .embeddingModel(doc.getEmbeddingModel())
                .category(doc.getCategory())
                .tags(doc.getTags())
                .publishDate(doc.getPublishDate())
                .source(doc.getSource())
                .websiteImported(sourceMapping != null)
                .externalSourceUrl(sourceMapping != null ? sourceMapping.getSourceUrl() : null)
                .externalSourcePage(sourceMapping != null ? sourceMapping.getSourcePage() : null)
                .externalSourceSite(sourceMapping != null ? sourceMapping.getSourceSite() : null)
                .importJobId(sourceMapping != null && sourceMapping.getImportItem() != null && sourceMapping.getImportItem().getJob() != null
                    ? sourceMapping.getImportItem().getJob().getId()
                    : null)
                .importItemId(sourceMapping != null && sourceMapping.getImportItem() != null
                    ? sourceMapping.getImportItem().getId()
                    : null)
                .validFrom(doc.getValidFrom())
                .validTo(doc.getValidTo())
                .summary(doc.getSummary())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .chunkCount(doc.getChunkCount())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    @DeleteMapping("/url-import-items/{id}")
    public ResponseEntity<Void> deleteUrlImportItem(@PathVariable Long id) {
        urlImportService.deleteImportItem(id);
        return ResponseEntity.noContent().build();
    }
}
