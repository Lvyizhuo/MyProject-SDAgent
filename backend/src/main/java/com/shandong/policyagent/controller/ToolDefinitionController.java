package com.shandong.policyagent.controller;

import com.shandong.policyagent.model.dto.ToolCardDto;
import com.shandong.policyagent.model.dto.ToolUpsertRequest;
import com.shandong.policyagent.model.dto.ToggleEnabledRequest;
import com.shandong.policyagent.service.ToolDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/tools")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*")
@Tag(name = "工具管理", description = "LLM 可调用工具的定义、启停与测试")
public class ToolDefinitionController {

    private final ToolDefinitionService service;

    @Operation(summary = "获取工具列表", description = "获取所有工具定义的卡片视图数据")
    @GetMapping
    public ResponseEntity<List<ToolCardDto>> list() {
        return ResponseEntity.ok(service.listCards());
    }

    @Operation(summary = "获取工具详情", description = "按 ID 获取工具定义详情")
    @GetMapping("/{id}")
    public ResponseEntity<ToolCardDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCard(id));
    }

    @Operation(summary = "创建工具", description = "创建新的工具定义")
    @PostMapping
    public ResponseEntity<ToolCardDto> create(@Valid @RequestBody ToolUpsertRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @Operation(summary = "更新工具", description = "按 ID 更新工具定义")
    @PutMapping("/{id}")
    public ResponseEntity<ToolCardDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ToolUpsertRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @Operation(summary = "删除工具", description = "按 ID 删除工具定义")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "启用/禁用工具", description = "全局开关工具启用状态")
    @PostMapping("/{id}/enabled")
    public ResponseEntity<ToolCardDto> setEnabled(
            @PathVariable Long id,
            @RequestBody ToggleEnabledRequest request) {
        return ResponseEntity.ok(service.setEnabled(id, Boolean.TRUE.equals(request.getEnabled())));
    }

    @Operation(summary = "测试工具", description = "对工具定义执行连通性/可用性测试")
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable Long id) {
        return ResponseEntity.ok(service.test(id));
    }
}
