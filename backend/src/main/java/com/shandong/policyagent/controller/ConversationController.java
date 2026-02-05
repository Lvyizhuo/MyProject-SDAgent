package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.ChatSession;
import com.shandong.policyagent.service.ConversationService;
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
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<List<ChatSession>> getUserSessions(@AuthenticationPrincipal User user) {
        log.info("获取用户会话列表: userId={}", user.getId());
        return ResponseEntity.ok(conversationService.getUserSessions(user.getId()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSession> getSession(
            @AuthenticationPrincipal User user,
            @PathVariable String sessionId
    ) {
        ChatSession session = conversationService.getOrCreateSession(user.getId(), sessionId);
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal User user,
            @PathVariable String sessionId
    ) {
        conversationService.deleteSession(user.getId(), sessionId);
        return ResponseEntity.ok().build();
    }
}
