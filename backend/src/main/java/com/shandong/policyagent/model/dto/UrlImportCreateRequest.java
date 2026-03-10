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
public class UrlImportCreateRequest {

    @NotBlank(message = "网站链接不能为空")
    private String url;

    private Long folderId;
    private String embeddingModel;
    private String titleOverride;
    private String remark;
}