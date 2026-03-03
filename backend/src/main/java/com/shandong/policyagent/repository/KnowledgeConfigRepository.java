package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.KnowledgeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeConfigRepository extends JpaRepository<KnowledgeConfig, Long> {
}
