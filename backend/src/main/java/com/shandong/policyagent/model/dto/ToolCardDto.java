package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具卡片 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCardDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String category;
    private String icon;
    private Boolean enabled;
    private Long dailyCalls;
    private Integer usedByAgentCount;
}
