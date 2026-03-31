package com.shandong.policyagent.model.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    private Long llmModelId;
    private Long visionModelId;
    private Long audioModelId;
    private Long embeddingModelId;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String knowledgeBasePath;
    private String knowledgeBaseEmbeddingModel;
    private Long knowledgeBaseFolderId;
    private String knowledgeBaseFolderName;
    private String knowledgeBaseFolderPath;
    private String effectiveConfigSource;
    private String effectiveModelProvider;
    private String effectiveApiUrl;
    private String effectiveModelName;
    private Double effectiveTemperature;
    private Integer effectiveMaxTokens;
    private BigDecimal effectiveTopP;
    private ResolvedModelSnapshot resolvedLlmModel;
    private ResolvedModelSnapshot resolvedVisionModel;
    private ResolvedModelSnapshot resolvedAudioModel;
    private ResolvedModelSnapshot resolvedEmbeddingModel;
    private String systemPrompt;
    private String greetingMessage;
    private Map<String, Object> skills;
    private List<Map<String, Object>> mcpServersConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedModelSnapshot {
        private Long id;
        private String name;
        private String provider;
        private String apiUrl;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private BigDecimal topP;
        private Boolean isDefault;
        private Boolean isEnabled;
    }
}
