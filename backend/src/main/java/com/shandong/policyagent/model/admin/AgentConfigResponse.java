package com.shandong.policyagent.model.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigResponse {

    private Long id;
    private String name;
    private String description;
    private String modelProvider;
    private String apiKey;
    private String apiUrl;
    private String modelName;
    private Double temperature;
    private String systemPrompt;
    private String greetingMessage;
    private Map<String, Object> skills;
    private List<Map<String, Object>> mcpServersConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
