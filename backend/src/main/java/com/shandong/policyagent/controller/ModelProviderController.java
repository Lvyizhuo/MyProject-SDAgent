package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.ModelProviderRequest;
import com.shandong.policyagent.model.ModelProviderResponse;
import com.shandong.policyagent.service.ModelProviderService;
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
@RequestMapping("/api/admin/models")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "模型管理", description = "模型提供者的增删改查、默认设置与连接测试")
public class ModelProviderController {

    private final ModelProviderService modelProviderService;

    /**
     * 获取模型列表（支持按类型过滤）
     */
    @Operation(summary = "获取模型列表", description = "获取模型提供者列表，可按模型类型过滤")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ModelProviderResponse>> getModels(
            @RequestParam(required = false) ModelType type) {
        log.info("获取模型列表, type={}", type);
        return ResponseEntity.ok(modelProviderService.getAllModels(type));
    }

    /**
     * 获取单个模型详情
     */
    @Operation(summary = "获取模型详情", description = "按 ID 获取模型提供者详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ModelProviderResponse> getModelById(@PathVariable Long id) {
        log.info("获取模型详情, id={}", id);
        return ResponseEntity.ok(modelProviderService.getModelById(id));
    }

    /**
     * 新增模型
     */
    @Operation(summary = "新增模型", description = "创建新的模型提供者，API Key 加密存储")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ModelProviderResponse> createModel(
            @Valid @RequestBody ModelProviderRequest request,
            @AuthenticationPrincipal User user) {
        log.info("管理员创建模型: {}, type={}", user.getUsername(), request.getType());
        return ResponseEntity.ok(modelProviderService.createModel(request, user.getId()));
    }

    /**
     * 更新模型
     */
    @Operation(summary = "更新模型", description = "按 ID 更新模型提供者配置")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ModelProviderResponse> updateModel(
            @PathVariable Long id,
            @Valid @RequestBody ModelProviderRequest request,
            @AuthenticationPrincipal User user) {
        log.info("管理员更新模型: {}, id={}", user.getUsername(), id);
        return ResponseEntity.ok(modelProviderService.updateModel(id, request));
    }

    /**
     * 删除模型
     */
    @Operation(summary = "删除模型", description = "按 ID 删除模型提供者")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteModel(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        log.info("管理员删除模型: {}, id={}", user.getUsername(), id);
        modelProviderService.deleteModel(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 设为默认模型
     */
    @Operation(summary = "设为默认模型", description = "将指定模型设为该类型的默认模型")
    @PutMapping("/{id}/set-default")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ModelProviderResponse> setDefault(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        log.info("管理员设置默认模型: {}, id={}", user.getUsername(), id);
        return ResponseEntity.ok(modelProviderService.setDefault(id));
    }

    /**
     * 测试模型连接
     */
    @Operation(summary = "测试模型连接", description = "对指定模型执行连通性与 API Key 有效性测试")
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        log.info("测试模型连接, id={}", id);
        return ResponseEntity.ok(modelProviderService.testConnection(id));
    }

    /**
     * 获取模型选项列表（用于下拉选择）
     */
    @Operation(summary = "获取模型选项列表", description = "获取按类型分组的模型选项，用于前端下拉选择")
    @GetMapping("/options")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, List<ModelProviderService.ModelOption>>> getModelOptions() {
        log.info("获取模型选项列表");
        return ResponseEntity.ok(modelProviderService.getModelOptions());
    }
}
