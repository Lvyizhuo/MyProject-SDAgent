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
public class UrlImportRejectRequest {

    @NotBlank(message = "驳回原因不能为空")
    private String reason;
}