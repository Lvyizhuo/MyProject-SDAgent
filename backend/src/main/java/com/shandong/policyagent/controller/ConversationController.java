package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.ChatSession;
import com.shandong.policyagent.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "会话管理", description = "用户对话会话的创建、查询与删除")
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "获取会话列表", description = "获取当前用户的所有对话会话")
    @GetMapping
    public ResponseEntity<List<ChatSession>> getUserSessions(@AuthenticationPrincipal User user) {
        log.info("获取用户会话列表: userId={}", user.getId());
        return ResponseEntity.ok(conversationService.getUserSessions(user.getId()));
    }

    @Operation(summary = "获取或创建会话", description = "按 sessionId 获取会话，不存在则自动创建")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSession> getSession(
            @AuthenticationPrincipal User user,
            @PathVariable String sessionId
    ) {
        ChatSession session = conversationService.getOrCreateSession(user.getId(), sessionId);
        return ResponseEntity.ok(session);
    }

    @Operation(summary = "删除会话", description = "删除指定 sessionId 的会话及其消息")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal User user,
            @PathVariable String sessionId
    ) {
        conversationService.deleteSession(user.getId(), sessionId);
        return ResponseEntity.ok().build();
    }
}
