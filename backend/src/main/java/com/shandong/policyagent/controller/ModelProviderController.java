package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.ModelProviderRequest;
import com.shandong.policyagent.model.ModelProviderResponse;
import com.shandong.policyagent.service.ModelProviderService;
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
public class ModelProviderController {

    private final ModelProviderService modelProviderService;

    /**
     * 获取模型列表（支持按类型过滤）
     */
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
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ModelProviderResponse> getModelById(@PathVariable Long id) {
        log.info("获取模型详情, id={}", id);
        return ResponseEntity.ok(modelProviderService.getModelById(id));
    }

    /**
     * 新增模型
     */
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
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        log.info("测试模型连接, id={}", id);
        return ResponseEntity.ok(modelProviderService.testConnection(id));
    }

    /**
     * 获取模型选项列表（用于下拉选择）
     */
    @GetMapping("/options")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, List<ModelProviderService.ModelOption>>> getModelOptions() {
        log.info("获取模型选项列表");
        return ResponseEntity.ok(modelProviderService.getModelOptions());
    }
}
