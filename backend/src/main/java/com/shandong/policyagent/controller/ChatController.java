package com.shandong.policyagent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.ChatMessage;
import com.shandong.policyagent.model.ChatRequest;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.model.ChatStreamEvent;
import com.shandong.policyagent.service.ChatService;
import com.shandong.policyagent.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final int MAX_IMAGE_COUNT = 3;
    private static final int MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_IMAGE_FORMATS = Set.of("jpeg", "jpg", "png", "webp");

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    /**
     * 标准对话接口 - 返回完整响应
     */
    @PostMapping
    public ChatResponse chat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequest request) {
        Long userId = user != null ? user.getId() : null;
        log.info("收到对话请求: userId={}, conversationId={}, message={}, hasImages={}", 
                userId, request.getConversationId(), request.getMessage(), request.hasImages());
        
        validateImageRequest(request);
        
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
                .references(response.getReferences())
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
        log.info("收到流式对话请求: userId={}, conversationId={}, message={}, hasImages={}", 
                userId, request.getConversationId(), request.getMessage(), request.hasImages());
        
        validateImageRequest(request);
        
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
        AtomicReference<List<ChatResponse.Reference>> responseReferences = new AtomicReference<>(List.of());

        return chatService.chatStream(request)
                .doOnNext(event -> {
                    if ("delta".equals(event.getType()) && event.getContent() != null) {
                        responseContent.append(event.getContent());
                    }
                    if ("references".equals(event.getType()) && event.getReferences() != null) {
                        responseReferences.set(event.getReferences());
                    }
                })
                .map(this::serializeStreamEvent)
                .doOnComplete(() -> {
                    ChatMessage assistantMessage = ChatMessage.builder()
                            .id(UUID.randomUUID().toString())
                            .role("assistant")
                            .content(responseContent.toString())
                            .references(responseReferences.get())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    conversationService.addMessage(finalConversationId, assistantMessage);
                    log.info("流式响应完成并保存: conversationId={}", finalConversationId);
                });
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    private void validateImageRequest(ChatRequest request) {
        if (!request.hasImages()) {
            return;
        }

        List<String> images = request.getImageBase64List();

        if (images.size() > MAX_IMAGE_COUNT) {
            throw new IllegalArgumentException(
                    String.format("图片数量不能超过%d张，当前%d张", MAX_IMAGE_COUNT, images.size()));
        }

        String format = request.getImageFormat() != null ? request.getImageFormat().toLowerCase() : "jpeg";
        if (!ALLOWED_IMAGE_FORMATS.contains(format)) {
            throw new IllegalArgumentException(
                    String.format("不支持的图片格式: %s，支持格式: %s", format, ALLOWED_IMAGE_FORMATS));
        }

        for (int i = 0; i < images.size(); i++) {
            String base64 = images.get(i);
            if (base64 == null || base64.isBlank()) {
                throw new IllegalArgumentException("第" + (i + 1) + "张图片数据为空");
            }

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("第" + (i + 1) + "张图片Base64格式无效");
            }

            if (decoded.length > MAX_IMAGE_SIZE_BYTES) {
                throw new IllegalArgumentException(
                        String.format("第%d张图片大小%.1fMB超过限制%dMB",
                                i + 1, decoded.length / (1024.0 * 1024.0), MAX_IMAGE_SIZE_BYTES / (1024 * 1024)));
            }

            validateImageMagicBytes(decoded, i + 1);
        }
    }

    private void validateImageMagicBytes(byte[] data, int imageIndex) {
        if (data.length < 4) {
            throw new IllegalArgumentException("第" + imageIndex + "张图片数据过小，不是有效图片");
        }

        boolean isJpeg = data[0] == (byte) 0xFF && data[1] == (byte) 0xD8;
        boolean isPng = data[0] == (byte) 0x89 && data[1] == (byte) 0x50
                && data[2] == (byte) 0x4E && data[3] == (byte) 0x47;
        boolean isWebp = data.length >= 12
                && data[0] == (byte) 0x52 && data[1] == (byte) 0x49
                && data[2] == (byte) 0x46 && data[3] == (byte) 0x46
                && data[8] == (byte) 0x57 && data[9] == (byte) 0x45
                && data[10] == (byte) 0x42 && data[11] == (byte) 0x50;

        if (!isJpeg && !isPng && !isWebp) {
            throw new IllegalArgumentException("第" + imageIndex + "张图片格式无效，仅支持JPEG/PNG/WebP");
        }
    }

    private String serializeStreamEvent(ChatStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化流式响应失败", exception);
        }
    }
}
