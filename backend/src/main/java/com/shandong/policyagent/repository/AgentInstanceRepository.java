package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.AgentInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AgentInstanceRepository extends JpaRepository<AgentInstance, Long> {

    Optional<AgentInstance> findByCode(String code);

    List<AgentInstance> findAllByOrderByIsDefaultDescIdAsc();

    boolean existsByIsDefaultTrue();

    @Query("select count(a) from AgentInstance a where a.status = 'published'")
    long countPublished();
}
