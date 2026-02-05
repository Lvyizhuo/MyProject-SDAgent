package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.ChatMessage;
import com.shandong.policyagent.model.ChatRequest;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.service.ChatService;
import com.shandong.policyagent.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 对话控制器
 * 提供标准对话和流式对话两种接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;

    /**
     * 标准对话接口 - 返回完整响应
     */
    @PostMapping
    public ChatResponse chat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequest request) {
        Long userId = user != null ? user.getId() : null;
        log.info("收到对话请求: userId={}, conversationId={}, message={}", 
                userId, request.getConversationId(), request.getMessage());
        
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            request.setConversationId(conversationId);
        }
        conversationService.getOrCreateSession(userId, conversationId);
        
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("user")
                .content(request.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
        conversationService.addMessage(conversationId, userMessage);
        
        ChatResponse response = chatService.chat(request);
        
        ChatMessage assistantMessage = ChatMessage.builder()
                .id(response.getId())
                .role("assistant")
                .content(response.getContent())
                .timestamp(System.currentTimeMillis())
                .build();
        conversationService.addMessage(conversationId, assistantMessage);
        
        return response;
    }

    /**
     * 流式对话接口 - SSE 推送
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequest request) {
        Long userId = user != null ? user.getId() : null;
        log.info("收到流式对话请求: userId={}, conversationId={}, message={}", 
                userId, request.getConversationId(), request.getMessage());
        
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            request.setConversationId(conversationId);
        }
        conversationService.getOrCreateSession(userId, conversationId);
        
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("user")
                .content(request.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
        conversationService.addMessage(conversationId, userMessage);
        
        final String finalConversationId = conversationId;
        StringBuilder responseContent = new StringBuilder();
        
        return chatService.chatStream(request)
                .doOnNext(chunk -> responseContent.append(chunk))
                .doOnComplete(() -> {
                    ChatMessage assistantMessage = ChatMessage.builder()
                            .id(UUID.randomUUID().toString())
                            .role("assistant")
                            .content(responseContent.toString())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    conversationService.addMessage(finalConversationId, assistantMessage);
                    log.info("流式响应完成并保存: conversationId={}", finalConversationId);
                });
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
