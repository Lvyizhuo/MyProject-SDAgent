package com.shandong.policyagent.service;

import com.shandong.policyagent.advisor.LoggingAdvisor;
import com.shandong.policyagent.advisor.ReReadingAdvisor;
import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.advisor.TimingAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 ChatClient 工厂。
 *
 * <p>配置解析已下沉到 {@link RuntimeChatModelProvider}（同时被 RedisChatMemory /
 * ReActPlanningService 复用，保证三处用的是同一套运行时模型配置）。
 *
 * <p><b>Client 复用</b>：ChatClient 按 (运行时模型配置, 是否启用工具, 是否启用 RAG) 缓存。
 * 原实现每次请求都 new 一个 {@code OpenAiApi}，意味着每次都要重新建连池 + TLS 握手
 * （实测约 40ms），高并发下还会放大文件描述符压力。
 */
@Slf4j
@Component
public class DynamicChatClientFactory {

    private final RuntimeChatModelProvider runtimeChatModelProvider;
    private final SecurityAdvisor securityAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final ReReadingAdvisor reReadingAdvisor;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final LoggingAdvisor loggingAdvisor;
    private final TimingAdvisor timingAdvisor;
    private final List<ToolCallbackProvider> toolCallbackProviders;

    /** ChatClient 缓存：key = 模型配置指纹 + 工具开关 + RAG 开关。 */
    private final ConcurrentHashMap<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 容器使用：MCP 彻底关闭（spring.ai.mcp.client.enabled=false）时无 ToolCallbackProvider 候选，
     * List 集合注入零候选会启动失败（NoSuchBeanDefinitionException），
     * 故以 ObjectProvider 注入并 getIfAvailable(List::of) 空集兜底。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public DynamicChatClientFactory(RuntimeChatModelProvider runtimeChatModelProvider,
                                    SecurityAdvisor securityAdvisor,
                                    MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                                    ReReadingAdvisor reReadingAdvisor,
                                    QuestionAnswerAdvisor questionAnswerAdvisor,
                                    LoggingAdvisor loggingAdvisor,
                                    TimingAdvisor timingAdvisor,
                                    ObjectProvider<List<ToolCallbackProvider>> toolCallbackProviders) {
        this(runtimeChatModelProvider, securityAdvisor, messageChatMemoryAdvisor, reReadingAdvisor,
                questionAnswerAdvisor, loggingAdvisor, timingAdvisor,
                toolCallbackProviders.getIfAvailable(List::of));
    }

    public DynamicChatClientFactory(RuntimeChatModelProvider runtimeChatModelProvider,
                                    SecurityAdvisor securityAdvisor,
                                    MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                                    ReReadingAdvisor reReadingAdvisor,
                                    QuestionAnswerAdvisor questionAnswerAdvisor,
                                    LoggingAdvisor loggingAdvisor,
                                    TimingAdvisor timingAdvisor,
                                    List<ToolCallbackProvider> toolCallbackProviders) {
        this.runtimeChatModelProvider = runtimeChatModelProvider;
        this.securityAdvisor = securityAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.reReadingAdvisor = reReadingAdvisor;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.loggingAdvisor = loggingAdvisor;
        this.timingAdvisor = timingAdvisor;
        this.toolCallbackProviders = toolCallbackProviders;
    }

    public ChatClient create() {
        return create(true, true);
    }

    public ChatClient create(boolean enableTools) {
        return create(enableTools, true);
    }

    public ChatClient create(boolean enableTools, boolean enableRag) {
        RuntimeChatModelProvider.ResolvedChatModelConfig runtimeConfig =
                runtimeChatModelProvider.resolveRuntimeConfig();
        String cacheKey = cacheKey(runtimeConfig, enableTools, enableRag);
        return clientCache.computeIfAbsent(cacheKey, key -> buildClient(runtimeConfig, enableTools, enableRag));
    }

    private ChatClient buildClient(RuntimeChatModelProvider.ResolvedChatModelConfig runtimeConfig,
                                   boolean enableTools,
                                   boolean enableRag) {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(securityAdvisor);
        advisors.add(messageChatMemoryAdvisor);
        advisors.add(reReadingAdvisor);
        if (enableRag) {
            advisors.add(questionAnswerAdvisor);
        }
        // 计时 Advisor 放在最后一位（order 85 < LoggingAdvisor 的 90），
        // 用于把「RAG/记忆等前置开销」与「模型调用本身」分开计量
        advisors.add(timingAdvisor);
        advisors.add(loggingAdvisor);

        ChatClient.Builder builder = ChatClient.builder(runtimeChatModelProvider.buildChatModel(runtimeConfig))
                .defaultAdvisors(advisors.toArray(Advisor[]::new));

        if (enableTools) {
            builder.defaultToolCallbacks(toolCallbackProviders.toArray(ToolCallbackProvider[]::new));
            log.info("ChatClient 已装配工具回调 | providers={} | tools={}",
                    toolCallbackProviders.size(),
                    toolCallbackProviders.stream()
                            .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                            .map(callback -> callback.getToolDefinition().name())
                            .toList());
        }

        return builder.build();
    }

    private String cacheKey(RuntimeChatModelProvider.ResolvedChatModelConfig config,
                            boolean enableTools,
                            boolean enableRag) {
        return String.join("|",
                String.valueOf(config.baseUrl()),
                String.valueOf(config.modelName()),
                String.valueOf(config.temperature()),
                String.valueOf(config.maxTokens()),
                String.valueOf(config.topP()),
                "tools=" + enableTools,
                "rag=" + enableRag);
    }

    /**
     * 清空 Client 缓存。管理员修改模型配置后必须调用，否则旧配置的实例会一直被复用。
     */
    public void invalidateCache() {
        clientCache.clear();
        runtimeChatModelProvider.invalidateCache();
    }

    // 模型侧工具调用失败的兜底 processor 已迁移至 config/ToolFailureConfig：
    // 若定义在本类（构造注入 OpenAiChatModel），会形成
    // openAiChatModel → toolCallingManager → processor → 本类 → openAiChatModel 的循环依赖。
}
