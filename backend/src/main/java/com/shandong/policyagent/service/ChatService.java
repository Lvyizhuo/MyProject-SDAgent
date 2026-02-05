package com.shandong.policyagent.service;

import com.shandong.policyagent.model.ChatRequest;
import com.shandong.policyagent.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 对话服务
 * 封装与 Spring AI ChatClient 的交互逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;

    /**
     * 标准对话 - 返回完整响应
     */
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        
        String response = chatClient.prompt()
                .user(request.getMessage())
                .call()
                .content();
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("对话完成, 耗时: {}ms", duration);
        
        return ChatResponse.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(request.getConversationId())
                .content(response)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 流式对话 - SSE 推送
     */
    public Flux<String> chatStream(ChatRequest request) {
        return chatClient.prompt()
                .user(request.getMessage())
                .stream()
                .content();
    }
}
