package com.shandong.policyagent.service;

import com.shandong.policyagent.model.ChatRequest;
import com.shandong.policyagent.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 对话服务
 * 
 * Spring AI 1.0.0-M6 流式模式 + 工具调用存在已知问题 (toolInput cannot be null or empty)。
 * 解决方案：流式接口检测到工具调用失败时，自动降级为非流式调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String CHAT_MEMORY_CONVERSATION_ID = "chat_memory_conversation_id";
    private static final int STREAM_CHUNK_SIZE = 15;
    
    private final ChatClient chatClient;

    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String conversationId = getOrCreateConversationId(request.getConversationId());
        
        String response = chatClient.prompt()
                .user(request.getMessage())
                .advisors(advisorSpec -> advisorSpec
                        .param(CHAT_MEMORY_CONVERSATION_ID, conversationId))
                .call()
                .content();
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("对话完成 | conversationId={} | 耗时={}ms", conversationId, duration);
        
        return ChatResponse.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .content(response)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public Flux<String> chatStream(ChatRequest request) {
        String conversationId = getOrCreateConversationId(request.getConversationId());
        
        log.info("开始流式对话 | conversationId={}", conversationId);
        
        return chatClient.prompt()
                .user(request.getMessage())
                .advisors(advisorSpec -> advisorSpec
                        .param(CHAT_MEMORY_CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .onErrorResume(e -> {
                    if (isToolCallError(e)) {
                        log.warn("流式工具调用失败，降级为非流式调用 | conversationId={} | error={}", 
                                conversationId, e.getMessage());
                        return fallbackToNonStreaming(request, conversationId);
                    }
                    return Flux.error(e);
                });
    }
    
    private boolean isToolCallError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return hasToolErrorInCauseChain(e.getCause());
        }
        return isToolRelatedError(message);
    }
    
    private boolean hasToolErrorInCauseChain(Throwable cause) {
        while (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && isToolRelatedError(causeMessage)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
    
    private boolean isToolRelatedError(String message) {
        return message.contains("toolInput cannot be null or empty") ||
               message.contains("toolName cannot be null or empty") ||
               (message.contains("tool") && message.contains("null")) ||
               message.contains("Stream processing failed");
    }
    
    private Flux<String> fallbackToNonStreaming(ChatRequest request, String conversationId) {
        return Mono.fromCallable(() -> {
            log.info("执行非流式降级调用 | conversationId={}", conversationId);
            long startTime = System.currentTimeMillis();
            
            String response = chatClient.prompt()
                    .user(request.getMessage())
                    .advisors(advisorSpec -> advisorSpec
                            .param(CHAT_MEMORY_CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("非流式降级调用完成 | conversationId={} | 耗时={}ms", conversationId, duration);
            
            return response;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(this::simulateStreamOutput);
    }
    
    private Flux<String> simulateStreamOutput(String response) {
        if (response == null || response.isEmpty()) {
            return Flux.empty();
        }
        
        return Flux.create(sink -> {
            int index = 0;
            while (index < response.length()) {
                int end = Math.min(index + STREAM_CHUNK_SIZE, response.length());
                sink.next(response.substring(index, end));
                index = end;
            }
            sink.complete();
        });
    }

    private String getOrCreateConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return conversationId;
    }
}
