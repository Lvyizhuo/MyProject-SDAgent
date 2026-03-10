package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.UrlImportItem;
import com.shandong.policyagent.entity.UrlImportReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlImportItemRepository extends JpaRepository<UrlImportItem, Long> {

    List<UrlImportItem> findTop200ByReviewStatusOrderByCreatedAtDesc(UrlImportReviewStatus reviewStatus);

    List<UrlImportItem> findByJobIdOrderByQualityScoreDescCreatedAtDesc(Long jobId);

    Optional<UrlImportItem> findFirstBySourceUrlOrderByCreatedAtDesc(String sourceUrl);

    Optional<UrlImportItem> findFirstByContentHashAndReviewStatusInOrderByCreatedAtDesc(
            String contentHash,
            Collection<UrlImportReviewStatus> statuses
    );

    long countByJobIdAndReviewStatus(Long jobId, UrlImportReviewStatus reviewStatus);

    List<UrlImportItem> findAllByIdInOrderByCreatedAtAsc(List<Long> ids);
}