package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 卡片视图专用的 Model DTO。
 * 既存的 ModelProviderResponse 完整暴露数据库字段（含 API Key 脱敏），
 * 本 DTO 只暴露卡片 UI 需要的展示字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCardDto {
    private Long id;
    private String name;
    private String provider;
    private String modelKey;
    private String modelType;
    private String modelTypeLabel;
    private String description;
    private String icon;
    private Boolean enabled;
    private Boolean isDefault;
    /** ok / warn / err */
    private String status;
    private Long latencyMs;
    private Integer usedByAgentCount;
    private List<String> capabilities;
}
