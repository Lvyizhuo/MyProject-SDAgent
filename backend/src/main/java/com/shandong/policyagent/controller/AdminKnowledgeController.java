package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.entity.KnowledgeDocumentSource;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.*;
import com.shandong.policyagent.rag.EmbeddingService;
import com.shandong.policyagent.rag.KnowledgeService;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import com.shandong.policyagent.service.ModelProviderService;
import com.shandong.policyagent.service.KnowledgeArchiveService;
import com.shandong.policyagent.service.UrlImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
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
@Tag(name = "知识库管理", description = "知识库文件夹、文档、网站导入与归档管理")
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;
    private final EmbeddingService embeddingService;
    private final ModelProviderService modelProviderService;
    private final KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;
    private final KnowledgeArchiveService knowledgeArchiveService;
    private final UrlImportService urlImportService;

    @Operation(summary = "创建知识库文件夹", description = "创建新的知识库文件夹，可绑定嵌入模型")
    @PostMapping("/folders")
    public ResponseEntity<FolderTreeResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal User currentUser) {
        KnowledgeFolder folder = knowledgeService.createFolder(
                request.getParentId(),
                request.getName(),
                request.getDescription(),
            request.getEmbeddingModel(),
            request.getRerankModelId(),
                currentUser
        );
        return ResponseEntity.ok(toFolderTreeResponse(folder));
    }

    @Operation(summary = "获取文件夹树", description = "获取知识库文件夹树形结构")
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

    @Operation(summary = "更新文件夹", description = "按 ID 更新知识库文件夹名称与描述")
    @PutMapping("/folders/{id}")
    public ResponseEntity<FolderTreeResponse> updateFolder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFolderRequest request) {
        KnowledgeFolder folder = knowledgeService.updateFolder(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(toFolderTreeResponse(folder));
    }

    @Operation(summary = "删除文件夹", description = "按 ID 删除知识库文件夹及其文档")
    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable Long id) {
        knowledgeService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "上传文档", description = "上传 PDF/DOCX 文档到知识库，支持元数据参数（multipart/form-data）")
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

        KnowledgeDocument document = knowledgeService.uploadDocument(
                file, folderId, title, embeddingModel, category, tags,
                publishDate, source, validFrom, validTo, summary, currentUser
        );

        return ResponseEntity.ok(toDocumentResponse(document, null));
    }

    @Operation(summary = "提取文档元数据", description = "上传文件并自动提取标题、日期等元数据，用于上传前预览")
    @PostMapping(value = "/documents/extract-metadata", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentMetadataExtractResponse> extractDocumentMetadata(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(knowledgeService.extractDocumentMetadata(file));
    }

    @Operation(summary = "获取文档列表（分页）", description = "按文件夹/分类/状态/关键词分页查询文档列表")
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

    @Operation(summary = "获取文档 ID 选择列表", description = "按条件返回文档 ID 列表，用于批量操作选择")
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

    @Operation(summary = "获取文档详情", description = "按 ID 获取文档详情与元数据")
    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        return knowledgeService.getDocument(id)
            .map(doc -> ResponseEntity.ok(toDocumentResponse(doc,
                knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(List.of(doc.getId())).stream().findFirst().orElse(null))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取文档切片列表", description = "分页查看文档切分后的文本切片")
    @GetMapping("/documents/{id}/chunks")
    public ResponseEntity<DocumentChunksPageResponse> listDocumentChunks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "parent") String chunkLevel) {
        return ResponseEntity.ok(knowledgeService.listDocumentChunks(id, page, size, chunkLevel));
    }

    @Operation(summary = "下载文档", description = "下载知识库中的原始文档文件")
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

    @Operation(summary = "获取文档预览", description = "获取文档预览地址（外部站点或 MinIO 临时链接）")
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

    @Operation(summary = "导出知识库归档", description = "将知识库全部数据导出为 ZIP 归档文件（流式输出，避免大知识库导出占用内存）")
    @GetMapping("/archive/export")
    public void exportKnowledgeArchive(HttpServletResponse response) throws IOException {
        String fileName = "knowledge-archive-" + java.time.LocalDate.now() + ".zip";

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        knowledgeArchiveService.exportArchive(response.getOutputStream());
    }

    @Operation(summary = "导入知识库归档", description = "上传 ZIP 归档文件恢复知识库数据")
    @PostMapping(value = "/archive/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeArchiveImportResponse> importKnowledgeArchive(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(knowledgeArchiveService.importArchive(file, currentUser));
    }

    @Operation(summary = "删除文档", description = "按 ID 删除知识库文档及其向量数据")
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "重新入库文档", description = "对文档重新切片、嵌入并入库（用于嵌入模型切换后）")
    @PostMapping("/documents/{id}/reingest")
    public ResponseEntity<DocumentResponse> reingestDocument(@PathVariable Long id) {
        knowledgeService.reingestDocument(id);
        return knowledgeService.getDocument(id)
            .map(doc -> ResponseEntity.ok(toDocumentResponse(doc,
                knowledgeDocumentSourceRepository.findByKnowledgeDocumentIdIn(List.of(doc.getId())).stream().findFirst().orElse(null))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "批量删除文档", description = "按 ID 列表批量删除文档")
    @PostMapping("/documents/batch-delete")
    public ResponseEntity<Void> batchDeleteDocuments(@Valid @RequestBody BatchDocumentOperationRequest request) {
        knowledgeService.batchDeleteDocuments(request.getIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "批量移动文档", description = "按 ID 列表批量移动文档到目标文件夹")
    @PostMapping("/documents/batch-move")
    public ResponseEntity<Void> batchMoveDocuments(@Valid @RequestBody BatchDocumentOperationRequest request) {
        knowledgeService.batchMoveDocuments(request.getIds(), request.getTargetFolderId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "获取知识库配置", description = "获取切片参数、嵌入模型、MinIO 等知识库全局配置")
    @GetMapping("/config")
    public ResponseEntity<KnowledgeConfig> getConfig() {
        return ResponseEntity.ok(knowledgeService.getConfig());
    }

    @Operation(summary = "更新知识库配置", description = "部分更新知识库全局配置（仅更新传入字段）")
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

    @Operation(summary = "获取可用嵌入模型", description = "获取配置了向量映射的嵌入模型列表及默认模型")
    @GetMapping("/embedding-models")
    public ResponseEntity<Map<String, Object>> getEmbeddingModels() {
        List<com.shandong.policyagent.model.ModelProviderResponse> configuredEmbeddingModels = modelProviderService
            .getAllModels(ModelType.EMBEDDING)
            .stream()
            .filter(model -> Boolean.TRUE.equals(model.getBuiltIn()) || !Boolean.FALSE.equals(model.getIsEnabled()))
            .toList();

        String defaultModelId = embeddingService.resolveDefaultModelId(knowledgeService.getConfig().getDefaultEmbeddingModel());

        List<EmbeddingModelResponse> mappedModels = configuredEmbeddingModels.stream()
            .map(model -> {
                String mappedModelId = null;
                if (Boolean.TRUE.equals(model.getBuiltIn())
                    && model.getBuiltinCode() != null
                    && !model.getBuiltinCode().isBlank()) {
                    mappedModelId = model.getBuiltinCode();
                }
                if (mappedModelId == null) {
                    mappedModelId = embeddingService.findMappedModelId(model.getModelName(), model.getApiUrl());
                }
                if (mappedModelId == null) {
                    mappedModelId = embeddingService.findMappedModelId(model.getModelName(), null);
                }
                if (mappedModelId == null) {
                    mappedModelId = embeddingService.findMappedModelId(model.getName(), null);
                }
                if (mappedModelId == null) {
                    log.warn("嵌入模型未匹配到系统向量配置，已跳过: modelId={} | name={} | modelName={} | apiUrl={}",
                        model.getId(), model.getName(), model.getModelName(), model.getApiUrl());
                    return null;
                }
                Integer dimensions = embeddingService.getModelConfig(mappedModelId).getDimensions();
                return EmbeddingModelResponse.builder()
                    .id(mappedModelId)
                    .name(model.getName())
                    .provider(model.getProvider())
                    .dimensions(dimensions)
                    .isDefault(Boolean.TRUE.equals(model.getIsDefault()) || mappedModelId.equals(defaultModelId))
                    .build();
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toMap(
                EmbeddingModelResponse::getId,
                item -> item,
                (left, right) -> Boolean.TRUE.equals(left.isDefault()) ? left : right,
                java.util.LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("models", mappedModels);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "创建网站导入任务", description = "提交网址爬取任务，抓取政策网站内容")
    @PostMapping("/url-imports")
    public ResponseEntity<UrlImportJobResponse> createUrlImport(
            @Valid @RequestBody UrlImportCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlImportService.createImport(request, currentUser));
    }

    @Operation(summary = "获取网站导入任务列表", description = "获取所有网站导入任务及抓取状态")
    @GetMapping("/url-imports")
    public ResponseEntity<UrlImportListResponse> listUrlImports() {
        return ResponseEntity.ok(urlImportService.listImports());
    }

    @Operation(summary = "获取导入任务详情", description = "获取网站导入任务详情及抓取到的页面列表")
    @GetMapping("/url-imports/{id}")
    public ResponseEntity<UrlImportItemResponse> getUrlImportItem(@PathVariable Long id) {
        return ResponseEntity.ok(urlImportService.getImportItem(id));
    }

    @Operation(summary = "确认导入页面", description = "确认抓取到的页面入库为知识库文档")
    @PostMapping("/url-imports/{id}/confirm")
    public ResponseEntity<DocumentResponse> confirmUrlImport(
            @PathVariable Long id,
            @RequestBody UrlImportConfirmRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlImportService.confirmImport(id, request, currentUser));
    }

    @Operation(summary = "批量确认导入页面", description = "批量确认抓取到的页面入库为知识库文档")
    @PostMapping("/url-imports/batch-confirm")
    public ResponseEntity<BatchUrlImportConfirmResponse> batchConfirmUrlImports(
            @RequestBody BatchUrlImportConfirmRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlImportService.batchConfirmImports(request, currentUser));
    }

    @Operation(summary = "拒绝导入页面", description = "拒绝抓取到的页面，不入库")
    @PostMapping("/url-imports/{id}/reject")
    public ResponseEntity<Void> rejectUrlImport(
            @PathVariable Long id,
            @Valid @RequestBody UrlImportRejectRequest request) {
        urlImportService.rejectImport(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "取消导入任务", description = "取消正在进行的网站导入任务")
    @PostMapping("/url-imports/{id}/cancel")
    public ResponseEntity<Void> cancelUrlImport(@PathVariable Long id) {
        urlImportService.cancelImportJob(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除导入任务", description = "删除网站导入任务及其记录")
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
                .embeddingModel(folder.getEmbeddingModel())
                .vectorTableName(folder.getVectorTableName())
                .rerankModelId(folder.getRerankModelId())
                .rerankModelName(folder.getRerankModelName())
                .initStatus(folder.getInitStatus() != null ? folder.getInitStatus().name() : "READY")
                .initError(folder.getInitError())
                .initializedAt(folder.getInitializedAt())
                .children(List.of())
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

    @Operation(summary = "删除导入条目", description = "删除单个抓取页面条目记录")
    @DeleteMapping("/url-import-items/{id}")
    public ResponseEntity<Void> deleteUrlImportItem(@PathVariable Long id) {
        urlImportService.deleteImportItem(id);
        return ResponseEntity.noContent().build();
    }
}
