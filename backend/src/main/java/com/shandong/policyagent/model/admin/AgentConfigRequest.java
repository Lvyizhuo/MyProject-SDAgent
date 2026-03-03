package com.shandong.policyagent.model.admin;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigRequest {

    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 100, message = "智能体名称长度不能超过 100 字符")
    private String name;

    @Size(max = 500, message = "描述长度不能超过 500 字符")
    private String description;

    @NotBlank(message = "模型提供商不能为空")
    @Size(max = 50, message = "模型提供商长度不能超过 50 字符")
    private String modelProvider;

    @Size(max = 255, message = "API Key 长度不能超过 255 字符")
    private String apiKey;

    @Size(max = 255, message = "API URL 长度不能超过 255 字符")
    private String apiUrl;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过 100 字符")
    private String modelName;

    @DecimalMin(value = "0.0", message = "温度值不能小于 0.0")
    @DecimalMax(value = "1.0", message = "温度值不能大于 1.0")
    private Double temperature;

    @NotBlank(message = "系统提示词不能为空")
    @Size(max = 10000, message = "系统提示词长度不能超过 10000 字符")
    private String systemPrompt;

    @Size(max = 1000, message = "开场白长度不能超过 1000 字符")
    private String greetingMessage;

    @NotNull(message = "技能配置不能为空")
    private Map<String, Object> skills;

    @NotNull(message = "MCP 服务器配置不能为空")
    private List<Map<String, Object>> mcpServersConfig;
}
