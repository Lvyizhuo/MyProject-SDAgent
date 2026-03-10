package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.KnowledgeDocumentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeDocumentSourceRepository extends JpaRepository<KnowledgeDocumentSource, Long> {

    boolean existsBySourceUrl(String sourceUrl);
}