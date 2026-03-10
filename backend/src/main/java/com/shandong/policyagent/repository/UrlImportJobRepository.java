package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.UrlImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlImportJobRepository extends JpaRepository<UrlImportJob, Long> {

    List<UrlImportJob> findTop20ByOrderByCreatedAtDesc();
}