package com.shandong.policyagent.controller;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.*;
import com.shandong.policyagent.rag.EmbeddingService;
import com.shandong.policyagent.rag.KnowledgeService;
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

        return ResponseEntity.ok(toDocumentResponse(document));
    }

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
        Page<KnowledgeDocument> documentPage = knowledgeService.listDocuments(
                folderId, category, tag, status, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("content", documentPage.getContent().stream()
                .map(this::toDocumentResponse).toList());
        result.put("page", documentPage.getNumber());
        result.put("size", documentPage.getSize());
        result.put("totalElements", documentPage.getTotalElements());
        result.put("totalPages", documentPage.getTotalPages());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        return knowledgeService.getDocument(id)
                .map(doc -> ResponseEntity.ok(toDocumentResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
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
        String previewUrl = knowledgeService.getDocumentPreviewUrl(id, 60);
        Map<String, String> result = new HashMap<>();
        result.put("previewUrl", previewUrl);
        return ResponseEntity.ok(result);
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
                .map(doc -> ResponseEntity.ok(toDocumentResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/documents/batch-delete")
    public ResponseEntity<Void> batchDeleteDocuments(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        for (Long id : ids) {
            knowledgeService.deleteDocument(id);
        }
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
        if (request.getDefaultEmbeddingModel() != null) config.setDefaultEmbeddingModel(request.getDefaultEmbeddingModel());
        if (request.getMinioEndpoint() != null) config.setMinioEndpoint(request.getMinioEndpoint());
        if (request.getMinioAccessKey() != null) config.setMinioAccessKey(request.getMinioAccessKey());
        if (request.getMinioSecretKey() != null) config.setMinioSecretKey(request.getMinioSecretKey());
        if (request.getMinioBucketName() != null) config.setMinioBucketName(request.getMinioBucketName());
        return ResponseEntity.ok(knowledgeService.updateConfig(config));
    }

    @GetMapping("/embedding-models")
    public ResponseEntity<Map<String, Object>> getEmbeddingModels() {
        EmbeddingModelConfig.EmbeddingModel defaultModel = embeddingService.getDefaultModel();
        List<EmbeddingModelResponse> models = embeddingService.getAvailableModels().stream()
                .map(m -> EmbeddingModelResponse.builder()
                        .id(m.getId())
                        .name(m.getProvider() + " - " + m.getModelName())
                        .provider(m.getProvider())
                        .dimensions(m.getDimensions())
                        .isDefault(m.getId().equals(defaultModel.getId()))
                        .build())
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("models", models);
        return ResponseEntity.ok(result);
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

    private DocumentResponse toDocumentResponse(KnowledgeDocument doc) {
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
}
