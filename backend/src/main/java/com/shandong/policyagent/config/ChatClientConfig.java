package com.shandong.policyagent.config;

import com.shandong.policyagent.advisor.LoggingAdvisor;
import com.shandong.policyagent.advisor.RedisChatMemory;
import com.shandong.policyagent.advisor.ReReadingAdvisor;
import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.rag.RagConfig;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.Executor;

@Configuration
public class ChatClientConfig {

    /**
     * 系统提示词现已移至数据库（agent_config.system_prompt），由 DynamicAgentConfigHolder 持有。
     * ChatService 每次请求时通过 .system(dynamicHolder.getSystemPrompt()) 动态注入。
     * 此常量仅作为首次初始化的兜底默认值（由 DefaultAgentConfigLoader 使用）。
     */
    // SYSTEM_PROMPT 已迁移，不再在此硬编码

    @Bean
        public ChatMemory chatMemory(StringRedisTemplate redisTemplate,
                                                                 ChatModel chatModel,
                                                                 @Qualifier("chatAsyncTaskExecutor") Executor chatAsyncTaskExecutor,
                                                                 @Value("${app.advisor.memory.summary-enabled:true}") boolean summaryEnabled,
                                                                 @Value("${app.advisor.memory.summary-trigger-messages:8}") int summaryTriggerMessages,
                                                                 @Value("${app.advisor.memory.summary-keep-messages:4}") int summaryKeepMessages,
                                                                 @Value("${app.advisor.memory.summary-max-chars:1200}") int summaryMaxChars,
                                                                 @Value("${app.advisor.memory.summary-timeout-seconds:8}") int summaryTimeoutSeconds) {
        return RedisChatMemory.builder()
                .redisTemplate(redisTemplate)
                .ttlDays(7)
                                .maxMessages(8)
                                .maxMessageChars(1500)
                                .summaryEnabled(summaryEnabled)
                                .summaryTriggerMessages(summaryTriggerMessages)
                                .summaryKeepMessages(summaryKeepMessages)
                                .summaryMaxChars(summaryMaxChars)
                                .summaryTimeoutSeconds(summaryTimeoutSeconds)
                                .chatModel(chatModel)
                                .summaryExecutor(chatAsyncTaskExecutor)
                .build();
    }

    @Bean
    public SecurityAdvisor securityAdvisor() {
        return SecurityAdvisor.builder()
                .order(10)
                .build();
    }

    @Bean
    public ReReadingAdvisor reReadingAdvisor() {
        return ReReadingAdvisor.builder()
                .order(50)
                .build();
    }

    @Bean
    public LoggingAdvisor loggingAdvisor() {
        return LoggingAdvisor.builder()
                .order(90)
                .logTokenUsage(true)
                .logRetrievedDocs(true)
                .build();
    }

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(
            @Qualifier("runtimeRagVectorStore") VectorStore vectorStore,
            RagConfig ragConfig) {
        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(retrievalConfig.getTopK())
                        .similarityThreshold(retrievalConfig.getSimilarityThreshold())
                        .build())
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 SecurityAdvisor securityAdvisor,
                                 MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                                 ReReadingAdvisor reReadingAdvisor,
                                 QuestionAnswerAdvisor questionAnswerAdvisor,
                                 LoggingAdvisor loggingAdvisor,
                                 List<ToolCallbackProvider> toolCallbackProviders) {
        return builder
                .defaultAdvisors(
                        securityAdvisor,
                        messageChatMemoryAdvisor,
                        reReadingAdvisor,
                        questionAnswerAdvisor,
                        loggingAdvisor
                )
                .defaultToolCallbacks(toolCallbackProviders.toArray(ToolCallbackProvider[]::new))
                .build();
    }
}
