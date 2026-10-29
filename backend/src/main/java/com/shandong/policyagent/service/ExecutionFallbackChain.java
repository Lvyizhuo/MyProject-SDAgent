package com.shandong.policyagent.service;

import com.shandong.policyagent.agent.DynamicAgentConfigHolder;
import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.rag.RagFailureDetector;
import com.shandong.policyagent.tool.SubsidyCalculatorTool;
import com.shandong.policyagent.tool.ToolFailurePolicyCenter;
import com.shandong.policyagent.tool.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 执行降级链。
 * <p>
 * 负责模型调用、RAG 故障自动降级重试、超时/网络异常兜底、原生 REST 降级（合并 A9 两套重复逻辑）、
 * 空响应兜底文案生成。从 ChatService 拆分而来，保持原逻辑行为完全不变。
 * </p>
 *
 * <h3>A9 合并说明</h3>
 * 原 ChatService 中存在两套几乎相同的原生 REST 降级：
 * <ol>
 *   <li>{@code tryDirectOpenAiCompatibleFallback} — 超时/网络异常时从 DynamicChatClientFactory 解析运行时配置后调用</li>
 *   <li>{@code fallbackToDirectRestIfNeeded} — Spring AI 调用失败时从 AgentConfig 解析托管模型后调用</li>
 * </ol>
 * 合并为统一的 {@link #executeUnifiedDirectRestFallback}，依次尝试托管模型 → 运行时配置两种解析路径，
 * 统一日志格式，行为语义与原来等价。
 */
@Slf4j
@Service
public class ExecutionFallbackChain {

    private final DynamicChatClientFactory dynamicChatClientFactory;
    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final ModelProviderService modelProviderService;
    private final ToolFailurePolicyCenter toolFailurePolicyCenter;
    private final RagFailureDetector ragFailureDetector;
    private final KnowledgeReferenceService knowledgeReferenceService;
    private final ExecutionPromptBuilder executionPromptBuilder;

    public ExecutionFallbackChain(DynamicChatClientFactory dynamicChatClientFactory,
                                   DynamicAgentConfigHolder dynamicAgentConfigHolder,
                                   ModelProviderService modelProviderService,
                                   ToolFailurePolicyCenter toolFailurePolicyCenter,
                                   RagFailureDetector ragFailureDetector,
                                   KnowledgeReferenceService knowledgeReferenceService,
                                   ExecutionPromptBuilder executionPromptBuilder) {
        this.dynamicChatClientFactory = dynamicChatClientFactory;
        this.dynamicAgentConfigHolder = dynamicAgentConfigHolder;
        this.modelProviderService = modelProviderService;
        this.toolFailurePolicyCenter = toolFailurePolicyCenter;
        this.ragFailureDetector = ragFailureDetector;
        this.knowledgeReferenceService = knowledgeReferenceService;
        this.executionPromptBuilder = executionPromptBuilder;
    }

    /**
     * 执行结果记录：模型输出内容 + 引用列表。
     */
    public record ChatExecutionResult(String content, List<ChatResponse.Reference> references) {
        public ChatExecutionResult {
            references = references == null ? List.of() : List.copyOf(references);
        }

        public ChatExecutionResult withContent(String updatedContent) {
            return new ChatExecutionResult(updatedContent, references);
        }
    }

    // ==================== 主降级执行入口 ====================

    /**
     * 带 RAG 降级重试的模型调用。
     * <ol>
     *   <li>优先使用 ChatClient 调用（含 RAG）</li>
     *   <li>若 RAG 可恢复故障 → 关闭 RAG 重试</li>
     *   <li>若仍然失败 → 统一原生 REST 降级</li>
     * </ol>
     */
    public ChatExecutionResult executeChatWithFallback(String userMessage,
                                                        String conversationId,
                                                        boolean enableToolCallbacks,
                                                        boolean enableRag) {
        try {
            return executePrompt(dynamicChatClientFactory.create(enableToolCallbacks, enableRag),
                    userMessage, conversationId);
        } catch (Exception exception) {
            if (enableRag && ragFailureDetector.isRecoverable(exception)) {
                log.warn("RAG 检索链路异常，关闭知识库增强后重试 | conversationId={} | error={}",
                        conversationId, exception.getMessage());
                try {
                    return executePrompt(dynamicChatClientFactory.create(enableToolCallbacks, false),
                            userMessage, conversationId);
                } catch (Exception degradedException) {
                    return fallbackToUnifiedDirectRest(degradedException, userMessage, conversationId);
                }
            }

            return fallbackToUnifiedDirectRest(exception, userMessage, conversationId);
        }
    }

    /**
     * 通过 ChatClient 执行一次模型调用并提取结果。
     */
    public ChatExecutionResult executePrompt(ChatClient chatClient, String userMessage, String conversationId) {
        ChatClientResponse response = chatClient.prompt()
                .system(dynamicAgentConfigHolder.getSystemPrompt())
                .user(userMessage)
                .advisors(advisorSpec -> advisorSpec
                        .param("chat_memory_conversation_id", conversationId))
                .call()
                .chatClientResponse();
        return new ChatExecutionResult(
                extractContent(response),
                knowledgeReferenceService.buildReferences(response.context())
        );
    }

    // ==================== 超时/网络异常兜底 ====================

    /**
     * 同步对话的超时/网络异常兜底。
     * <p>
     * 优先级：补贴计算结果 > 联网搜索结果 > 统一 REST 降级 > 固定兜底文案。
     * </p>
     */
    public ChatExecutionResult fallbackForSyncChatFailure(RuntimeException exception,
                                                           String rawUserMessage,
                                                           String conversationId,
                                                           WebSearchTool.SearchResponse webSearchResponse,
                                                           SubsidyCalculatorTool.SubsidyResponse subsidyResponse) {
        if (!isTimeoutOrNetworkError(exception)) {
            throw exception;
        }

        log.warn("模型调用超时/网络异常，使用本地兜底应答 | conversationId={} | error={}",
                conversationId, exception.getMessage());

        if (subsidyResponse != null) {
            return new ChatExecutionResult(
                    executionPromptBuilder.buildDirectSubsidyAnswer(subsidyResponse), List.of());
        }

        if (webSearchResponse != null) {
            return new ChatExecutionResult(
                    executionPromptBuilder.buildWebSearchFallbackAnswer(webSearchResponse), List.of());
        }

        ChatExecutionResult directFallback = executeUnifiedDirectRestFallback(rawUserMessage, conversationId);
        if (directFallback != null) {
            return directFallback;
        }

        return new ChatExecutionResult("当前模型服务响应超时，请稍后重试。", List.of());
    }

    // ==================== A9 合并：统一原生 REST 降级 ====================

    /**
     * Spring AI 调用失败时的统一降级入口。
     * <p>
     * 合并原 {@code fallbackToDirectRestIfNeeded} 和 {@code tryDirectOpenAiCompatibleFallback} 两套逻辑。
     * 先检查是否满足降级条件（404+volcano / 超时网络错误），再依次尝试托管模型和运行时配置两种路径。
     * </p>
     */
    private ChatExecutionResult fallbackToUnifiedDirectRest(Exception exception,
                                                              String userMessage,
                                                              String conversationId) {
        ModelProvider runtimeModel = resolveManagedLlmModel();
        if (runtimeModel == null || !shouldUseDirectRestFallback(exception, runtimeModel)) {
            throw asRuntimeException(exception);
        }

        log.warn("Spring AI 调用模型失败，切换为原生 REST 降级 | conversationId={} | provider={} | model={} | error={}",
                conversationId, runtimeModel.getProvider(), runtimeModel.getModelName(), exception.getMessage());

        try {
            String content = modelProviderService.executeChatCompletion(
                    runtimeModel,
                    dynamicAgentConfigHolder.getSystemPrompt(),
                    userMessage
            );
            log.info("原生 REST 降级调用成功 | conversationId={} | model={}",
                    conversationId, runtimeModel.getModelName());
            return new ChatExecutionResult(content, List.of());
        } catch (Exception restException) {
            log.warn("托管模型 REST 降级失败，尝试运行时配置降级 | conversationId={} | error={}",
                    conversationId, restException.getMessage());
            ChatExecutionResult runtimeFallback = executeUnifiedDirectRestFallback(userMessage, conversationId);
            if (runtimeFallback != null) {
                return runtimeFallback;
            }
            throw asRuntimeException(exception);
        }
    }

    /**
     * 统一原生 REST 降级：通过 DynamicChatClientFactory 解析运行时配置并调用模型。
     * <p>
     * 合并自原 {@code tryDirectOpenAiCompatibleFallback}，用于超时/网络异常场景的 REST 兜底。
     * 同时作为 {@code fallbackToUnifiedDirectRest} 的第二阶段降级（托管模型 REST 失败时回退）。
     * </p>
     *
     * @return 降级结果，若无法执行则返回 null
     */
    public ChatExecutionResult executeUnifiedDirectRestFallback(String userMessage, String conversationId) {
        try {
            DynamicChatClientFactory.ResolvedChatModelConfig runtimeConfig =
                    dynamicChatClientFactory.resolveRuntimeConfig();
            if (runtimeConfig == null
                    || runtimeConfig.baseUrl() == null || runtimeConfig.baseUrl().isBlank()
                    || runtimeConfig.apiKey() == null || runtimeConfig.apiKey().isBlank()
                    || runtimeConfig.modelName() == null || runtimeConfig.modelName().isBlank()) {
                return null;
            }

            ModelProvider fallbackModel = ModelProvider.builder()
                    .provider("openai-compatible")
                    .apiUrl(runtimeConfig.baseUrl())
                    .apiKey(runtimeConfig.apiKey())
                    .modelName(runtimeConfig.modelName())
                    .temperature(runtimeConfig.temperature() == null ? null
                            : BigDecimal.valueOf(runtimeConfig.temperature()))
                    .maxTokens(runtimeConfig.maxTokens())
                    .topP(runtimeConfig.topP())
                    .build();

            String content = modelProviderService.executeChatCompletion(
                    fallbackModel,
                    dynamicAgentConfigHolder.getSystemPrompt(),
                    userMessage
            );
            log.warn("已通过原生 REST 完成降级调用 | conversationId={} | model={}",
                    conversationId, runtimeConfig.modelName());
            return new ChatExecutionResult(content, List.of());
        } catch (Exception exception) {
            log.warn("原生 REST 降级调用失败 | conversationId={} | error={}",
                    conversationId, exception.getMessage());
            return null;
        }
    }

    // ==================== 空响应兜底 ====================

    /**
     * 确保字符串响应非空（String 版本）。
     */
    public String ensureNonBlankResponse(String response,
                                          WebSearchTool.SearchResponse webSearchResponse,
                                          SubsidyCalculatorTool.SubsidyResponse subsidyResponse,
                                          String conversationId) {
        if (response != null && !response.isBlank()) {
            return response;
        }
        if (subsidyResponse != null) {
            log.warn("模型返回空内容，使用补贴计算结果生成兜底答案 | conversationId={}", conversationId);
            return executionPromptBuilder.buildDirectSubsidyAnswer(subsidyResponse);
        }
        if (webSearchResponse == null) {
            return response;
        }

        log.warn("模型返回空内容，使用联网搜索结果生成兜底答案 | conversationId={}", conversationId);
        return executionPromptBuilder.buildWebSearchFallbackAnswer(webSearchResponse);
    }

    /**
     * 确保 ChatExecutionResult 非空（Result 版本）。
     */
    public ChatExecutionResult ensureNonBlankResponse(ChatExecutionResult result,
                                                       WebSearchTool.SearchResponse webSearchResponse,
                                                       SubsidyCalculatorTool.SubsidyResponse subsidyResponse,
                                                       String conversationId) {
        if (result == null) {
            return new ChatExecutionResult(
                    ensureNonBlankResponse((String) null, webSearchResponse, subsidyResponse, conversationId),
                    List.of()
            );
        }
        return result.withContent(
                ensureNonBlankResponse(result.content(), webSearchResponse, subsidyResponse, conversationId));
    }

    // ==================== 模型解析与降级判定 ====================

    /**
     * 从管理员已配置的 LLM 模型中解析 ModelProvider 实体。
     */
    public ModelProvider resolveManagedLlmModel() {
        AgentConfig config = dynamicAgentConfigHolder.get();
        if (config == null || config.getLlmModelId() == null) {
            return null;
        }

        try {
            return modelProviderService.getModelEntityForRuntime(config.getLlmModelId(), null);
        } catch (Exception ex) {
            log.warn("解析管理员已配置模型失败，跳过托管模型 REST 降级 | modelId={} | error={}",
                    config.getLlmModelId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 判断是否应该走原生 REST 降级（不走 Spring AI）。
     * <p>
     * 触发条件：404+volcano 或超时/网络异常。
     * </p>
     */
    public boolean shouldUseDirectRestFallback(Exception exception, ModelProvider runtimeModel) {
        if (is404Error(exception) && "volcano".equalsIgnoreCase(runtimeModel.getProvider())) {
            return true;
        }
        return isTimeoutOrNetworkError(exception);
    }

    // ==================== 异常判定工具方法 ====================

    /**
     * 判断异常链中是否包含 404 状态码。
     */
    public boolean is404Error(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("404")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 判断异常链中是否包含超时或网络错误。
     */
    public boolean isTimeoutOrNetworkError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("readtimeout")
                        || normalized.contains("timed out")
                        || normalized.contains("resourceaccessexception")
                        || normalized.contains("i/o error on post request")
                        || normalized.contains("connection reset")
                        || normalized.contains("connection refused")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public RuntimeException asRuntimeException(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }

    // ==================== 内部工具方法 ====================

    private String extractContent(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }
}
