package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.AgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {

    /**
     * 获取第一个（也是唯一一个）agent_config 记录
     */
    Optional<AgentConfig> findFirstByOrderByIdAsc();
}
