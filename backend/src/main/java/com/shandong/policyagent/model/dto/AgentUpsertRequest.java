package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建/更新智能体的请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUpsertRequest {
    @NotBlank(message = "智能体名称不能为空")
    private String name;

    /** 内部编码（创建时必填，更新时可选） */
    private String code;

    private String description;
    private String category;
    private String icon;
    /** draft / published */
    private String status;
    private String version;
    private List<BindingDto> bindings;
    private List<String> enabledTools;
    private Long knowledgeFolderId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingDto {
        /** LLM / VISION / AUDIO / FUNCTION */
        @NotBlank
        private String role;
        private Long modelId;
        private String toolCode;
        private String displayName;
    }
}
