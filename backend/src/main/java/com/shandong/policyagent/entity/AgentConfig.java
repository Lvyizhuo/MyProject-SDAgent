package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_config")
public class AgentConfig {

    @Id
    private Long id;

    // 基础信息
    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // AI 模型配置
    @Column(nullable = false, length = 50)
    private String modelProvider;

    @Column(length = 255)
    private String apiKey;

    @Column(length = 255)
    private String apiUrl;

    @Column(nullable = false, length = 100)
    private String modelName;

    private Double temperature;

    @Column(name = "llm_model_id")
    private Long llmModelId;

    @Column(name = "vision_model_id")
    private Long visionModelId;

    @Column(name = "audio_model_id")
    private Long audioModelId;

    @Column(name = "embedding_model_id")
    private Long embeddingModelId;

    // 系统提示词
    @Column(nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    // 开场白
    @Column(columnDefinition = "TEXT")
    private String greetingMessage;

    // 技能模块配置
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String skills;

    // MCP 服务器配置
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String mcpServersConfig;

    // 时间戳
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * MCP 服务器配置内嵌类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpServerConfig {
        private String name;
        private Boolean enabled;
        private String url;
        private Integer timeout;
    }
}
