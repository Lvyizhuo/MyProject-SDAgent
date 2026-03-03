package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Page<KnowledgeDocument> findByFolderId(Long folderId, Pageable pageable);

    Page<KnowledgeDocument> findByStatus(DocumentStatus status, Pageable pageable);

    Page<KnowledgeDocument> findByCategory(String category, Pageable pageable);

    @Query("SELECT d FROM KnowledgeDocument d WHERE d.embeddingModel = :embeddingModel")
    List<KnowledgeDocument> findByEmbeddingModel(@Param("embeddingModel") String embeddingModel);

    @Query(value = "SELECT * FROM knowledge_documents WHERE ?1 = ANY(tags)",
           nativeQuery = true)
    Page<KnowledgeDocument> findByTag(@Param("tag") String tag, Pageable pageable);
}
