package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.KnowledgeDocumentSource;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeDocumentSourceRepository extends JpaRepository<KnowledgeDocumentSource, Long> {

    boolean existsBySourceUrl(String sourceUrl);

    @EntityGraph(attributePaths = {"knowledgeDocument", "importItem", "importItem.job"})
    Optional<KnowledgeDocumentSource> findFirstBySourceUrlOrderByCreatedAtDesc(String sourceUrl);

    @EntityGraph(attributePaths = {"knowledgeDocument", "importItem", "importItem.job"})
    List<KnowledgeDocumentSource> findByKnowledgeDocumentIdIn(Collection<Long> knowledgeDocumentIds);

    @Modifying
    @Transactional
    void deleteByKnowledgeDocumentIdIn(Collection<Long> knowledgeDocumentIds);
}