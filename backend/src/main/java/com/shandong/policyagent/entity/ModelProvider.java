package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型服务商配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "model_provider")
public class ModelProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模型名称（显示用）
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 模型类型
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ModelType type;

    /**
     * 服务商标识
     */
    @Column(nullable = false, length = 50)
    private String provider;

    /**
     * API 地址
     */
    @Column(name = "api_url", nullable = false, length = 500)
    private String apiUrl;

    /**
     * API 密钥（加密存储）
     */
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    /**
     * 具体模型名称
     */
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /**
     * 温度参数
     */
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = new BigDecimal("0.7");

    /**
     * 最大Token数
     */
    @Column(name = "max_tokens")
    private Integer maxTokens;

    /**
     * TopP 采样参数
     */
    @Column(precision = 5, scale = 4)
    private BigDecimal topP;

    /**
     * 是否为默认模型
     */
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * 是否启用
     */
    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    @Column(name = "created_by")
    private Long createdBy;

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
}