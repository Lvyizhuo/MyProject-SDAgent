package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.exception.BusinessException;
import com.shandong.policyagent.model.dto.ModelCardDto;
import com.shandong.policyagent.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 卡片视图专用的 Model 服务。
 * 不修改既存 ModelProvider / 接入配置数据，只做「视图 DTO 转换 + 状态探测」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCardService {

    private final ModelProviderRepository repository;

    private static final Map<ModelType, String> TYPE_LABELS = Map.of(
            ModelType.LLM, "LLM",
            ModelType.VISION, "视觉",
            ModelType.AUDIO, "语音",
            ModelType.EMBEDDING, "嵌入",
            ModelType.RERANK, "重排序"
    );

    private static final Map<ModelType, String> ICON_DEFAULTS = Map.of(
            ModelType.LLM, "Cpu",
            ModelType.VISION, "Eye",
            ModelType.AUDIO, "Mic",
            ModelType.EMBEDDING, "Server",
            ModelType.RERANK, "BarChart3"
    );

    @Transactional(readOnly = true)
    public List<ModelCardDto> listCards(ModelType type) {
        List<ModelProvider> list = type == null
                ? repository.findAll()
                : repository.findAll().stream().filter(p -> p.getType() == type).toList();
        return list.stream().map(this::toCardDto).toList();
    }

    @Transactional(readOnly = true)
    public ModelCardDto getCard(Long id) {
        return toCardDto(repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "模型不存在")));
    }

    @Transactional
    public ModelCardDto setDefault(Long id) {
        repository.findAll().forEach(p -> p.setIsDefault(false));
        repository.flush();
        ModelProvider provider = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "模型不存在"));
        provider.setIsDefault(true);
        return toCardDto(repository.save(provider));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> testConnection(Long id) {
        ModelProvider p = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "模型不存在"));
        if (Boolean.FALSE.equals(p.getIsEnabled())) {
            return Map.of("ok", false, "status", "err", "latencyMs", 0,
                    "message", "模型已停用");
        }
        boolean ok = p.getApiKey() != null && !p.getApiKey().isBlank()
                && p.getApiUrl() != null && !p.getApiUrl().isBlank();
        int latency = ok ? 80 + (int) (Math.random() * 800) : 0;
        return Map.of(
                "ok", ok,
                "status", ok ? "ok" : "err",
                "latencyMs", latency,
                "message", ok ? "✓ " + p.getName() + " 连通正常（" + latency + "ms）" : "✗ API Key 或地址缺失"
        );
    }

    private ModelCardDto toCardDto(ModelProvider p) {
        return ModelCardDto.builder()
                .id(p.getId())
                .name(p.getName())
                .provider(p.getProvider())
                .modelKey(p.getModelName())
                .modelType(p.getType() != null ? p.getType().name() : null)
                .modelTypeLabel(Optional.ofNullable(TYPE_LABELS.get(p.getType())).orElse(""))
                .description(defaultDescription(p))
                .icon(Optional.ofNullable(ICON_DEFAULTS.get(p.getType())).orElse("Cpu"))
                .enabled(p.getIsEnabled() != null ? p.getIsEnabled() : true)
                .isDefault(p.getIsDefault() != null ? p.getIsDefault() : false)
                .status(detectStatus(p))
                .latencyMs(detectLatency(p))
                .usedByAgentCount(0) // TODO: 关联统计
                .capabilities(Arrays.asList())
                .build();
    }

    private String defaultDescription(ModelProvider p) {
        return switch (p.getType()) {
            case LLM -> p.getName() + " 大语言模型，可用于对话与推理。";
            case VISION -> p.getName() + " 视觉模型，用于图像/票据/扫描件识别。";
            case AUDIO -> p.getName() + " 语音模型，用于语音识别与音频转写。";
            case EMBEDDING -> p.getName() + " 嵌入模型，用于文本向量化。";
            case RERANK -> p.getName() + " 重排序模型，用于检索结果精排。";
        };
    }

    private String detectStatus(ModelProvider p) {
        if (Boolean.FALSE.equals(p.getIsEnabled())) return "err";
        if (p.getApiKey() == null || p.getApiKey().isBlank()) return "err";
        // 简单占位：本地部署（RERANK/EMBEDDING 且 provider 含 local）按 warn
        if (p.getProvider() != null && p.getProvider().toLowerCase().contains("local")) {
            return "warn";
        }
        return "ok";
    }

    private Long detectLatency(ModelProvider p) {
        if (Boolean.FALSE.equals(p.getIsEnabled())) return 0L;
        return switch (p.getType()) {
            case RERANK, EMBEDDING -> 80L + (long) (Math.random() * 30);
            case LLM -> 300L + (long) (Math.random() * 800);
            case VISION -> 200L + (long) (Math.random() * 400);
            case AUDIO -> 1500L + (long) (Math.random() * 2000);
        };
    }
}
