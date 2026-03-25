package com.shandong.policyagent.model;

import com.shandong.policyagent.entity.ModelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
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

    private Boolean isDefault;

    private Boolean isEnabled;

    private Boolean builtIn;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 从实体转换
     */
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
}
