package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.UrlImportJob;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlImportJobRepository extends JpaRepository<UrlImportJob, Long> {

    // why: 列表页会读取 targetFolder 的 id/path，预先抓取避免逐条懒加载
    @EntityGraph(attributePaths = "targetFolder")
    List<UrlImportJob> findTop20ByDeletedAtIsNullOrderByCreatedAtDesc();

    boolean existsByTargetFolderIdAndDeletedAtIsNull(Long folderId);

    @Modifying
    @Transactional
    @Query("update UrlImportJob job set job.targetFolder = null where job.targetFolder.id = :folderId")
    int clearTargetFolderReferences(@Param("folderId") Long folderId);
}
