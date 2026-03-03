package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.ChatRequest;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.model.admin.AgentConfigTestRequest;
import com.shandong.policyagent.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 智能体配置测试接口
 *
 * 允许管理员在修改配置后，通过发送测试消息验证当前运行时配置是否正常工作。
 * 使用与正式聊天相同的 ChatService，确保测试结果准确反映实际行为。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/agent-config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentConfigTestController {

    private final ChatService chatService;

    /**
     * 测试当前智能体配置
     *
     * POST /api/admin/agent-config/test
     *
     * @param request 测试请求（message 必填，sessionId 可选）
     * @param user    当前认证的管理员用户
     * @return 与正式对话相同格式的 ChatResponse
     */
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatResponse> testConfig(
            @Valid @RequestBody AgentConfigTestRequest request,
            @AuthenticationPrincipal User user) {

        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : "admin-test-" + UUID.randomUUID();

        log.info("管理员测试配置 | admin={} | sessionId={} | message={}",
                user.getUsername(), sessionId, request.getMessage());

        ChatRequest chatRequest = ChatRequest.builder()
                .conversationId(sessionId)
                .message(request.getMessage())
                .build();

        ChatResponse response = chatService.chat(chatRequest);
        return ResponseEntity.ok(response);
    }
}
