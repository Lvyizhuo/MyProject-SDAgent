package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.AgentCardDto;
import com.shandong.policyagent.model.dto.AgentUpsertRequest;
import com.shandong.policyagent.service.AgentInstanceService;
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
@CrossOrigin(origins = "*")
public class AgentInstanceController {

    private final AgentInstanceService service;

    @GetMapping
    public ResponseEntity<List<AgentCardDto>> list() {
        return ResponseEntity.ok(service.listCards());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentCardDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCard(id));
    }

    @PostMapping
    public ResponseEntity<AgentCardDto> create(
            @Valid @RequestBody AgentUpsertRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.create(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentCardDto> update(
            @PathVariable Long id,
            @Valid @RequestBody AgentUpsertRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<AgentCardDto> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(service.setDefault(id));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<AgentCardDto> duplicate(@PathVariable Long id) {
        return ResponseEntity.ok(service.duplicate(id));
    }

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
