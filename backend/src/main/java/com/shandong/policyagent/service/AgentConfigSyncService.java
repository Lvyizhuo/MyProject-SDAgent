package com.shandong.policyagent.service;

import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.repository.AgentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 智能体配置同步服务
 *
 * 负责在管理员通过 API 修改配置后，将最新配置同步到运行时内存（DynamicAgentConfigHolder）。
 * 同步成功后无需重启，ChatService 下次请求即使用新配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigSyncService {

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final AgentConfigRepository agentConfigRepository;
    private final AgentConfigBindingSanitizer agentConfigBindingSanitizer;

    /**
     * 将指定的 AgentConfig 同步到运行时。
     * 调用方（AgentConfigController）在数据库保存成功后调用此方法。
     *
     * @param config 已保存到数据库的最新配置
     */
    public void syncConfigToRuntime(AgentConfig config) {
        try {
            AgentConfig sanitizedConfig = agentConfigBindingSanitizer.sanitizeAndPersistIfNeeded(config);
            dynamicAgentConfigHolder.update(sanitizedConfig);
            log.info("配置同步完成 | modelName={} | systemPromptLen={}",
                    sanitizedConfig.getModelName(),
                    sanitizedConfig.getSystemPrompt() != null ? sanitizedConfig.getSystemPrompt().length() : 0);
        } catch (Exception e) {
            log.error("配置同步到运行时失败，将从数据库重新加载: {}", e.getMessage(), e);
            // 兜底：从数据库重新加载最新记录
            reloadFromDatabase();
        }
    }

    /**
     * 从数据库重新加载配置到运行时（兜底逻辑 / 手动触发）
     */
    public void reloadFromDatabase() {
        agentConfigRepository.findFirstByOrderByIdAsc().ifPresentOrElse(
                config -> {
                    AgentConfig sanitizedConfig = agentConfigBindingSanitizer.sanitizeAndPersistIfNeeded(config);
                    dynamicAgentConfigHolder.update(sanitizedConfig);
                    log.info("从数据库重新加载配置成功 | modelName={}", sanitizedConfig.getModelName());
                },
                () -> log.warn("数据库中不存在 agent_config 记录，运行时配置未更新")
        );
    }
}
