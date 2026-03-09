package com.shandong.policyagent.model;

import com.shandong.policyagent.entity.ModelType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProviderRequest {

    /**
     * 模型名称
     */
    @Size(max = 100, message = "模型名称长度不能超过 100 字符")
    private String name;

    /**
     * 模型类型
     */
    @NotNull(message = "模型类型不能为空")
    private ModelType type;

    /**
     * 服务商标识
     */
    @NotBlank(message = "服务商不能为空")
    @Size(max = 50, message = "服务商长度不能超过 50 字符")
    private String provider;

    /**
     * API 地址
     */
    @NotBlank(message = "API地址不能为空")
    @Size(max = 500, message = "API 地址长度不能超过 500 字符")
    private String apiUrl;

    /**
     * API 密钥。编辑时允许留空以保持原值。
     */
    private String apiKey;

    /**
     * 具体模型名称
     */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过 100 字符")
    private String modelName;

    /**
     * 温度参数
     */
    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private BigDecimal temperature;

    /**
     * 最大Token数
     */
    @Min(value = 1, message = "最大 Token 必须大于 0")
    @Max(value = 1000000, message = "最大 Token 不能超过 1000000")
    private Integer maxTokens;

    /**
     * TopP 采样参数
     */
    @DecimalMin(value = "0.0", message = "TopP 不能小于 0")
    @DecimalMax(value = "1.0", message = "TopP 不能大于 1")
    private BigDecimal topP;

    /**
     * 是否设为默认
     */
    private Boolean isDefault;

    /**
     * 是否启用
     */
    private Boolean isEnabled;
}
