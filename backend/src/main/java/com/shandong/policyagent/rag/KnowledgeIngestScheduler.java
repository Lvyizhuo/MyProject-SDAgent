package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.properties.KnowledgeIngestProperties;
import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestScheduler {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeService knowledgeService;
    private final KnowledgeIngestProperties ingestProperties;
    @Qualifier("knowledgeIngestTaskExecutor")
    private final Executor knowledgeIngestTaskExecutor;

    private Semaphore concurrencyLimiter;
    private boolean rateLimitEnabled;
    private long availableTokens;
    private long lastRefillNanos;
    private long refillIntervalNanos;
    // PROCESSING 超过该时长未更新即视为悬挂（JVM 重启/嵌入调用卡死），回退为 PENDING 重新排队
    private static final Duration STALE_PROCESSING_THRESHOLD = Duration.ofHours(1);

    @PostConstruct
    void init() {
        int maxConcurrent = Math.max(ingestProperties.getMaxConcurrent(), 1);
        concurrencyLimiter = new Semaphore(maxConcurrent);

        int maxPerMinute = Math.max(ingestProperties.getMaxPerMinute(), 0);
        rateLimitEnabled = maxPerMinute > 0;
        if (rateLimitEnabled) {
            availableTokens = maxPerMinute;
            refillIntervalNanos = TimeUnit.MINUTES.toNanos(1) / maxPerMinute;
            lastRefillNanos = System.nanoTime();
        }

        resetLeftoverProcessingDocuments();
    }

    @Scheduled(fixedDelayString = "${app.knowledge.ingest.poll-interval-ms:2000}")
    public void pollPendingDocuments() {
        if (!ingestProperties.isSchedulerEnabled()) {
            return;
        }

        requeueStaleProcessingDocuments();

        int availablePermits = concurrencyLimiter.availablePermits();
        if (availablePermits <= 0) {
            return;
        }

        int batchSize = Math.min(ingestProperties.getBatchSize(), availablePermits);
        if (batchSize <= 0) {
            return;
        }

        List<Long> pendingIds = documentRepository.findIdsByStatus(
                DocumentStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        if (pendingIds.isEmpty()) {
            return;
        }

        for (Long documentId : pendingIds) {
            if (!concurrencyLimiter.tryAcquire()) {
                break;
            }

            if (!claimDocument(documentId)) {
                concurrencyLimiter.release();
                continue;
            }

            if (!tryAcquireRateToken()) {
                revertToPending(documentId);
                concurrencyLimiter.release();
                break;
            }

            submitProcessingTask(documentId);
        }
    }

    private void submitProcessingTask(Long documentId) {
        try {
            knowledgeIngestTaskExecutor.execute(() -> {
                try {
                    knowledgeService.processDocumentAsync(documentId);
                } finally {
                    concurrencyLimiter.release();
                }
            });
        } catch (RuntimeException ex) {
            log.warn("知识库入库任务提交失败，将回退为待处理: documentId={}", documentId, ex);
            revertToPending(documentId);
            concurrencyLimiter.release();
        }
    }

    @Transactional
    protected boolean claimDocument(Long documentId) {
        return documentRepository.updateStatusIfMatch(
                documentId,
                DocumentStatus.PENDING,
                DocumentStatus.PROCESSING
        ) > 0;
    }

    @Transactional
    protected void revertToPending(Long documentId) {
        documentRepository.updateStatusIfMatch(
                documentId,
                DocumentStatus.PROCESSING,
                DocumentStatus.PENDING
        );
    }

    // why: 启动时遗留的 PROCESSING 文档无人认领会永久卡死并占满并发额度，先重置为 PENDING
    private void resetLeftoverProcessingDocuments() {
        try {
            int resetCount = documentRepository.updateAllStatusIfMatch(
                    DocumentStatus.PROCESSING,
                    DocumentStatus.PENDING
            );
            if (resetCount > 0) {
                log.warn("启动时重置遗留的 PROCESSING 文档为 PENDING: count={}", resetCount);
            }
        } catch (Exception exception) {
            // why: 启动期 DB 未就绪不应阻断应用启动，后续由定时任务的超时回退机制兜底
            log.error("启动时重置遗留 PROCESSING 文档失败，将依赖定时任务超时回退", exception);
        }
    }

    // why: 进程崩溃/嵌入悬挂后 updatedAt 不再刷新，超时回退 PENDING 让定时任务重新认领
    private void requeueStaleProcessingDocuments() {
        try {
            int requeueCount = documentRepository.requeueStaleProcessingBefore(
                    DocumentStatus.PROCESSING,
                    DocumentStatus.PENDING,
                    LocalDateTime.now().minus(STALE_PROCESSING_THRESHOLD)
            );
            if (requeueCount > 0) {
                log.warn("已将 {} 内未更新的 PROCESSING 文档回退为 PENDING: count={}",
                        STALE_PROCESSING_THRESHOLD, requeueCount);
            }
        } catch (Exception exception) {
            log.error("回退超时 PROCESSING 文档失败", exception);
        }
    }

    private boolean tryAcquireRateToken() {
        if (!rateLimitEnabled) {
            return true;
        }

        refillTokens();
        if (availableTokens <= 0) {
            return false;
        }

        availableTokens -= 1;
        return true;
    }

    private void refillTokens() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }

        long tokensToAdd = elapsed / refillIntervalNanos;
        if (tokensToAdd <= 0) {
            return;
        }

        int maxPerMinute = Math.max(ingestProperties.getMaxPerMinute(), 0);
        availableTokens = Math.min(maxPerMinute, availableTokens + tokensToAdd);
        lastRefillNanos += tokensToAdd * refillIntervalNanos;
    }
}
