package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.KnowledgeFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeFolderRepository extends JpaRepository<KnowledgeFolder, Long> {

    List<KnowledgeFolder> findByParentIsNullOrderBySortOrderAsc();

    Optional<KnowledgeFolder> findByPath(String path);

    @Query("SELECT f FROM KnowledgeFolder f LEFT JOIN FETCH f.children WHERE f.parent IS NULL ORDER BY f.sortOrder ASC")
    List<KnowledgeFolder> findAllRootFoldersWithChildren();
}
