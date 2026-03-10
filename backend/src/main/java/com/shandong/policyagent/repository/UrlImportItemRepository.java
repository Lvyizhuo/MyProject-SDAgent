package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.UrlImportItem;
import com.shandong.policyagent.entity.UrlImportReviewStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlImportItemRepository extends JpaRepository<UrlImportItem, Long> {

    List<UrlImportItem> findTop200ByReviewStatusOrderByCreatedAtDesc(UrlImportReviewStatus reviewStatus);

        @Query("""
                        select item
                        from UrlImportItem item
                        join item.job job
                        where item.reviewStatus = :reviewStatus
                            and job.deletedAt is null
                        order by item.createdAt desc
                        """)
        List<UrlImportItem> findTop200VisibleByReviewStatusOrderByCreatedAtDesc(@Param("reviewStatus") UrlImportReviewStatus reviewStatus);

    List<UrlImportItem> findByJobIdOrderByQualityScoreDescCreatedAtDesc(Long jobId);

    Optional<UrlImportItem> findFirstBySourceUrlOrderByCreatedAtDesc(String sourceUrl);

    Optional<UrlImportItem> findFirstByContentHashAndReviewStatusInOrderByCreatedAtDesc(
            String contentHash,
            Collection<UrlImportReviewStatus> statuses
    );

    long countByJobIdAndReviewStatus(Long jobId, UrlImportReviewStatus reviewStatus);

    List<UrlImportItem> findAllByIdInOrderByCreatedAtAsc(List<Long> ids);

    @Modifying
    @Transactional
    @Query("update UrlImportItem item set item.knowledgeDocument = null where item.knowledgeDocument.id in :knowledgeDocumentIds")
    int clearKnowledgeDocumentReferences(@Param("knowledgeDocumentIds") Collection<Long> knowledgeDocumentIds);
}