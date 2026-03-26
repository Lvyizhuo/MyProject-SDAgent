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
import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

        @Query("""
                        SELECT d FROM KnowledgeDocument d
                        WHERE (:folderId IS NULL OR d.folder.id = :folderId)
                            AND (:status IS NULL OR d.status = :status)
                            AND (
                                        LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(COALESCE(d.source, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(COALESCE(d.summary, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            )
                        """)
        Page<KnowledgeDocument> searchDocuments(@Param("folderId") Long folderId,
                                                                                        @Param("status") DocumentStatus status,
                                                                                        @Param("keyword") String keyword,
                                                                                        Pageable pageable);

        @Query("""
                                                SELECT d FROM KnowledgeDocument d
                                                WHERE EXISTS (
                                                        SELECT 1 FROM KnowledgeDocumentSource s
                                                        WHERE s.knowledgeDocument = d
                                                            AND s.importItem.job.id = :importJobId
                                                )
                                                    AND (:status IS NULL OR d.status = :status)
                                                    AND (
                                                                        LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                                         OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                                         OR LOWER(COALESCE(d.source, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                                         OR LOWER(COALESCE(d.summary, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                                    )
                                                """)
        Page<KnowledgeDocument> searchDocumentsByImportJobId(@Param("importJobId") Long importJobId,
                                                                                                                 @Param("status") DocumentStatus status,
                                                                                                                 @Param("keyword") String keyword,
                                                                                                                 Pageable pageable);

    Page<KnowledgeDocument> findByFolderId(Long folderId, Pageable pageable);

    boolean existsByFolderId(Long folderId);

        @Query("""
                        SELECT d FROM KnowledgeDocument d
                        WHERE EXISTS (
                                SELECT 1 FROM KnowledgeDocumentSource s
                                WHERE s.knowledgeDocument = d
                                    AND s.importItem.job.id = :importJobId
                        )
                            AND (:status IS NULL OR d.status = :status)
                        """)
        Page<KnowledgeDocument> findByImportJobId(@Param("importJobId") Long importJobId,
                                                                                            @Param("status") DocumentStatus status,
                                                                                            Pageable pageable);

    Page<KnowledgeDocument> findByStatus(DocumentStatus status, Pageable pageable);

    Page<KnowledgeDocument> findByCategory(String category, Pageable pageable);

    @Query("SELECT d FROM KnowledgeDocument d WHERE d.embeddingModel = :embeddingModel")
    List<KnowledgeDocument> findByEmbeddingModel(@Param("embeddingModel") String embeddingModel);

        @Query("""
                        SELECT d.id FROM KnowledgeDocument d
                        WHERE (:folderId IS NULL OR d.folder.id = :folderId)
                            AND (:status IS NULL OR d.status = :status)
                        ORDER BY d.createdAt DESC
                        """)
        List<Long> findIdsBySelection(@Param("folderId") Long folderId,
                                                                    @Param("status") DocumentStatus status);

        @Query("""
                        SELECT d.id FROM KnowledgeDocument d
                        WHERE EXISTS (
                                SELECT 1 FROM KnowledgeDocumentSource s
                                WHERE s.knowledgeDocument = d
                                    AND s.importItem.job.id = :importJobId
                        )
                            AND (:status IS NULL OR d.status = :status)
                        ORDER BY d.createdAt DESC
                        """)
        List<Long> findIdsByImportJobId(@Param("importJobId") Long importJobId,
                                                                        @Param("status") DocumentStatus status);

        @Query("""
                        SELECT d.id FROM KnowledgeDocument d
                        WHERE (:folderId IS NULL OR d.folder.id = :folderId)
                            AND (:status IS NULL OR d.status = :status)
                            AND (
                                        LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                        OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                        OR LOWER(COALESCE(d.source, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                        OR LOWER(COALESCE(d.summary, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            )
                        ORDER BY d.createdAt DESC
                        """)
        List<Long> searchIdsBySelection(@Param("folderId") Long folderId,
                                                                        @Param("status") DocumentStatus status,
                                                                        @Param("keyword") String keyword);

        @Query("""
                        SELECT d.id FROM KnowledgeDocument d
                        WHERE EXISTS (
                                SELECT 1 FROM KnowledgeDocumentSource s
                                WHERE s.knowledgeDocument = d
                                    AND s.importItem.job.id = :importJobId
                        )
                            AND (:status IS NULL OR d.status = :status)
                            AND (
                                                LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(COALESCE(d.source, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                 OR LOWER(COALESCE(d.summary, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            )
                        ORDER BY d.createdAt DESC
                        """)
        List<Long> searchIdsByImportJobId(@Param("importJobId") Long importJobId,
                                                                            @Param("status") DocumentStatus status,
                                                                            @Param("keyword") String keyword);

    @Query(value = "SELECT * FROM knowledge_documents WHERE ?1 = ANY(tags)",
           nativeQuery = true)
    Page<KnowledgeDocument> findByTag(@Param("tag") String tag, Pageable pageable);

    @Query("""
            select d
            from KnowledgeDocument d
            left join d.folder f
            where (
                    (:folderPath = '/' and d.folder is null)
                    or f.path = :folderPath
            )
                and d.title = :title
                and d.fileName = :fileName
            order by d.createdAt desc
            """)
    Optional<KnowledgeDocument> findFirstByFolderPathAndTitleAndFileName(@Param("folderPath") String folderPath,
                                                                         @Param("title") String title,
                                                                         @Param("fileName") String fileName);
}
