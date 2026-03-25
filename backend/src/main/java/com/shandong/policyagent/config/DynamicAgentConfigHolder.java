package com.shandong.policyagent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.repository.AgentConfigRepository;
import com.shandong.policyagent.service.AgentConfigBindingSanitizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 动态智能体配置持有器
 *
 * 以 AtomicReference 持有当前运行时生效的 AgentConfig，
 * 启动时从数据库加载，管理员修改配置后通过 AgentConfigSyncService 更新。
 * ChatService 每次请求时从此处读取最新配置（system prompt / model / temperature）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicAgentConfigHolder {

    private final AgentConfigRepository agentConfigRepository;
    private final AgentConfigBindingSanitizer agentConfigBindingSanitizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicReference<AgentConfig> currentConfig = new AtomicReference<>();

    /** 启动时从数据库加载最新配置 */
    @PostConstruct
    public void init() {
        agentConfigRepository.findFirstByOrderByIdAsc().ifPresentOrElse(
                config -> {
                    AgentConfig sanitizedConfig = agentConfigBindingSanitizer.sanitizeAndPersistIfNeeded(config);
                    currentConfig.set(sanitizedConfig);
                    log.info("DynamicAgentConfigHolder 初始化成功 | modelName={} | systemPromptLen={}",
                            sanitizedConfig.getModelName(),
                            sanitizedConfig.getSystemPrompt() != null ? sanitizedConfig.getSystemPrompt().length() : 0);
                },
                () -> log.warn("DynamicAgentConfigHolder 初始化：数据库中尚无 agent_config 记录，等待 AdminInitializer 创建")
        );
    }

    /**
     * 获取当前生效配置（可能为 null，需调用方做 null 检查）
     */
    public AgentConfig get() {
        return currentConfig.get();
    }

    /**
     * 更新运行时配置（线程安全）
     */
    public void update(AgentConfig config) {
        currentConfig.set(config);
        log.info("运行时配置已更新 | modelName={} | systemPromptLen={}",
                config.getModelName(),
                config.getSystemPrompt() != null ? config.getSystemPrompt().length() : 0);
    }

    /**
     * 获取当前 System Prompt，若配置不存在则返回空字符串
     */
    public String getSystemPrompt() {
        AgentConfig config = currentConfig.get();
        if (config == null || config.getSystemPrompt() == null) {
            return "";
        }
        return config.getSystemPrompt();
    }

    /**
     * 获取当前模型名称，若配置不存在则返回默认值
     */
    public String getModelName() {
        AgentConfig config = currentConfig.get();
        if (config == null || config.getModelName() == null) {
            return "qwen3.5-plus";
        }
        return config.getModelName();
    }

    /**
     * 获取当前 temperature，若配置不存在则返回默认值
     */
    public Double getTemperature() {
        AgentConfig config = currentConfig.get();
        if (config == null || config.getTemperature() == null) {
            return 0.7;
        }
        return config.getTemperature();
    }

    /**
     * 获取当前 skills 配置（Map 形式），若解析失败则返回空 Map
     */
    public Map<String, Object> getSkills() {
        AgentConfig config = currentConfig.get();
        if (config == null || config.getSkills() == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(config.getSkills(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析 skills 配置失败，返回空 Map: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
