package com.shandong.policyagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.model.admin.AgentConfigRequest;
import com.shandong.policyagent.model.admin.AgentConfigResponse;
import com.shandong.policyagent.repository.AgentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService {

    private static final String MASK_PLACEHOLDER = "****";

    private final AgentConfigRepository agentConfigRepository;
    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentConfigResponse getCurrentConfig() {
        AgentConfig config = getCurrentEntity();
        return toResponse(config, true);
    }

    public AgentConfigResponse updateConfig(AgentConfigRequest request) {
        validateConfig(request);

        AgentConfig config = getCurrentEntity();
        config.setName(request.getName());
        config.setDescription(request.getDescription());
        config.setLlmModelId(request.getLlmModelId());
        config.setVisionModelId(request.getVisionModelId());
        config.setAudioModelId(request.getAudioModelId());
        config.setEmbeddingModelId(request.getEmbeddingModelId());

        if (request.getLlmModelId() == null) {
            config.setModelProvider(request.getModelProvider());
            config.setApiUrl(request.getApiUrl());
            config.setModelName(request.getModelName());
            config.setTemperature(request.getTemperature());
            config.setApiKey(resolveApiKeyForManualConfig(request.getApiKey(), config.getApiKey()));
        }

        config.setSystemPrompt(request.getSystemPrompt());
        config.setGreetingMessage(request.getGreetingMessage());

        try {
            config.setSkills(objectMapper.writeValueAsString(request.getSkills()));
            config.setMcpServersConfig(objectMapper.writeValueAsString(request.getMcpServersConfig()));
        } catch (JsonProcessingException e) {
            log.error("无法序列化配置 JSON", e);
            throw new IllegalArgumentException("配置 JSON 格式错误");
        }

        AgentConfig saved = agentConfigRepository.save(config);
        log.info("智能体配置更新成功: id={}, llmModelId={}", saved.getId(), saved.getLlmModelId());
        return toResponse(saved, true);
    }

    public AgentConfigResponse resetToDefault() {
        AgentConfig config = getCurrentEntity();

        config.setName("政策问答智能体");
        config.setDescription("用于山东省以旧换新补贴政策咨询");
        config.setModelProvider("dashscope");
        config.setApiKey("${DASHSCOPE_API_KEY}");
        config.setApiUrl("https://dashscope.aliyuncs.com/compatible-mode");
        config.setModelName("qwen3.5-plus");
        config.setTemperature(0.70);
        config.setLlmModelId(null);
        config.setVisionModelId(null);
        config.setAudioModelId(null);
        config.setEmbeddingModelId(null);
        config.setSystemPrompt("""
                你是一个专业的山东省以旧换新补贴政策咨询助手。你的任务是：

                1. 准确理解用户关于补贴政策的咨询问题
                2. 基于提供的政策文档内容回答问题
                3. 当信息不足时，可以主动询问用户更多细节
                4. 对于补贴金额计算，使用提供的工具进行精确计算
                5. 保持回答准确、客观、易于理解

                请始终基于提供的事实依据回答，不要编造信息。
                """);
        config.setGreetingMessage("""
                您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。

                **我可以帮您：**
                - 查询各类产品补贴金额
                - 了解申请条件和流程
                - 计算您能获得的补贴
                - 解答政策相关疑问
                """);

        Map<String, Object> defaultSkills = new HashMap<>();
        Map<String, Object> webSearch = new HashMap<>();
        webSearch.put("enabled", true);
        defaultSkills.put("webSearch", webSearch);

        Map<String, Object> subsidyCalculator = new HashMap<>();
        subsidyCalculator.put("enabled", true);
        defaultSkills.put("subsidyCalculator", subsidyCalculator);

        Map<String, Object> fileParser = new HashMap<>();
        fileParser.put("enabled", true);
        defaultSkills.put("fileParser", fileParser);

        try {
            config.setSkills(objectMapper.writeValueAsString(defaultSkills));
            config.setMcpServersConfig("[]");
        } catch (JsonProcessingException e) {
            log.error("无法序列化默认配置 JSON", e);
            throw new IllegalArgumentException("配置 JSON 格式错误");
        }

        AgentConfig saved = agentConfigRepository.save(config);
        log.info("智能体配置重置为默认: id={}", saved.getId());
        return toResponse(saved, true);
    }

    private AgentConfig getCurrentEntity() {
        return agentConfigRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("智能体配置不存在，请初始化"));
    }

    private void validateConfig(AgentConfigRequest request) {
        if (request.getSystemPrompt() == null || request.getSystemPrompt().trim().isEmpty()) {
            throw new IllegalArgumentException("系统提示词不能为空");
        }

        validateModelSelection(request.getLlmModelId(), ModelType.LLM, "大语言");
        validateModelSelection(request.getVisionModelId(), ModelType.VISION, "视觉");
        validateModelSelection(request.getAudioModelId(), ModelType.AUDIO, "语音");
        validateModelSelection(request.getEmbeddingModelId(), ModelType.EMBEDDING, "嵌入");

        if (request.getLlmModelId() == null) {
            if (request.getModelName() == null || request.getModelName().trim().isEmpty()) {
                throw new IllegalArgumentException("模型名称不能为空");
            }
            if (request.getApiUrl() == null || request.getApiUrl().trim().isEmpty()) {
                throw new IllegalArgumentException("API 地址不能为空");
            }
            if (request.getModelProvider() == null || request.getModelProvider().trim().isEmpty()) {
                throw new IllegalArgumentException("模型提供商不能为空");
            }
        }
    }

    private void validateModelSelection(Long modelId, ModelType type, String label) {
        if (modelId == null) {
            return;
        }
        modelProviderService.getModelEntityForRuntime(modelId, type);
        log.debug("{}模型选择校验通过: modelId={}", label, modelId);
    }

    private AgentConfigResponse toResponse(AgentConfig config, boolean maskApiKey) {
        try {
            Map<String, Object> skills = config.getSkills() != null
                    ? objectMapper.readValue(config.getSkills(), new TypeReference<Map<String, Object>>() {})
                    : new HashMap<>();

            List<Map<String, Object>> mcpServersConfig = config.getMcpServersConfig() != null
                    ? objectMapper.readValue(config.getMcpServersConfig(), new TypeReference<List<Map<String, Object>>>() {})
                    : new ArrayList<>();

                AgentConfigResponse.ResolvedModelSnapshot resolvedLlmModel = resolveModelSnapshot(config.getLlmModelId());
                AgentConfigResponse.ResolvedModelSnapshot resolvedVisionModel = resolveModelSnapshot(config.getVisionModelId());
                AgentConfigResponse.ResolvedModelSnapshot resolvedAudioModel = resolveModelSnapshot(config.getAudioModelId());
                AgentConfigResponse.ResolvedModelSnapshot resolvedEmbeddingModel = resolveModelSnapshot(config.getEmbeddingModelId());

                String effectiveConfigSource = resolvedLlmModel != null ? "MODEL_PROVIDER" : "MANUAL";
                String effectiveModelProvider = resolvedLlmModel != null ? resolvedLlmModel.getProvider() : config.getModelProvider();
                String effectiveApiUrl = resolvedLlmModel != null ? resolvedLlmModel.getApiUrl() : config.getApiUrl();
                String effectiveModelName = resolvedLlmModel != null ? resolvedLlmModel.getModelName() : config.getModelName();
                Double effectiveTemperature = resolvedLlmModel != null ? resolvedLlmModel.getTemperature() : config.getTemperature();
                Integer effectiveMaxTokens = resolvedLlmModel != null ? resolvedLlmModel.getMaxTokens() : null;
                java.math.BigDecimal effectiveTopP = resolvedLlmModel != null ? resolvedLlmModel.getTopP() : null;

            return AgentConfigResponse.builder()
                    .id(config.getId())
                    .name(config.getName())
                    .description(config.getDescription())
                    .modelProvider(config.getModelProvider())
                    .apiKey(maskApiKey ? maskApiKey(config.getApiKey()) : config.getApiKey())
                    .apiUrl(config.getApiUrl())
                    .modelName(config.getModelName())
                    .temperature(config.getTemperature())
                    .llmModelId(config.getLlmModelId())
                    .visionModelId(config.getVisionModelId())
                    .audioModelId(config.getAudioModelId())
                    .embeddingModelId(config.getEmbeddingModelId())
                    .effectiveConfigSource(effectiveConfigSource)
                    .effectiveModelProvider(effectiveModelProvider)
                    .effectiveApiUrl(effectiveApiUrl)
                    .effectiveModelName(effectiveModelName)
                    .effectiveTemperature(effectiveTemperature)
                    .effectiveMaxTokens(effectiveMaxTokens)
                    .effectiveTopP(effectiveTopP)
                    .resolvedLlmModel(resolvedLlmModel)
                    .resolvedVisionModel(resolvedVisionModel)
                    .resolvedAudioModel(resolvedAudioModel)
                    .resolvedEmbeddingModel(resolvedEmbeddingModel)
                    .systemPrompt(config.getSystemPrompt())
                    .greetingMessage(config.getGreetingMessage())
                    .skills(skills)
                    .mcpServersConfig(mcpServersConfig)
                    .createdAt(config.getCreatedAt())
                    .updatedAt(config.getUpdatedAt())
                    .build();
        } catch (JsonProcessingException e) {
            log.error("无法解析配置 JSON", e);
            throw new IllegalStateException("配置数据格式错误");
        }
    }

    private AgentConfigResponse.ResolvedModelSnapshot resolveModelSnapshot(Long modelId) {
        if (modelId == null) {
            return null;
        }

        try {
            ModelProvider model = modelProviderService.getModelEntity(modelId);
            return AgentConfigResponse.ResolvedModelSnapshot.builder()
                    .id(model.getId())
                    .name(model.getName())
                    .provider(model.getProvider())
                    .apiUrl(model.getApiUrl())
                    .modelName(model.getModelName())
                    .temperature(model.getTemperature() != null ? model.getTemperature().doubleValue() : null)
                    .maxTokens(model.getMaxTokens())
                    .topP(model.getTopP())
                    .isDefault(model.getIsDefault())
                    .isEnabled(model.getIsEnabled())
                    .build();
        } catch (Exception ex) {
            log.warn("解析已选模型失败: modelId={} | error={}", modelId, ex.getMessage());
            return null;
        }
    }

    private String resolveApiKeyForManualConfig(String incomingApiKey, String currentApiKey) {
        if (incomingApiKey == null || incomingApiKey.isBlank() || incomingApiKey.contains(MASK_PLACEHOLDER)) {
            return currentApiKey;
        }
        return incomingApiKey;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.startsWith("${") && apiKey.endsWith("}")) {
            return apiKey;
        }
        if (apiKey.length() <= 8) {
            return MASK_PLACEHOLDER;
        }

        String first4 = apiKey.substring(0, 4);
        String last4 = apiKey.substring(apiKey.length() - 4);
        return first4 + MASK_PLACEHOLDER + last4;
    }
}
