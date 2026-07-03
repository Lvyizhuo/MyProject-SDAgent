package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 智能体卡片视图 DTO（同时充当编辑/详情视图）。
 * 暴露给前端 /api/admin/agents 列表与详情接口，不返回敏感字段。
 *
 * 提示词/技能/MCP 字段也返回，便于前端「编辑」面板一次拉全。
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

    /** 知识库文件夹 ID（兼容旧版单绑定，可选） */
    private Long knowledgeFolderId;
    private String knowledgeFolderName;
    private String knowledgeFolderPath;

    /** 模型绑定：role → 模型/工具 */
    private List<BindingDto> bindings;

    /** 启用的工具 key 列表 */
    private List<String> enabledTools;

    /** 提示词相关 */
    private String systemPrompt;
    private String greetingMessage;
    private Map<String, Object> skills;
    private List<Map<String, Object>> mcpServersConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingDto {
        private String role;
        /** 角色标签，LLM / 视觉 / 语音 / 嵌入 / 重排序 / 函数 */
        private String roleLabel;
        private String modelName;
        private Long modelId;
        private String toolCode;
    }
}
