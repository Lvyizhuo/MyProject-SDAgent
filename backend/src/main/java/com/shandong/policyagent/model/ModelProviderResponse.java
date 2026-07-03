package com.shandong.policyagent.model;

import com.shandong.policyagent.entity.ModelType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型服务商响应
 *
 * isDefault / isEnabled 字段显式声明 getter/setter 名称，
 * 避免 Lombok 1.18.20+ 对 Boolean 包装类型方法命名的不稳定。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProviderResponse {

    private Long id;
    private String name;
    private ModelType type;
    private String provider;
    private String apiUrl;
    private String modelName;
    private String builtinCode;
    private Integer dimensions;
    private BigDecimal temperature;
    private Integer maxTokens;
    private BigDecimal topP;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean isDefault;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean isEnabled;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean builtIn;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ModelProviderResponse fromEntity(com.shandong.policyagent.entity.ModelProvider entity) {
        if (entity == null) {
            return null;
        }
        return ModelProviderResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .provider(entity.getProvider())
                .apiUrl(entity.getApiUrl())
                .modelName(entity.getModelName())
                .builtinCode(null)
                .dimensions(null)
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .topP(entity.getTopP())
                .isDefault(entity.getIsDefault())
                .isEnabled(entity.getIsEnabled())
                .builtIn(false)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ==================== 显式覆盖 Lombok 命名 ====================

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

    public Boolean getBuiltIn() {
        return this.builtIn;
    }

    public void setBuiltIn(Boolean builtIn) {
        this.builtIn = builtIn;
    }
}
