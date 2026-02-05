package com.shandong.policyagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class LoggingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String NAME = "LoggingAdvisor";
    private static final int DEFAULT_ORDER = 90;

    private static final String RETRIEVED_DOCUMENTS = "qa_retrieved_documents";
    private static final String CONVERSATION_ID_KEY = "chat_memory_conversation_id";

    private final int order;
    private final boolean logTokenUsage;
    private final boolean logRetrievedDocs;

    public LoggingAdvisor() {
        this(DEFAULT_ORDER, true, true);
    }

    public LoggingAdvisor(int order, boolean logTokenUsage, boolean logRetrievedDocs) {
        this.order = order;
        this.logTokenUsage = logTokenUsage;
        this.logRetrievedDocs = logRetrievedDocs;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Instant startTime = Instant.now();
        String userMessage = truncateMessage(extractUserText(request), 100);
        String conversationId = extractConversationId(request);

        log.info("[Chat] 开始处理请求 | conversationId={} | message={}", conversationId, userMessage);

        try {
            ChatClientResponse response = chain.nextCall(request);
            
            Duration duration = Duration.between(startTime, Instant.now());
            logResponse(response, duration, conversationId);
            
            return response;
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            log.error("[Chat] 请求处理失败 | conversationId={} | duration={}ms | error={}",
                    conversationId, duration.toMillis(), e.getMessage());
            throw e;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Instant startTime = Instant.now();
        String userMessage = truncateMessage(extractUserText(request), 100);
        String conversationId = extractConversationId(request);
        AtomicLong tokenCount = new AtomicLong(0);

        log.info("[Stream] 开始处理请求 | conversationId={} | message={}", conversationId, userMessage);

        Flux<ChatClientResponse> responseFlux = chain.nextStream(request);

        return new ChatClientMessageAggregator().aggregateChatClientResponse(responseFlux, response -> {
            Duration duration = Duration.between(startTime, Instant.now());
            log.info("[Stream] 请求处理完成 | conversationId={} | duration={}ms",
                    conversationId, duration.toMillis());
            
            if (logRetrievedDocs) {
                logRetrievedDocuments(response, conversationId);
            }
            
            if (logTokenUsage) {
                logTokenUsageFromResponse(response, conversationId);
            }
        });
    }

    private void logResponse(ChatClientResponse response, Duration duration, String conversationId) {
        ChatResponse chatResponse = response.chatResponse();
        
        if (chatResponse == null) {
            log.warn("[Chat] 响应为空 | conversationId={} | duration={}ms", conversationId, duration.toMillis());
            return;
        }

        if (logTokenUsage && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
            var usage = chatResponse.getMetadata().getUsage();
            log.info("[Chat] Token使用 | conversationId={} | promptTokens={} | completionTokens={} | totalTokens={}",
                    conversationId,
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
        }

        log.info("[Chat] 请求处理完成 | conversationId={} | duration={}ms", conversationId, duration.toMillis());

        if (logRetrievedDocs) {
            logRetrievedDocuments(response, conversationId);
        }
    }

    private void logTokenUsageFromResponse(ChatClientResponse response, String conversationId) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
            var usage = chatResponse.getMetadata().getUsage();
            log.info("[Stream] Token使用 | conversationId={} | promptTokens={} | completionTokens={} | totalTokens={}",
                    conversationId,
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
        }
    }

    @SuppressWarnings("unchecked")
    private void logRetrievedDocuments(ChatClientResponse response, String conversationId) {
        Object retrievedDocs = response.context().get(RETRIEVED_DOCUMENTS);
        if (retrievedDocs instanceof List<?> docs && !docs.isEmpty()) {
            log.info("[RAG] 检索到 {} 个相关文档 | conversationId={}", docs.size(), conversationId);
            
            for (int i = 0; i < docs.size(); i++) {
                if (docs.get(i) instanceof Document doc) {
                    String source = doc.getMetadata().getOrDefault("source", "unknown").toString();
                    String title = doc.getMetadata().getOrDefault("title", "").toString();
                    double score = 0.0;
                    Object scoreObj = doc.getMetadata().get("score");
                    if (scoreObj instanceof Number) {
                        score = ((Number) scoreObj).doubleValue();
                    }
                    
                    log.info("[RAG] 文档[{}] | source={} | title={} | score={}", 
                            i + 1, source, title, String.format("%.4f", score));
                }
            }
        }
    }

    private String extractConversationId(ChatClientRequest request) {
        Object conversationId = request.context().get(CONVERSATION_ID_KEY);
        return conversationId != null ? conversationId.toString() : "anonymous";
    }

    private String extractUserText(ChatClientRequest request) {
        if (request.prompt() != null && request.prompt().getUserMessage() != null) {
            return request.prompt().getUserMessage().getText();
        }
        return "";
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "";
        }
        return message.length() > maxLength ? message.substring(0, maxLength) + "..." : message;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int order = DEFAULT_ORDER;
        private boolean logTokenUsage = true;
        private boolean logRetrievedDocs = true;

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder logTokenUsage(boolean logTokenUsage) {
            this.logTokenUsage = logTokenUsage;
            return this;
        }

        public Builder logRetrievedDocs(boolean logRetrievedDocs) {
            this.logRetrievedDocs = logRetrievedDocs;
            return this;
        }

        public LoggingAdvisor build() {
            return new LoggingAdvisor(order, logTokenUsage, logRetrievedDocs);
        }
    }
}
