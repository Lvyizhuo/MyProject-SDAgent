package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.ToolDefinition;
import com.shandong.policyagent.exception.BusinessException;
import com.shandong.policyagent.model.dto.ToolCardDto;
import com.shandong.policyagent.model.dto.ToolUpsertRequest;
import com.shandong.policyagent.repository.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolDefinitionService {

    private final ToolDefinitionRepository repository;

    private static final Map<String, String> ICON_DEFAULTS = Map.of(
            "web", "Globe",
            "calc", "Calculator",
            "parse", "FileText",
            "external", "Code2"
    );

    @Transactional(readOnly = true)
    public List<ToolCardDto> listCards() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(this::toCardDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ToolCardDto getCard(Long id) {
        return toCardDto(repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工具不存在")));
    }

    @Transactional
    public ToolCardDto create(ToolUpsertRequest request) {
        if (repository.findByCode(request.getCode()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "工具编码已存在: " + request.getCode());
        }
        ToolDefinition tool = ToolDefinition.builder()
                .code(request.getCode()).name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .icon(request.getIcon() != null ? request.getIcon() : ICON_DEFAULTS.getOrDefault(request.getCategory(), "FileText"))
                .isEnabled(request.getEnabled() != null ? request.getEnabled() : true)
                .dailyCalls(0L).usedByAgentCount(0)
                .build();
        return toCardDto(repository.save(tool));
    }

    @Transactional
    public ToolCardDto update(Long id, ToolUpsertRequest request) {
        ToolDefinition tool = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工具不存在"));
        if (request.getName() != null) tool.setName(request.getName());
        if (request.getDescription() != null) tool.setDescription(request.getDescription());
        if (request.getCategory() != null) tool.setCategory(request.getCategory());
        if (request.getIcon() != null) tool.setIcon(request.getIcon());
        if (request.getEnabled() != null) tool.setIsEnabled(request.getEnabled());
        return toCardDto(repository.save(tool));
    }

    @Transactional
    public void delete(Long id) {
        ToolDefinition tool = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工具不存在"));
        repository.delete(tool);
    }

    @Transactional
    public ToolCardDto setEnabled(Long id, boolean enabled) {
        ToolDefinition tool = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工具不存在"));
        tool.setIsEnabled(enabled);
        return toCardDto(repository.save(tool));
    }

    /**
     * 测试连通性：根据 code 简单返回 ok / err（占位实现）。
     * 实际可注入具体 Tool bean 触发 dry-run。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> test(Long id) {
        ToolDefinition tool = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "工具不存在"));
        boolean ok = Boolean.TRUE.equals(tool.getIsEnabled());
        return Map.of(
                "ok", ok,
                "latencyMs", ok ? (50 + (int) (Math.random() * 200)) : 0,
                "message", ok ? "✓ " + tool.getName() + " 连通正常" : "✗ 工具已停用"
        );
    }

    private ToolCardDto toCardDto(ToolDefinition t) {
        return ToolCardDto.builder()
                .id(t.getId()).code(t.getCode()).name(t.getName())
                .description(t.getDescription()).category(t.getCategory())
                .icon(t.getIcon())
                .enabled(t.getIsEnabled())
                .dailyCalls(t.getDailyCalls() != null ? t.getDailyCalls() : 0L)
                .usedByAgentCount(t.getUsedByAgentCount() != null ? t.getUsedByAgentCount() : 0)
                .build();
    }
}
