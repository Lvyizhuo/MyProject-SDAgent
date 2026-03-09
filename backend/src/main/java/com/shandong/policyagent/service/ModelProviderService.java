package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.model.ModelProviderRequest;
import com.shandong.policyagent.model.ModelProviderResponse;
import com.shandong.policyagent.repository.ModelProviderRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProviderService {

    private final ModelProviderRepository modelProviderRepository;
    private final ApiKeyCipherService apiKeyCipherService;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.model-provider.direct.connect-timeout-seconds:10}")
    private int directConnectTimeoutSeconds;

    @Value("${app.model-provider.direct.read-timeout-seconds:90}")
    private int directReadTimeoutSeconds;

    public List<ModelProviderResponse> getAllModels(ModelType type) {
        List<ModelProvider> models = type != null
                ? modelProviderRepository.findByType(type)
                : modelProviderRepository.findAll();

        return models.stream()
                .sorted(Comparator.comparing(ModelProvider::getIsDefault, Comparator.nullsLast(Boolean::compareTo)).reversed()
                        .thenComparing(ModelProvider::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ModelProviderResponse::fromEntity)
                .toList();
    }

    public ModelProviderResponse getModelById(Long id) {
        return ModelProviderResponse.fromEntity(getModelEntity(id));
    }

    public ModelProvider getModelEntity(Long id) {
        return modelProviderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + id));
    }

    @Transactional
    public ModelProvider getModelEntityForRuntime(Long id, ModelType expectedType) {
        ModelProvider model = getModelEntity(id);
        if (expectedType != null && model.getType() != expectedType) {
            throw new IllegalArgumentException("模型类型不匹配: " + id);
        }
        if (!Boolean.TRUE.equals(model.getIsEnabled())) {
            throw new IllegalArgumentException("所选模型已禁用: " + model.getName());
        }

        ModelProvider runtimeModel = cloneForRuntime(model);
        runtimeModel.setApiKey(resolveRuntimeApiKey(model));
        return runtimeModel;
    }

    @Transactional
    public ModelProviderResponse createModel(ModelProviderRequest request, Long createdBy) {
        validateRequest(request, true);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            modelProviderRepository.clearDefaultByType(request.getType());
        }

        String resolvedModelName = normalizeModelName(request.getProvider(), request.getModelName());
        String resolvedDisplayName = resolveDisplayName(request.getName(), resolvedModelName);

        ModelProvider model = ModelProvider.builder()
                .name(resolvedDisplayName)
                .type(request.getType())
                .provider(request.getProvider().trim())
                .apiUrl(normalizeBaseUrl(request.getApiUrl()))
                .apiKey(apiKeyCipherService.encrypt(request.getApiKey().trim()))
                .modelName(resolvedModelName)
                .temperature(request.getTemperature() != null ? request.getTemperature() : new BigDecimal("0.7"))
                .maxTokens(request.getMaxTokens())
                .topP(request.getTopP())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .isEnabled(!Boolean.FALSE.equals(request.getIsEnabled()))
                .createdBy(createdBy)
                .build();

        ModelProvider saved = modelProviderRepository.save(model);
        log.info("模型创建成功: id={}, name={}, type={}, createdBy={}",
                saved.getId(), saved.getName(), saved.getType(), createdBy);
        return ModelProviderResponse.fromEntity(saved);
    }

    @Transactional
    public ModelProviderResponse updateModel(Long id, ModelProviderRequest request) {
        validateRequest(request, false);
        ModelProvider model = getModelEntity(id);
        ModelType targetType = request.getType() != null ? request.getType() : model.getType();
        String resolvedModelName = request.getModelName() != null && !request.getModelName().isBlank()
                ? normalizeModelName(request.getProvider() != null ? request.getProvider() : model.getProvider(), request.getModelName())
                : model.getModelName();
        String resolvedDisplayName = resolveDisplayName(request.getName(), resolvedModelName);

        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(model.getIsDefault())) {
            modelProviderRepository.clearDefaultByType(targetType);
        }
        if (request.getType() != null && request.getType() != model.getType() && Boolean.TRUE.equals(model.getIsDefault())) {
            modelProviderRepository.clearDefaultByType(request.getType());
        }

        model.setName(resolvedDisplayName);
        if (request.getType() != null) {
            model.setType(request.getType());
        }
        if (request.getProvider() != null && !request.getProvider().isBlank()) {
            model.setProvider(request.getProvider().trim());
        }
        if (request.getApiUrl() != null && !request.getApiUrl().isBlank()) {
            model.setApiUrl(normalizeBaseUrl(request.getApiUrl()));
        }
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            model.setApiKey(apiKeyCipherService.encrypt(request.getApiKey().trim()));
        }
        if (request.getModelName() != null && !request.getModelName().isBlank()) {
            model.setModelName(resolvedModelName);
        }
        if (request.getTemperature() != null) {
            model.setTemperature(request.getTemperature());
        }
        model.setMaxTokens(request.getMaxTokens());
        model.setTopP(request.getTopP());
        if (request.getIsDefault() != null) {
            model.setIsDefault(request.getIsDefault());
        }
        if (request.getIsEnabled() != null) {
            model.setIsEnabled(request.getIsEnabled());
            if (!request.getIsEnabled() && Boolean.TRUE.equals(model.getIsDefault())) {
                model.setIsDefault(false);
            }
        }

        ModelProvider saved = modelProviderRepository.save(model);
        log.info("模型更新成功: id={}, name={}, type={}", saved.getId(), saved.getName(), saved.getType());
        return ModelProviderResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteModel(Long id) {
        ModelProvider model = getModelEntity(id);
        modelProviderRepository.delete(model);
        log.info("模型删除成功: id={}, name={}", id, model.getName());
    }

    @Transactional
    public ModelProviderResponse setDefault(Long id) {
        ModelProvider model = getModelEntity(id);
        if (!Boolean.TRUE.equals(model.getIsEnabled())) {
            throw new IllegalArgumentException("禁用模型不能设为默认");
        }

        modelProviderRepository.clearDefaultByType(model.getType());
        model.setIsDefault(true);
        ModelProvider saved = modelProviderRepository.save(model);
        log.info("模型设为默认成功: id={}, type={}", id, model.getType());
        return ModelProviderResponse.fromEntity(saved);
    }

    public Map<String, List<ModelOption>> getModelOptions() {
        Map<String, List<ModelOption>> result = new HashMap<>();
        for (ModelType type : ModelType.values()) {
            List<ModelOption> options = modelProviderRepository.findByTypeAndIsEnabled(type, true).stream()
                    .sorted(Comparator.comparing(ModelProvider::getIsDefault, Comparator.nullsLast(Boolean::compareTo)).reversed()
                            .thenComparing(ModelProvider::getName))
                    .map(model -> ModelOption.builder()
                            .id(model.getId())
                            .name(model.getName())
                            .isDefault(model.getIsDefault())
                            .build())
                    .toList();
            result.put(type.name(), options);
        }
        return result;
    }

    @Transactional
    public ModelProvider getDefaultModel(ModelType type) {
        return modelProviderRepository.findByTypeAndIsDefaultTrueAndIsEnabledTrue(type)
                .map(model -> {
                    ModelProvider runtimeModel = cloneForRuntime(model);
                    runtimeModel.setApiKey(resolveRuntimeApiKey(model));
                    return runtimeModel;
                })
                .orElse(null);
    }

    public ModelProviderResponse getDefaultModelResponse(ModelType type) {
        return Optional.ofNullable(getDefaultModel(type))
                .map(ModelProviderResponse::fromEntity)
                .orElse(null);
    }

    public Map<String, Object> testConnection(Long id) {
        ModelProvider model = getModelEntityForRuntime(id, null);
        String baseUrl = normalizeBaseUrl(model.getApiUrl());
        String modelsUrl = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
        long startTime = System.currentTimeMillis();

        try {
            RestClient client = restClientBuilder
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + model.getApiKey())
                    .build();

                ResponseEntity<String> response = switch (model.getType()) {
                case LLM -> client.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                        "model", model.getModelName(),
                        "messages", List.of(Map.of("role", "user", "content", "Reply with OK only.")),
                        "temperature", 0.1,
                        "max_tokens", 16
                    ))
                    .retrieve()
                    .toEntity(String.class);
                default -> client.get()
                    .uri("/models")
                    .retrieve()
                    .toEntity(String.class);
                };

            long latencyMs = System.currentTimeMillis() - startTime;
            log.info("模型连接测试成功: id={}, status={}, latencyMs={}, url={}",
                    id, response.getStatusCode(), latencyMs, modelsUrl);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "连接成功");
            result.put("latencyMs", latencyMs);
            return result;
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("模型连接测试失败: id={}, latencyMs={}, url={}, error={}",
                    id, latencyMs, modelsUrl, ex.getMessage());

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", buildTestErrorMessage(ex));
            result.put("latencyMs", latencyMs);
            result.put("error", ex.getClass().getSimpleName());
            return result;
        }
    }

    public String executeChatCompletion(ModelProvider model, String systemPrompt, String userPrompt) {
        String baseUrl = normalizeBaseUrl(model.getApiUrl());
        RestClient client = createTimeoutRestClient(baseUrl, model.getApiKey());

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model.getModelName());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", model.getTemperature() != null ? model.getTemperature().doubleValue() : 0.7D);
        if (model.getMaxTokens() != null) {
            payload.put("max_tokens", model.getMaxTokens());
        }
        if (model.getTopP() != null) {
            payload.put("top_p", model.getTopP());
        }

        Map<?, ?> response = client.post()
                .uri("/chat/completions")
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("模型未返回响应");
        }

        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("模型响应中缺少 choices");
        }

        Object firstChoice = choices.getFirst();
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new IllegalStateException("模型响应格式无效");
        }

        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("模型响应缺少 message 字段");
        }

        Object contentObj = messageMap.get("content");
        String content = contentObj instanceof String text ? text : null;
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型响应内容为空");
        }

        return content;
    }

    private RestClient createTimeoutRestClient(String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(directConnectTimeoutSeconds * 1000);
        requestFactory.setReadTimeout(directReadTimeoutSeconds * 1000);

        return restClientBuilder
                .clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    private void validateRequest(ModelProviderRequest request, boolean creating) {
        if (request.getType() == null) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        if (request.getProvider() == null || request.getProvider().isBlank()) {
            throw new IllegalArgumentException("服务商不能为空");
        }
        if (request.getApiUrl() == null || request.getApiUrl().isBlank()) {
            throw new IllegalArgumentException("API 地址不能为空");
        }
        if (request.getModelName() == null || request.getModelName().isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (creating && (request.getApiKey() == null || request.getApiKey().isBlank())) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
    }

    private String resolveRuntimeApiKey(ModelProvider model) {
        ApiKeyCipherService.DecryptionResult decryptResult = apiKeyCipherService.decryptWithMetadata(model.getApiKey());
        if (decryptResult.isUsedLegacySecret()) {
            model.setApiKey(apiKeyCipherService.encrypt(decryptResult.getPlainText()));
            modelProviderRepository.save(model);
            log.info("模型 API Key 已使用当前密钥重新加密: id={}, name={}", model.getId(), model.getName());
        }
        return decryptResult.getPlainText();
    }

    private String normalizeBaseUrl(String apiUrl) {
        if (apiUrl == null) {
            return null;
        }
        String trimmed = apiUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizeModelName(String provider, String modelName) {
        String trimmed = modelName == null ? "" : modelName.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }

        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!"volcano".equals(normalizedProvider)) {
            return trimmed;
        }

        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "doubao-seed-code" -> "doubao-seed-code-preview-251028";
            default -> trimmed;
        };
    }

    private String resolveDisplayName(String name, String modelName) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return modelName;
    }

    private String buildTestErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "连接失败";
        }
        if (message.contains("401")) {
            return "API Key 无效";
        }
        if (message.contains("403")) {
            return "当前账号无权限访问该模型服务";
        }
        if (message.contains("404")) {
            return "API 地址不可用，请检查 Base URL";
        }
        if (message.contains("InvalidEndpointOrModel.NotFound") || message.contains("does not exist")) {
            return "模型名称不可用，或当前 API Key 无权访问该模型";
        }
        if (message.contains("Connection refused")) {
            return "无法连接到模型服务，请检查网络或地址";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private ModelProvider cloneForRuntime(ModelProvider model) {
        return ModelProvider.builder()
                .id(model.getId())
                .name(model.getName())
                .type(model.getType())
                .provider(model.getProvider())
                .apiUrl(model.getApiUrl())
                .apiKey(model.getApiKey())
                .modelName(model.getModelName())
                .temperature(model.getTemperature())
                .maxTokens(model.getMaxTokens())
                .topP(model.getTopP())
                .isDefault(model.getIsDefault())
                .isEnabled(model.getIsEnabled())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .createdBy(model.getCreatedBy())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelOption {
        private Long id;
        private String name;
        private Boolean isDefault;
    }
}
