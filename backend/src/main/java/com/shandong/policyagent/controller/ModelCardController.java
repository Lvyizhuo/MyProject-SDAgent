package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.model.dto.ModelCardDto;
import com.shandong.policyagent.service.ModelCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/models-card")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "模型卡片视图", description = "模型卡片视图（只读 + 默认设置 + 连接测试）")
public class ModelCardController {

    private final ModelCardService service;

    @Operation(summary = "获取模型卡片列表", description = "按模型类型筛选获取模型卡片列表（可空）")
    @GetMapping
    public ResponseEntity<List<ModelCardDto>> list(
            @RequestParam(required = false) ModelType modelType) {
        return ResponseEntity.ok(service.listCards(modelType));
    }

    @Operation(summary = "获取模型卡片详情", description = "按 ID 获取模型卡片详情")
    @GetMapping("/{id}")
    public ResponseEntity<ModelCardDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCard(id));
    }

    @Operation(summary = "设为默认模型", description = "将指定模型设为该类型的默认模型")
    @PostMapping("/{id}/default")
    public ResponseEntity<ModelCardDto> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(service.setDefault(id));
    }

    @Operation(summary = "测试模型连接", description = "对指定模型执行连接/密钥有效性测试")
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        return ResponseEntity.ok(service.testConnection(id));
    }
}
