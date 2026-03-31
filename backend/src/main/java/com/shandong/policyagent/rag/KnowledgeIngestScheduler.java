package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.KnowledgeIngestProperties;
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
    }

    @Scheduled(fixedDelayString = "${app.knowledge.ingest.poll-interval-ms:2000}")
    public void pollPendingDocuments() {
        if (!ingestProperties.isSchedulerEnabled()) {
            return;
        }

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
