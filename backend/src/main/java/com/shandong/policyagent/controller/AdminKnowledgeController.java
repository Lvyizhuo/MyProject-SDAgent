package com.shandong.policyagent.controller;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.model.dto.*;
import com.shandong.policyagent.rag.EmbeddingService;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {

    private final KnowledgeFolderRepository folderRepository;
    private final KnowledgeConfigRepository configRepository;
    private final EmbeddingService embeddingService;

    @GetMapping("/folders")
    public ResponseEntity<Map<String, Object>> getFolderTree() {
        Map<String, Object> result = new HashMap<>();
        result.put("folders", folderRepository.findByParentIsNullOrderBySortOrderAsc());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    public ResponseEntity<KnowledgeConfig> getConfig() {
        return ResponseEntity.ok(configRepository.findById(1L).orElseGet(() -> configRepository.save(KnowledgeConfig.builder().build())));
    }

    @GetMapping("/embedding-models")
    public ResponseEntity<Map<String, Object>> getEmbeddingModels() {
        EmbeddingModelConfig.EmbeddingModel defaultModel = embeddingService.getDefaultModel();
        List<EmbeddingModelResponse> models = embeddingService.getAvailableModels().stream()
                .map(m -> EmbeddingModelResponse.builder()
                        .id(m.getId())
                        .name(m.getProvider() + " - " + m.getModelName())
                        .provider(m.getProvider())
                        .dimensions(m.getDimensions())
                        .isDefault(m.getId().equals(defaultModel.getId()))
                        .build())
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("models", models);
        return ResponseEntity.ok(result);
    }
}
