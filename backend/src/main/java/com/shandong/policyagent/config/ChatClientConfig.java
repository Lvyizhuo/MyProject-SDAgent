package com.shandong.policyagent.config;

import com.shandong.policyagent.advisor.LoggingAdvisor;
import com.shandong.policyagent.advisor.RedisChatMemory;
import com.shandong.policyagent.advisor.ReReadingAdvisor;
import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.rag.RagConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@Configuration
public class ChatClientConfig {

    /**
     * 系统提示词现已移至数据库（agent_config.system_prompt），由 DynamicAgentConfigHolder 持有。
     * ChatService 每次请求时通过 .system(dynamicHolder.getSystemPrompt()) 动态注入。
     * 此常量仅作为首次初始化的兜底默认值（由 DefaultAgentConfigLoader 使用）。
     */
    // SYSTEM_PROMPT 已迁移，不再在此硬编码

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate redisTemplate) {
        return RedisChatMemory.builder()
                .redisTemplate(redisTemplate)
                .ttlDays(7)
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
    public ChatClient chatClient(ChatClient.Builder builder, 
                                  VectorStore vectorStore,
                                  RagConfig ragConfig,
                                  ChatMemory chatMemory,
                                  SecurityAdvisor securityAdvisor,
                                  ReReadingAdvisor reReadingAdvisor,
                                  LoggingAdvisor loggingAdvisor,
                                  List<ToolCallbackProvider> toolCallbackProviders) {
        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();

        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(retrievalConfig.getTopK())
                        .similarityThreshold(retrievalConfig.getSimilarityThreshold())
                        .build())
                .build();

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        // System Prompt 已迁移到数据库，由 ChatService 每次请求时动态注入 .system(...)
        return builder
                .defaultAdvisors(
                        securityAdvisor,
                        memoryAdvisor,
                        reReadingAdvisor,
                        qaAdvisor,
                        loggingAdvisor
                )
                .defaultToolCallbacks(toolCallbackProviders.toArray(ToolCallbackProvider[]::new))
                .build();
    }
}
