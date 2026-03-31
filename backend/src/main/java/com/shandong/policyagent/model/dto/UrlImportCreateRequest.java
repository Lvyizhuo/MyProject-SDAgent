package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "请选择目标知识库")
    private Long folderId;

    // 兼容旧参数，当前版本由知识库固定绑定 embedding，不再使用该字段。
    private String embeddingModel;
    private String titleOverride;
    private String remark;
}