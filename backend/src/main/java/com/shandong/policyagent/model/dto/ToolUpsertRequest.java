package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolUpsertRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;
    /** web / calc / parse / external */
    @NotBlank
    private String category;
    private String icon;
    private Boolean enabled;
}
