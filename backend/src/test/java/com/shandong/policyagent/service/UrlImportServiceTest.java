package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.UrlImportItem;
import com.shandong.policyagent.entity.UrlImportJob;
import com.shandong.policyagent.entity.UrlImportJobStatus;
import com.shandong.policyagent.entity.UrlImportParseStatus;
import com.shandong.policyagent.entity.UrlImportReviewStatus;
import com.shandong.policyagent.entity.UrlImportItemType;
import com.shandong.policyagent.repository.KnowledgeDocumentSourceRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import com.shandong.policyagent.repository.UrlImportItemRepository;
import com.shandong.policyagent.repository.UrlImportJobRepository;
import com.shandong.policyagent.rag.KnowledgeService;
import com.shandong.policyagent.rag.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlImportServiceTest {

    @Mock
    private UrlImportJobRepository jobRepository;

    @Mock
    private UrlImportItemRepository itemRepository;

    @Mock
    private KnowledgeFolderRepository folderRepository;

    @Mock
    private KnowledgeDocumentSourceRepository knowledgeDocumentSourceRepository;

    @Mock
    private KnowledgeImportBridgeService knowledgeImportBridgeService;

    @Mock
    private CandidateContentService candidateContentService;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private StorageService storageService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private UrlImportService urlImportService;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        urlImportService = new UrlImportService(
                jobRepository,
                itemRepository,
                folderRepository,
                knowledgeDocumentSourceRepository,
                knowledgeImportBridgeService,
                candidateContentService,
                knowledgeService,
                storageService,
                List.of(),
                jdbcTemplate,
                directExecutor
        );
    }

    @Test
    void shouldSoftDeleteCompletedImportJob() {
        UrlImportJob job = UrlImportJob.builder()
                .id(11L)
                .status(UrlImportJobStatus.COMPLETED)
                .importedCount(2)
                .candidateCount(2)
                .rejectedCount(0)
                .build();
        when(jobRepository.findById(11L)).thenReturn(Optional.of(job));

        urlImportService.deleteImportJob(11L);

        assertNotNull(job.getDeletedAt());
        verify(jobRepository).save(job);
    }

    @Test
    void shouldDeletePendingImportItemAndRefreshJobStatus() {
        UrlImportJob job = UrlImportJob.builder()
                .id(21L)
                .status(UrlImportJobStatus.WAITING_CONFIRM)
                .candidateCount(2)
                .importedCount(0)
                .rejectedCount(0)
                .build();
        UrlImportItem item = UrlImportItem.builder()
                .id(31L)
                .job(job)
                .itemType(UrlImportItemType.ARTICLE)
                .parseStatus(UrlImportParseStatus.PARSED)
                .reviewStatus(UrlImportReviewStatus.WAITING_CONFIRM)
                .build();
        when(itemRepository.findById(31L)).thenReturn(Optional.of(item));
        when(itemRepository.countByJobIdAndReviewStatus(21L, UrlImportReviewStatus.WAITING_CONFIRM)).thenReturn(0L);
        when(itemRepository.countByJobIdAndReviewStatus(21L, UrlImportReviewStatus.IMPORT_FAILED)).thenReturn(0L);

        urlImportService.deleteImportItem(31L);

        assertEquals(1, job.getCandidateCount());
        assertEquals(UrlImportJobStatus.COMPLETED, job.getStatus());
        verify(itemRepository).delete(item);
        verify(jobRepository).save(job);
    }

    @Test
    void shouldRejectDeletingConfirmedImportItem() {
        UrlImportJob job = UrlImportJob.builder()
                .id(41L)
                .status(UrlImportJobStatus.PARTIALLY_IMPORTED)
                .candidateCount(1)
                .importedCount(1)
                .build();
        UrlImportItem item = UrlImportItem.builder()
                .id(51L)
                .job(job)
                .itemType(UrlImportItemType.ARTICLE)
                .parseStatus(UrlImportParseStatus.PARSED)
                .reviewStatus(UrlImportReviewStatus.CONFIRMED)
                .build();
        when(itemRepository.findById(51L)).thenReturn(Optional.of(item));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> urlImportService.deleteImportItem(51L));

        assertEquals("已入库内容请在知识库列表中删除对应文档", error.getMessage());
        verify(itemRepository, never()).delete(any());
    }
}