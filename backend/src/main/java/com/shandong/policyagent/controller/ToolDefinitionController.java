package com.shandong.policyagent.controller;

import com.shandong.policyagent.model.dto.ToolCardDto;
import com.shandong.policyagent.model.dto.ToolUpsertRequest;
import com.shandong.policyagent.model.dto.ToggleEnabledRequest;
import com.shandong.policyagent.service.ToolDefinitionService;
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
public class ToolDefinitionController {

    private final ToolDefinitionService service;

    @GetMapping
    public ResponseEntity<List<ToolCardDto>> list() {
        return ResponseEntity.ok(service.listCards());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolCardDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCard(id));
    }

    @PostMapping
    public ResponseEntity<ToolCardDto> create(@Valid @RequestBody ToolUpsertRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ToolCardDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ToolUpsertRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enabled")
    public ResponseEntity<ToolCardDto> setEnabled(
            @PathVariable Long id,
            @RequestBody ToggleEnabledRequest request) {
        return ResponseEntity.ok(service.setEnabled(id, Boolean.TRUE.equals(request.getEnabled())));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable Long id) {
        return ResponseEntity.ok(service.test(id));
    }
}
