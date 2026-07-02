package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.ToolDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {

    Optional<ToolDefinition> findByCode(String code);

    List<ToolDefinition> findAllByOrderByIdAsc();
}
