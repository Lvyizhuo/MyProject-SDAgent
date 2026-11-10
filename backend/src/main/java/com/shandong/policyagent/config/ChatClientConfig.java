package com.shandong.policyagent.config;

import com.shandong.policyagent.advisor.LoggingAdvisor;
import com.shandong.policyagent.advisor.RedisChatMemory;
import com.shandong.policyagent.advisor.ReReadingAdvisor;
import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.advisor.TimingAdvisor;
import com.shandong.policyagent.rag.RagConfig;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
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
                                                                 com.shandong.policyagent.service.RuntimeChatModelProvider runtimeChatModelProvider,
                                                                 @Qualifier("chatAsyncTaskExecutor") Executor chatAsyncTaskExecutor,
                                                                 @Value("${app.advisor.memory.max-messages:6}") int maxMessages,
                                                                 @Value("${app.advisor.memory.max-message-chars:1000}") int maxMessageChars,
                                                                 @Value("${app.advisor.memory.summary-enabled:true}") boolean summaryEnabled,
                                                                 @Value("${app.advisor.memory.summary-trigger-messages:6}") int summaryTriggerMessages,
                                                                 @Value("${app.advisor.memory.summary-keep-messages:2}") int summaryKeepMessages,
                                                                 @Value("${app.advisor.memory.summary-max-chars:1200}") int summaryMaxChars,
                                                                 @Value("${app.advisor.memory.summary-timeout-seconds:8}") int summaryTimeoutSeconds) {
        return RedisChatMemory.builder()
                .redisTemplate(redisTemplate)
                .ttlDays(7)
                .maxMessages(maxMessages)
                .maxMessageChars(maxMessageChars)
                .summaryEnabled(summaryEnabled)
                .summaryTriggerMessages(summaryTriggerMessages)
                .summaryKeepMessages(summaryKeepMessages)
                .summaryMaxChars(summaryMaxChars)
                .summaryTimeoutSeconds(summaryTimeoutSeconds)
                // 摘要使用运行时模型：管理员在管理台改模型后，摘要不会继续用失效的默认 Key
                .chatModelSupplier(runtimeChatModelProvider::buildChatModel)
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
    public LoggingAdvisor loggingAdvisor(com.shandong.policyagent.support.ChatMetrics chatMetrics) {
        return LoggingAdvisor.builder()
                .order(90)
                .logTokenUsage(true)
                .logRetrievedDocs(true)
                .chatMetrics(chatMetrics)
                .build();
    }

    /**
     * 链路计时 Advisor：order 85（早于 LoggingAdvisor 的 90，晚于 RAG 检索），
     * 用于把「RAG/记忆等前置开销」与「模型调用本身」分开计量。
     */
    @Bean
    public TimingAdvisor timingAdvisor() {
        return new TimingAdvisor(85);
    }

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(
            @Qualifier("runtimeRagVectorStore") VectorStore vectorStore,
            RagConfig ragConfig) {
        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();
        return QuestionAnswerAdvisor.builder(vectorStore)
                // 显式指定 order：此前未设置，实际执行顺序依赖 Spring AI 默认值，
                // 存在 RAG 早于会话记忆加载的风险。order=60 保证在记忆(20)之后、计时(85)之前。
                .order(60)
                .searchRequest(SearchRequest.builder()
                        .topK(retrievalConfig.getTopK())
                        .similarityThreshold(retrievalConfig.getSimilarityThreshold())
                        .build())
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .order(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 SecurityAdvisor securityAdvisor,
                                 MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                                 ReReadingAdvisor reReadingAdvisor,
                                 QuestionAnswerAdvisor questionAnswerAdvisor,
                                 LoggingAdvisor loggingAdvisor,
                                 TimingAdvisor timingAdvisor,
                                 ObjectProvider<List<ToolCallbackProvider>> toolCallbackProviders) {
        return builder
                .defaultAdvisors(
                        securityAdvisor,
                        messageChatMemoryAdvisor,
                        reReadingAdvisor,
                        questionAnswerAdvisor,
                        timingAdvisor,
                        loggingAdvisor
                )
                // MCP 彻底关闭时无 ToolCallbackProvider 候选，getIfAvailable 以空集兜底避免启动失败
                .defaultToolCallbacks(toolCallbackProviders.getIfAvailable(List::of)
                        .toArray(ToolCallbackProvider[]::new))
                .build();
    }
}
