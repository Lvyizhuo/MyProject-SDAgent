package com.shandong.policyagent.model;

import com.shandong.policyagent.entity.ModelType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 模型服务商创建/更新请求
 *
 * isDefault / isEnabled 字段显式声明 getter/setter 名称，
 * 避免 Lombok 1.18.20+ 对 Boolean 包装类型方法命名的不稳定。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProviderRequest {

    @Size(max = 100, message = "模型名称长度不能超过 100 字符")
    private String name;

    @NotNull(message = "模型类型不能为空")
    private ModelType type;

    @NotBlank(message = "服务商不能为空")
    @Size(max = 50, message = "服务商长度不能超过 50 字符")
    private String provider;

    @NotBlank(message = "API地址不能为空")
    @Size(max = 500, message = "API 地址长度不能超过 500 字符")
    private String apiUrl;

    /** API 密钥。编辑时允许留空以保持原值。 */
    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过 100 字符")
    private String modelName;

    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private BigDecimal temperature;

    @Min(value = 1, message = "最大 Token 必须大于 0")
    @Max(value = 1000000, message = "最大 Token 不能超过 1000000")
    private Integer maxTokens;

    @DecimalMin(value = "0.0", message = "TopP 不能小于 0")
    @DecimalMax(value = "1.0", message = "TopP 不能大于 1")
    private BigDecimal topP;

    /** 是否设为默认 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean isDefault;

    /** 是否启用 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean isEnabled;

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
}
