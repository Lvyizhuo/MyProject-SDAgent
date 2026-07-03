package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型服务商配置实体
 *
 * 重要：isDefault / isEnabled 字段在 Lombok 1.18.20+ 的某些配置组合下，
 * 注解处理器生成的 getter 名称（getIsXxx vs isXxx）会有歧义，
 * 导致方法引用（ModelProvider::getIsDefault）编译失败。
 *
 * 这里采用"双保险"策略：
 * 1. 字段名以 is 开头（兼容 DB 列名）
 * 2. 在字段上加 @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) 跳过 Lombok 自动生成
 * 3. 在类底部显式声明 4 个 getter/setter，方法名固定为 getIsDefault/setIsDefault/getIsEnabled/setIsEnabled
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

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ModelType type;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "api_url", nullable = false, length = 500)
    private String apiUrl;

    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = new BigDecimal("0.7");

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(precision = 5, scale = 4)
    private BigDecimal topP;

    /** 是否为默认模型。⚠️ 跳过 Lombok 自动生成，显式声明 getter/setter，方法名固定为 getIsDefault/setIsDefault。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    /** 是否启用。⚠️ 跳过 Lombok 自动生成，显式声明 getter/setter，方法名固定为 getIsEnabled/setIsEnabled。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    // ==================== 显式 getter/setter，绕开 Lombok 命名歧义 ====================

    public Boolean getIsDefault() {
        return this.isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getIsEnabled() {
        return this.isEnabled;
    }

    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
}
