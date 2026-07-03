package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 创建/更新智能体的请求体。
 *
 * 字段说明：
 * - 基础：name / code / description / category / icon / status / version
 * - 模型：bindings（多角色 LLM / VISION / AUDIO / EMBEDDING / RERANK）
 * - 工具：enabledTools（技能 key 列表）+ 知识库 knowledgeFolderId
 * - 提示词：systemPrompt / greetingMessage / skills / mcpServersConfig
 *   （这些是 agent 自身一份，编辑时可选。兼容老版本：未传则保持原值）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUpsertRequest {

    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 100, message = "智能体名称长度不能超过 100 字符")
    private String name;

    /** 内部编码（创建时必填，更新时可选）。留空则按 name 自动生成 kebab-case */
    private String code;

    @Size(max = 500, message = "描述长度不能超过 500 字符")
    private String description;

    /** chat / workflow / support */
    private String category;

    /** 前端 lucide 名称：MessageSquare / Calculator / FileSearch / BarChart3 / Mic */
    private String icon;

    /** draft / published */
    private String status;

    private String version;

    private List<BindingDto> bindings;

    private List<String> enabledTools;

    private Long knowledgeFolderId;

    @Size(max = 10000, message = "系统提示词长度不能超过 10000 字符")
    private String systemPrompt;

    @Size(max = 1000, message = "开场白长度不能超过 1000 字符")
    private String greetingMessage;

    /** 技能模块配置，键为技能 key，值为配置对象（含 enabled 等） */
    private Map<String, Object> skills;

    /** MCP 服务器配置列表 */
    private List<Map<String, Object>> mcpServersConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingDto {
        /** LLM / VISION / AUDIO / EMBEDDING / RERANK / FUNCTION */
        @NotBlank
        private String role;
        private Long modelId;
        private String toolCode;
        private String displayName;
    }
}
