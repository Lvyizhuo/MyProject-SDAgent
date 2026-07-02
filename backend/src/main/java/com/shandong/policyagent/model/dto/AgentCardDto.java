package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能体卡片视图 DTO。
 * 暴露给前端 /api/admin/agents 列表接口，不返回敏感字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCardDto {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String category;
    private String categoryLabel;
    private String categoryTone;
    private String icon;
    private String status;
    private Boolean isDefault;
    private String version;
    private Long dailyCalls;
    private String updatedAtLabel;
    private List<BindingDto> bindings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingDto {
        private String role;
        private String modelName;
    }
}
