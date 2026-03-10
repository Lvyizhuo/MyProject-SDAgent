package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.*;
import com.shandong.policyagent.ingestion.crawler.CrawledAttachment;
import com.shandong.policyagent.ingestion.crawler.CrawledPage;
import com.shandong.policyagent.ingestion.crawler.SiteCrawlerAdapter;
import com.shandong.policyagent.model.dto.*;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.repository.UrlImportItemRepository;
import com.shandong.policyagent.repository.UrlImportJobRepository;
import com.shandong.policyagent.rag.KnowledgeService;
import com.shandong.policyagent.rag.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlImportService {

    private final UrlImportJobRepository jobRepository;
    private final UrlImportItemRepository itemRepository;
    private final KnowledgeFolderRepository folderRepository;
    private final KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;
    private final KnowledgeImportBridgeService knowledgeImportBridgeService;
    private final CandidateContentService candidateContentService;
    private final KnowledgeService knowledgeService;
    private final StorageService storageService;
    private final List<SiteCrawlerAdapter> crawlerAdapters;
    private final JdbcTemplate jdbcTemplate;
    @Qualifier("urlImportTaskExecutor")
    private final Executor urlImportTaskExecutor;

    @Transactional
    public UrlImportJobResponse createImport(UrlImportCreateRequest request, User currentUser) {
        validateSourceUrl(request.getUrl());
        KnowledgeFolder targetFolder = null;
        if (request.getFolderId() != null) {
            targetFolder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new IllegalArgumentException("目标文件夹不存在"));
        }

        String embeddingModel = request.getEmbeddingModel();
        if (embeddingModel == null || embeddingModel.isBlank()) {
            embeddingModel = knowledgeService.getConfig().getDefaultEmbeddingModel();
        }

        UrlImportJob job = jobRepository.save(UrlImportJob.builder()
                .sourceUrl(request.getUrl().trim())
                .sourceSite("山东省商务厅")
                .targetFolder(targetFolder)
                .embeddingModel(embeddingModel)
                .titleOverride(request.getTitleOverride())
                .remark(request.getRemark())
                .status(UrlImportJobStatus.PENDING)
                .createdBy(currentUser)
                .build());

        Long jobId = job.getId();
            log.info("创建网站导入任务: jobId={} | sourceUrl={} | folderId={} | embeddingModel={}",
                jobId,
                job.getSourceUrl(),
                targetFolder != null ? targetFolder.getId() : null,
                embeddingModel);
            scheduleProcessJob(jobId);
        return toJobResponse(job);
    }

    @Transactional
    public void cancelImportJob(Long jobId) {
        ensureStatusConstraintAllowsCanceled();

        UrlImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("导入任务不存在"));

        if (job.getStatus() == UrlImportJobStatus.CANCELED) {
            return;
        }
        if (job.getStatus() == UrlImportJobStatus.COMPLETED || job.getStatus() == UrlImportJobStatus.PARTIALLY_IMPORTED) {
            throw new IllegalArgumentException("任务已结束，请直接删除任务记录");
        }

        job.setStatus(UrlImportJobStatus.CANCELED);
        job.setErrorMessage("管理员已取消任务");
        jobRepository.save(job);
        log.info("网站导入任务已取消: jobId={}", jobId);
    }

    @Transactional
    public void deleteImportJob(Long jobId) {
        UrlImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("导入任务不存在"));

        if (job.getStatus() == UrlImportJobStatus.CRAWLING || job.getStatus() == UrlImportJobStatus.PROCESSING) {
            throw new IllegalArgumentException("任务仍在执行中，请先取消后再删除");
        }
        if (job.getImportedCount() != null && job.getImportedCount() > 0) {
            throw new IllegalArgumentException("任务下已有内容入库，不能直接删除任务记录");
        }

        jobRepository.delete(job);
        log.info("网站导入任务已删除: jobId={}", jobId);
    }

    public UrlImportListResponse listImports() {
        List<UrlImportJobResponse> jobs = jobRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .sorted(Comparator.comparing(UrlImportJob::getCreatedAt).reversed())
                .map(this::toJobResponse)
                .toList();
        List<UrlImportItemResponse> candidates = itemRepository
            .findTop200ByReviewStatusOrderByCreatedAtDesc(UrlImportReviewStatus.WAITING_CONFIRM)
                .stream()
                .map(item -> toItemResponse(item, false))
                .toList();
        return UrlImportListResponse.builder()
                .jobs(jobs)
                .candidates(candidates)
                .build();
    }

    public UrlImportItemResponse getImportItem(Long itemId) {
        UrlImportItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("待入库内容不存在"));
        return toItemResponse(item, true);
    }

    @Transactional
    public DocumentResponse confirmImport(Long itemId, UrlImportConfirmRequest request, User currentUser) {
        UrlImportItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("待入库内容不存在"));
        KnowledgeDocument document = confirmImportInternal(item, request, currentUser);
        return toDocumentResponse(document);
    }

    @Transactional
    public BatchUrlImportConfirmResponse batchConfirmImports(BatchUrlImportConfirmRequest request, User currentUser) {
        List<UrlImportItem> items = resolveBatchConfirmItems(request);
        List<Long> importedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (UrlImportItem item : items) {
            try {
                UrlImportConfirmRequest confirmRequest = UrlImportConfirmRequest.builder()
                        .folderId(request.getFolderId())
                        .publishDate(item.getPublishDate())
                        .source(item.getSourceSite())
                        .build();
                KnowledgeDocument document = confirmImportInternal(item, confirmRequest, currentUser);
                importedIds.add(document.getId());
            } catch (Exception e) {
                errors.add("itemId=" + item.getId() + ": " + e.getMessage());
            }
        }

        return BatchUrlImportConfirmResponse.builder()
                .requestedCount(items.size())
                .successCount(importedIds.size())
                .failedCount(errors.size())
                .importedIds(importedIds)
                .errors(errors)
                .build();
    }

    @Transactional
    public void rejectImport(Long itemId, UrlImportRejectRequest request) {
        UrlImportItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("待入库内容不存在"));
        item.setReviewStatus(UrlImportReviewStatus.REJECTED);
        item.setReviewComment(request.getReason().trim());
        itemRepository.save(item);

        UrlImportJob job = item.getJob();
        job.setRejectedCount(job.getRejectedCount() + 1);
        updateJobStatusAfterReview(job);
    }

    private KnowledgeDocument confirmImportInternal(UrlImportItem item, UrlImportConfirmRequest request, User currentUser) {
        if (item.getReviewStatus() != UrlImportReviewStatus.WAITING_CONFIRM && item.getReviewStatus() != UrlImportReviewStatus.IMPORT_FAILED) {
            throw new IllegalArgumentException("当前内容不处于可确认入库状态");
        }

        try {
            KnowledgeDocument document = knowledgeImportBridgeService.importCandidate(item, request, currentUser);
            item.setKnowledgeDocument(document);
            item.setReviewStatus(UrlImportReviewStatus.CONFIRMED);
            item.setReviewComment("已确认入库");
            item.setUpdatedAt(LocalDateTime.now());
            itemRepository.save(item);

            UrlImportJob job = item.getJob();
            job.setImportedCount(job.getImportedCount() + 1);
            updateJobStatusAfterReview(job);
            return document;
        } catch (Exception e) {
            item.setReviewStatus(UrlImportReviewStatus.IMPORT_FAILED);
            item.setReviewComment(e.getMessage());
            itemRepository.save(item);
            updateJobStatusAfterReview(item.getJob());
            throw e;
        }
    }

    private List<UrlImportItem> resolveBatchConfirmItems(BatchUrlImportConfirmRequest request) {
        List<Long> requestedIds = request != null ? request.getIds() : null;
        if (requestedIds == null || requestedIds.isEmpty()) {
            return itemRepository.findTop200ByReviewStatusOrderByCreatedAtDesc(UrlImportReviewStatus.WAITING_CONFIRM);
        }

        List<UrlImportItem> items = itemRepository.findAllByIdInOrderByCreatedAtAsc(requestedIds);
        if (items.size() != requestedIds.stream().filter(Objects::nonNull).distinct().count()) {
            throw new IllegalArgumentException("部分待入库内容不存在，无法批量确认");
        }
        return items;
    }

    private void updateJobStatusAfterReview(UrlImportJob job) {
        long waitingCount = itemRepository.countByJobIdAndReviewStatus(job.getId(), UrlImportReviewStatus.WAITING_CONFIRM);
        long failedCount = itemRepository.countByJobIdAndReviewStatus(job.getId(), UrlImportReviewStatus.IMPORT_FAILED);

        if (waitingCount == 0 && failedCount == 0) {
            job.setStatus(UrlImportJobStatus.COMPLETED);
        } else if (job.getImportedCount() != null && job.getImportedCount() > 0) {
            job.setStatus(UrlImportJobStatus.PARTIALLY_IMPORTED);
        } else {
            job.setStatus(UrlImportJobStatus.WAITING_CONFIRM);
        }
        jobRepository.save(job);
    }

    private DocumentResponse toDocumentResponse(KnowledgeDocument document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .folderId(document.getFolder() != null ? document.getFolder().getId() : null)
                .folderPath(document.getFolder() != null ? document.getFolder().getPath() : "/")
                .title(document.getTitle())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .fileType(document.getFileType())
                .embeddingModel(document.getEmbeddingModel())
                .category(document.getCategory())
                .tags(document.getTags())
                .publishDate(document.getPublishDate())
                .source(document.getSource())
                .validFrom(document.getValidFrom())
                .validTo(document.getValidTo())
                .summary(document.getSummary())
                .status(document.getStatus())
                .errorMessage(document.getErrorMessage())
                .chunkCount(document.getChunkCount())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private void processJob(Long jobId) {
        UrlImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("导入任务不存在"));
        try {
            if (isJobCanceled(jobId)) {
                log.info("网站导入任务在启动前已取消: jobId={}", jobId);
                return;
            }
            log.info("网站导入任务开始执行: jobId={} | sourceUrl={}", jobId, job.getSourceUrl());
            job.setStatus(UrlImportJobStatus.CRAWLING);
            jobRepository.save(job);

            SiteCrawlerAdapter adapter = crawlerAdapters.stream()
                    .filter(candidate -> candidate.supports(job.getSourceUrl()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("当前仅支持山东省商务厅公开栏目链接导入"));

            log.info("网站导入任务匹配站点适配器: jobId={} | adapter={}", jobId, adapter.getClass().getSimpleName());

            List<CrawledPage> crawledPages = adapter.crawl(job.getSourceUrl());
            if (isJobCanceled(jobId)) {
                log.info("网站导入任务在抓取完成后被取消: jobId={}", jobId);
                return;
            }
            log.info("网站导入任务抓取完成: jobId={} | discoveredCount={}", jobId, crawledPages.size());
            job.setDiscoveredCount(crawledPages.size());
            job.setStatus(UrlImportJobStatus.PROCESSING);
            jobRepository.save(job);

            int candidateCount = 0;
            for (CrawledPage page : crawledPages) {
                if (isJobCanceled(jobId)) {
                    log.info("网站导入任务在处理候选阶段被取消: jobId={} | processedCandidates={}", jobId, candidateCount);
                    return;
                }
                log.info("处理抓取结果: jobId={} | itemType={} | sourceUrl={}", jobId, page.itemType(), page.sourceUrl());
                CandidateContentService.CandidateEvaluation evaluation = candidateContentService.evaluate(page);
                if (!Boolean.TRUE.equals(evaluation.shouldKeep())) {
                    log.info("抓取结果被过滤: jobId={} | sourceUrl={} | score={} | comment={}",
                            jobId,
                            page.sourceUrl(),
                            evaluation.qualityScore(),
                            evaluation.reviewComment());
                    continue;
                }

                boolean suspectedDuplicate = isDuplicate(page.sourceUrl(), evaluation.contentHash());
                String rawObjectPath = storeRawPage(job, page);
                String cleanedTextObjectPath = storageService.storeText(
                        evaluation.cleanedText(),
                        buildJobFolder(job.getId()),
                        safeFileName(page.title(), ".txt")
                );

                UrlImportItem item = UrlImportItem.builder()
                        .job(job)
                        .sourceUrl(page.sourceUrl())
                        .sourcePage(page.sourcePage())
                        .sourceSite(page.sourceSite())
                        .itemType(page.itemType())
                        .sourceTitle(page.title())
                        .publishDate(page.publishDate())
                        .rawObjectPath(rawObjectPath)
                        .cleanedTextObjectPath(cleanedTextObjectPath)
                        .cleanedText(evaluation.cleanedText())
                        .contentHash(evaluation.contentHash())
                        .qualityScore(evaluation.qualityScore())
                        .parseStatus(UrlImportParseStatus.PARSED)
                        .reviewStatus(UrlImportReviewStatus.WAITING_CONFIRM)
                        .reviewComment(suspectedDuplicate ? "检测到疑似重复内容，请人工确认" : "")
                        .suspectedDuplicate(suspectedDuplicate)
                        .category(evaluation.category())
                        .tags(evaluation.tags())
                        .summary(evaluation.summary())
                        .attachments(new ArrayList<>())
                        .build();

                for (CrawledAttachment attachment : page.attachments()) {
                    String attachmentStoragePath = attachment.bytes() != null
                            ? storageService.storeBytes(
                                    attachment.bytes(),
                                    buildJobFolder(job.getId()),
                                    attachment.fileName(),
                                    attachment.fileType()
                            )
                            : null;
                    item.getAttachments().add(UrlImportAttachment.builder()
                            .item(item)
                            .attachmentUrl(attachment.attachmentUrl())
                            .fileName(attachment.fileName())
                            .fileType(attachment.fileType())
                            .storagePath(attachmentStoragePath)
                            .parseStatus(attachment.parsedText() != null && !attachment.parsedText().isBlank()
                                    ? UrlImportParseStatus.PARSED
                                    : UrlImportParseStatus.FAILED)
                            .ocrUsed(false)
                            .build());
                }
                itemRepository.save(item);
                candidateCount++;
                log.info("待入库候选已生成: jobId={} | itemId={} | sourceUrl={} | score={} | duplicate={}",
                        jobId,
                        item.getId(),
                        item.getSourceUrl(),
                        item.getQualityScore(),
                        item.getSuspectedDuplicate());
            }

            if (isJobCanceled(jobId)) {
                log.info("网站导入任务在结束前被取消: jobId={}", jobId);
                return;
            }
            job.setCandidateCount(candidateCount);
            job.setStatus(candidateCount > 0 ? UrlImportJobStatus.WAITING_CONFIRM : UrlImportJobStatus.COMPLETED);
            jobRepository.save(job);
            log.info("网站导入任务结束: jobId={} | status={} | candidateCount={}", jobId, job.getStatus(), candidateCount);
        } catch (Exception e) {
            log.error("处理网站导入任务失败: {}", jobId, e);
            job.setStatus(UrlImportJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        }
    }

    private void scheduleProcessJob(Long jobId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> processJob(jobId), urlImportTaskExecutor);
                }
            });
            return;
        }
        CompletableFuture.runAsync(() -> processJob(jobId), urlImportTaskExecutor);
    }

    private void ensureStatusConstraintAllowsCanceled() {
        String definition = jdbcTemplate.query(
                """
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = 'url_import_jobs'::regclass
                  and conname = 'url_import_jobs_status_check'
                """,
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (definition == null || definition.contains("CANCELED")) {
            return;
        }

        log.warn("检测到旧版导入任务状态约束，开始自动修复: {}", definition);
        jdbcTemplate.execute("alter table url_import_jobs drop constraint if exists url_import_jobs_status_check");
        jdbcTemplate.execute(
                """
                alter table url_import_jobs
                add constraint url_import_jobs_status_check
                check (status in ('PENDING', 'CRAWLING', 'PROCESSING', 'WAITING_CONFIRM', 'PARTIALLY_IMPORTED', 'COMPLETED', 'CANCELED', 'FAILED'))
                """
        );
    }

    private boolean isJobCanceled(Long jobId) {
        return jobRepository.findById(jobId)
                .map(job -> job.getStatus() == UrlImportJobStatus.CANCELED)
                .orElse(true);
    }

    private boolean isDuplicate(String sourceUrl, String contentHash) {
        boolean duplicatedBySource = itemRepository.findFirstBySourceUrlOrderByCreatedAtDesc(sourceUrl).isPresent()
                || knowledgeDocumentSourceRepository.existsBySourceUrl(sourceUrl);
        boolean duplicatedByHash = contentHash != null && !contentHash.isBlank()
                && itemRepository.findFirstByContentHashAndReviewStatusInOrderByCreatedAtDesc(
                        contentHash,
                        List.of(UrlImportReviewStatus.WAITING_CONFIRM, UrlImportReviewStatus.CONFIRMED)
                ).isPresent();
        return duplicatedBySource || duplicatedByHash;
    }

    private String storeRawPage(UrlImportJob job, CrawledPage page) {
        if (page.rawHtml() == null || page.rawHtml().isBlank()) {
            return "";
        }
        return storageService.storeText(page.rawHtml(), buildJobFolder(job.getId()), safeFileName(page.title(), ".html"));
    }

    private String buildJobFolder(Long jobId) {
        return "/url-imports/job-" + jobId;
    }

    private String safeFileName(String title, String extension) {
        String base = (title == null || title.isBlank() ? "imported-document" : title)
                .replaceAll("[\\/:*?\"<>|\\s]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "imported-document";
        }
        return base.substring(0, Math.min(base.length(), 80)) + extension;
    }

    private void validateSourceUrl(String sourceUrl) {
        String normalized = sourceUrl == null ? "" : sourceUrl.trim().toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw new IllegalArgumentException("网站链接必须为 http 或 https");
        }
        if (!normalized.contains("commerce.shandong.gov.cn")) {
            throw new IllegalArgumentException("首期仅支持山东省商务厅指定栏目链接导入");
        }
        if (!normalized.contains("/col/col352659/")) {
            throw new IllegalArgumentException("首期仅支持指定以旧换新栏目链接导入");
        }
    }

    private UrlImportJobResponse toJobResponse(UrlImportJob job) {
        return UrlImportJobResponse.builder()
                .id(job.getId())
                .sourceUrl(job.getSourceUrl())
                .sourceSite(job.getSourceSite())
                .status(job.getStatus())
                .discoveredCount(job.getDiscoveredCount())
                .candidateCount(job.getCandidateCount())
                .importedCount(job.getImportedCount())
                .rejectedCount(job.getRejectedCount())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private UrlImportItemResponse toItemResponse(UrlImportItem item, boolean includeContent) {
        return UrlImportItemResponse.builder()
                .id(item.getId())
                .jobId(item.getJob() != null ? item.getJob().getId() : null)
                .sourceUrl(item.getSourceUrl())
                .sourcePage(item.getSourcePage())
                .sourceSite(item.getSourceSite())
                .itemType(item.getItemType())
                .title(item.getSourceTitle())
                .publishDate(item.getPublishDate())
                .qualityScore(item.getQualityScore())
                .parseStatus(item.getParseStatus())
                .reviewStatus(item.getReviewStatus())
                .suspectedDuplicate(item.getSuspectedDuplicate())
                .category(item.getCategory())
                .tags(item.getTags())
                .summary(item.getSummary())
                .cleanedText(includeContent ? item.getCleanedText() : null)
                .reviewComment(item.getReviewComment())
                .errorMessage(item.getErrorMessage())
                .defaultFolderId(item.getJob() != null && item.getJob().getTargetFolder() != null ? item.getJob().getTargetFolder().getId() : null)
                .defaultFolderPath(item.getJob() != null && item.getJob().getTargetFolder() != null ? item.getJob().getTargetFolder().getPath() : "/")
                .createdAt(item.getCreatedAt())
                .attachments(item.getAttachments().stream()
                        .map(attachment -> UrlImportAttachmentResponse.builder()
                                .id(attachment.getId())
                                .attachmentUrl(attachment.getAttachmentUrl())
                                .fileName(attachment.getFileName())
                                .fileType(attachment.getFileType())
                                .parseStatus(attachment.getParseStatus())
                                .ocrUsed(attachment.getOcrUsed())
                                .build())
                        .toList())
                .build();
    }
}