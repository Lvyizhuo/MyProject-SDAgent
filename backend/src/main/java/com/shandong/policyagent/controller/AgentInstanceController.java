package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.AgentCardDto;
import com.shandong.policyagent.model.dto.AgentUpsertRequest;
import com.shandong.policyagent.service.AgentInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "智能体管理", description = "多智能体实例的增删改查、默认设置与复制")
public class AgentInstanceController {

    private final AgentInstanceService service;

    @Operation(summary = "获取智能体列表", description = "获取所有智能体实例的卡片视图数据")
    @GetMapping
    public ResponseEntity<List<AgentCardDto>> list() {
        return ResponseEntity.ok(service.listCards());
    }

    @Operation(summary = "获取智能体详情", description = "按 ID 获取单个智能体实例详情")
    @GetMapping("/{id}")
    public ResponseEntity<AgentCardDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCard(id));
    }

    @Operation(summary = "创建智能体", description = "创建一个新的智能体实例")
    @PostMapping
    public ResponseEntity<AgentCardDto> create(
            @Valid @RequestBody AgentUpsertRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.create(request, user));
    }

    @Operation(summary = "更新智能体", description = "按 ID 更新智能体实例配置")
    @PutMapping("/{id}")
    public ResponseEntity<AgentCardDto> update(
            @PathVariable Long id,
            @Valid @RequestBody AgentUpsertRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.update(id, request, user));
    }

    @Operation(summary = "删除智能体", description = "按 ID 删除智能体实例")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "设为默认智能体", description = "将指定智能体设为默认，配置自动同步到运行时")
    @PostMapping("/{id}/default")
    public ResponseEntity<AgentCardDto> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(service.setDefault(id));
    }

    @Operation(summary = "复制智能体", description = "复制指定智能体为新的智能体实例")
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<AgentCardDto> duplicate(@PathVariable Long id) {
        return ResponseEntity.ok(service.duplicate(id));
    }

    @Operation(summary = "获取智能体元数据", description = "获取分类、图标、状态等前端枚举选项")
    @GetMapping("/_meta")
    public ResponseEntity<Map<String, Object>> meta() {
        return ResponseEntity.ok(Map.of(
                "categories", List.of(
                        Map.of("value", "chat", "label", "对话型", "tone", "secondary"),
                        Map.of("value", "workflow", "label", "工作流", "tone", "warning"),
                        Map.of("value", "support", "label", "客服型", "tone", "success")
                ),
                "icons", List.of("MessageSquare", "Calculator", "FileSearch", "BarChart3", "Mic"),
                "statuses", List.of("draft", "published")
        ));
    }
}
