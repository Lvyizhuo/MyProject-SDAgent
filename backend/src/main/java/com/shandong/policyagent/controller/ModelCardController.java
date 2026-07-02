package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.ModelType;
import com.shandong.policyagent.model.dto.ModelCardDto;
import com.shandong.policyagent.service.ModelCardService;
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
@CrossOrigin(origins = "*")
public class ModelCardController {

    private final ModelCardService service;

    @GetMapping
    public ResponseEntity<List<ModelCardDto>> list(
            @RequestParam(required = false) ModelType modelType) {
        return ResponseEntity.ok(service.listCards(modelType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelCardDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCard(id));
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<ModelCardDto> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(service.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        return ResponseEntity.ok(service.testConnection(id));
    }
}
