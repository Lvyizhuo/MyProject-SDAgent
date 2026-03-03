package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.admin.AgentConfigRequest;
import com.shandong.policyagent.model.admin.AgentConfigResponse;
import com.shandong.policyagent.service.AgentConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/agent-config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentConfigController {

    private final AgentConfigService agentConfigService;

    /**
     * 获取当前配置（API Key 脱敏）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentConfigResponse> getConfig(@AuthenticationPrincipal User user) {
        log.info("管理员获取配置: {}", user.getUsername());
        return ResponseEntity.ok(agentConfigService.getCurrentConfig());
    }

    /**
     * 更新配置（调用同步服务）
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentConfigResponse> updateConfig(
            @Valid @RequestBody AgentConfigRequest request,
            @AuthenticationPrincipal User user) {

        log.info("管理员更新配置: {}", user.getUsername());
        AgentConfigResponse response = agentConfigService.updateConfig(request);

        // TODO: 调用配置同步服务
        // agentConfigSyncService.syncConfigToRuntime(config);

        return ResponseEntity.ok(response);
    }

    /**
     * 重置为默认配置
     */
    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentConfigResponse> resetConfig(@AuthenticationPrincipal User user) {
        log.info("管理员重置配置为默认: {}", user.getUsername());
        AgentConfigResponse response = agentConfigService.resetToDefault();

        // TODO: 调用配置同步服务
        // agentConfigSyncService.syncConfigToRuntime(config);

        return ResponseEntity.ok(response);
    }
}
