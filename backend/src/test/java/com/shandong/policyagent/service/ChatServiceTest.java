package com.shandong.policyagent.service;

import com.shandong.policyagent.agent.AgentExecutionPlan;
import com.shandong.policyagent.agent.ReActPlanningService;
import com.shandong.policyagent.agent.ToolIntentClassifier;
import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import com.shandong.policyagent.model.ChatRequest;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.model.ChatStreamEvent;
import com.shandong.policyagent.multimodal.service.VisionService;
import com.shandong.policyagent.rag.RagFailureDetector;
import com.shandong.policyagent.tool.ToolFailurePolicyCenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private DynamicChatClientFactory dynamicChatClientFactory;

    @Mock
    private DynamicAgentConfigHolder dynamicAgentConfigHolder;

    @Mock
    private VisionService visionService;

    @Mock
    private ReActPlanningService planningService;

    @Mock
    private ToolIntentClassifier toolIntentClassifier;

    @Mock
    private SessionFactCacheService sessionFactCacheService;

        @Mock
        private FastPathService fastPathService;

        @Mock
        private QuestionSemanticCacheService questionSemanticCacheService;

    @Mock
    private ToolFailurePolicyCenter toolFailurePolicyCenter;

    @Mock
    private ModelProviderService modelProviderService;

        @Mock
        private ProductPriceCacheService productPriceCacheService;

    @Mock
    private RagFailureDetector ragFailureDetector;

    @Mock
    private KnowledgeReferenceService knowledgeReferenceService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                dynamicChatClientFactory,
                dynamicAgentConfigHolder,
                visionService,
                planningService,
                toolIntentClassifier,
                sessionFactCacheService,
                fastPathService,
                questionSemanticCacheService,
                toolFailurePolicyCenter,
                modelProviderService,
                                productPriceCacheService,
                ragFailureDetector,
                knowledgeReferenceService
        );
    }

    @Test
    void shouldAskCategoryBeforePlanningForBroadPolicyQuery() {
        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class)))
                .thenReturn(new SessionFactCacheService.SessionFacts());

        ChatResponse response = chatService.chat(ChatRequest.builder()
                .conversationId("conversation-collect-1")
                .message("帮我查一下2026年的补贴政策")
                .build());

        assertTrue(response.getContent().contains("哪类产品"));
        verify(sessionFactCacheService).markPendingSlot("conversation-collect-1", "商品类别");
        verify(planningService, never()).createPlan(anyString(), anyString());
        verify(dynamicChatClientFactory, never()).create(anyBoolean(), anyBoolean());
    }

    @Test
    void shouldRetryWithoutRagWhenEmbeddingModelRunsOutOfMemory() {
        ChatClient primaryClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient degradedClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        RuntimeException ragFailure = new RuntimeException(
                "500 Internal Server Error: {\"error\":\"model requires more system memory (1.3 GiB) than is available (1.2 GiB)\"}"
        );

        AgentExecutionPlan plan = new AgentExecutionPlan(
                "直接回答用户问题",
                false,
                List.of(new AgentExecutionPlan.AgentStep(1, "基于已有信息回答", "rag"))
        );
        ToolIntentClassifier.IntentDecision decision =
                new ToolIntentClassifier.IntentDecision(true, "none", "", "无需工具");

        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class)))
                .thenReturn(new SessionFactCacheService.SessionFacts());
        when(planningService.createPlan(anyString(), anyString())).thenReturn(plan);
        when(toolIntentClassifier.classify(anyString(), eq(plan))).thenReturn(decision);
        when(toolIntentClassifier.applyDecision(eq(plan), eq(decision))).thenReturn(plan);
        when(dynamicAgentConfigHolder.getSystemPrompt()).thenReturn("你是山东省智能政策咨询助手。");
        when(dynamicChatClientFactory.create(false, true)).thenReturn(primaryClient);
        when(dynamicChatClientFactory.create(false, false)).thenReturn(degradedClient);
        when(primaryClient.prompt().system(anyString()).user(anyString()).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()).call().chatClientResponse())
                .thenThrow(ragFailure);
        when(degradedClient.prompt().system(anyString()).user(anyString()).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()).call().chatClientResponse())
                .thenReturn(chatClientResponse("已自动关闭知识库检索并完成回答。"));
        when(ragFailureDetector.isRecoverable(ragFailure)).thenReturn(true);
        when(knowledgeReferenceService.buildReferences(any())).thenReturn(List.of());

        ChatResponse response = chatService.chat(ChatRequest.builder()
                .conversationId("conversation-1")
                .message("山东家电以旧换新政策有哪些重点？")
                .build());

        assertEquals("已自动关闭知识库检索并完成回答。", response.getContent());
        verify(dynamicChatClientFactory).create(false, true);
        verify(dynamicChatClientFactory).create(false, false);
        verify(modelProviderService, never()).executeChatCompletion(any(), anyString(), anyString());
    }

    @Test
    void shouldDisableRagWhenRealtimeWebSearchAlreadyRan() {
        ChatClient directSearchClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "先联网搜索商品价格再回答",
                true,
                List.of(
                        new AgentExecutionPlan.AgentStep(1, "调用 webSearch 获取最新价格", "webSearch"),
                        new AgentExecutionPlan.AgentStep(2, "整理检索结果并回答", "none")
                )
        );
        ToolIntentClassifier.IntentDecision decision =
                new ToolIntentClassifier.IntentDecision(true, "webSearch", "", "需要联网搜索");

        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class)))
                .thenReturn(new SessionFactCacheService.SessionFacts());
        when(planningService.createPlan(anyString(), anyString())).thenReturn(plan);
        when(toolIntentClassifier.classify(anyString(), eq(plan))).thenReturn(decision);
        when(toolIntentClassifier.applyDecision(eq(plan), eq(decision))).thenReturn(plan);
        when(dynamicAgentConfigHolder.getSystemPrompt()).thenReturn("你是山东省智能政策咨询助手。");
        when(dynamicChatClientFactory.create(true, false)).thenReturn(directSearchClient);
        when(directSearchClient.prompt().system(anyString()).user(anyString()).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()).call().chatClientResponse())
                .thenReturn(chatClientResponse("已根据联网搜索结果回答。"));
        when(knowledgeReferenceService.buildReferences(any())).thenReturn(List.of());

        ChatResponse response = chatService.chat(ChatRequest.builder()
                .conversationId("conversation-2")
                .message("帮我查一下欧克 Pro m5Pro 的价格")
                .build());

        assertEquals("已根据联网搜索结果回答。", response.getContent());
        verify(dynamicChatClientFactory).create(true, false);
        verify(dynamicChatClientFactory, never()).create(false, true);
    }

    @Test
    void shouldTriggerAsyncPricePrefetchForPurePriceQuery() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "先明确价格后回答",
                true,
                List.of(new AgentExecutionPlan.AgentStep(1, "调用 webSearch 获取价格", "webSearch"))
        );
        ToolIntentClassifier.IntentDecision decision =
                new ToolIntentClassifier.IntentDecision(true, "webSearch", "", "价格查询");

        SessionFactCacheService.SessionFacts facts = new SessionFactCacheService.SessionFacts();
        facts.getDeviceModels().add("macbook pro 14");
        facts.getCategories().add("笔记本");

        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class))).thenReturn(facts);
        when(planningService.createPlan(anyString(), anyString())).thenReturn(plan);
        when(toolIntentClassifier.classify(anyString(), eq(plan))).thenReturn(decision);
        when(toolIntentClassifier.applyDecision(eq(plan), eq(decision))).thenReturn(plan);
        when(dynamicAgentConfigHolder.getSystemPrompt()).thenReturn("你是山东省智能政策咨询助手。");
        when(dynamicChatClientFactory.create(true, false)).thenReturn(client);
        when(client.prompt().system(anyString()).user(anyString()).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()).call().chatClientResponse())
                .thenReturn(chatClientResponse("已为您整理价格信息。"));
        when(knowledgeReferenceService.buildReferences(any())).thenReturn(List.of());

        chatService.chat(ChatRequest.builder()
                .conversationId("conversation-prefetch-1")
                .message("查一下MacBook Pro 14寸的价格")
                .build());

        verify(productPriceCacheService).prefetchPriceAsync(eq("查一下MacBook Pro 14寸的价格"), eq(facts));
    }

    @Test
    void shouldNotTriggerAsyncPricePrefetchForPolicyPriceMixedQuestion() {
        SessionFactCacheService.SessionFacts facts = new SessionFactCacheService.SessionFacts();
        facts.getDeviceModels().add("macbook");

        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class))).thenReturn(facts);

        chatService.chat(ChatRequest.builder()
                .conversationId("conversation-prefetch-2")
                .message("查一下MacBook价格和国补政策")
                .build());

        verify(productPriceCacheService, never()).prefetchPriceAsync(anyString(), any());
    }

    @Test
    void shouldIgnoreNullAndEmptyStreamChunks() {
        ChatClient streamingClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "直接回答用户问候",
                false,
                List.of(new AgentExecutionPlan.AgentStep(1, "友好回应用户问候", "none"))
        );
        ToolIntentClassifier.IntentDecision decision =
                new ToolIntentClassifier.IntentDecision(true, "none", "", "无需工具");

        ChatClientResponse nullChunk = new ChatClientResponse(null, Map.of());
        ChatClientResponse emptyChunk = chatClientResponse("");
        ChatClientResponse contentChunk = chatClientResponse("您好，我可以帮您解答山东以旧换新政策问题。");

        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class)))
                .thenReturn(new SessionFactCacheService.SessionFacts());
        when(planningService.createPlan(anyString(), anyString())).thenReturn(plan);
        when(toolIntentClassifier.classify(anyString(), eq(plan))).thenReturn(decision);
        when(toolIntentClassifier.applyDecision(eq(plan), eq(decision))).thenReturn(plan);
        when(dynamicAgentConfigHolder.getSystemPrompt()).thenReturn("你是山东省智能政策咨询助手。");
        when(dynamicChatClientFactory.create(false, false)).thenReturn(streamingClient);
        when(streamingClient.prompt().system(anyString()).user(anyString()).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())
                .stream().chatClientResponse())
                .thenReturn(Flux.just(nullChunk, emptyChunk, contentChunk));
        when(knowledgeReferenceService.buildReferences(any())).thenReturn(List.of());

        List<String> deltaContents = chatService.chatStream(ChatRequest.builder()
                        .conversationId("conversation-3")
                        .message("你好")
                        .build())
                .filter(event -> "delta".equals(event.getType()))
                .map(ChatStreamEvent::getContent)
                .collectList()
                .block();

        assertFalse(deltaContents == null || deltaContents.isEmpty());
        assertEquals(List.of("您好，我可以帮您解答山东以旧换新政策问题。"), deltaContents);
        verify(dynamicChatClientFactory).create(false, false);
        verify(dynamicChatClientFactory, never()).create(false, true);
    }

    @Test
    void shouldEnterAgentPhaseForSubsidyIntentWithoutBackendDirectToolCall() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "问题聚焦补贴金额计算，优先走补贴工具",
                true,
                List.of(
                        new AgentExecutionPlan.AgentStep(1, "收集并校验补贴计算所需参数", "calculateSubsidy"),
                        new AgentExecutionPlan.AgentStep(2, "调用 calculateSubsidy 输出补贴金额和说明", "calculateSubsidy")
                )
        );
        ToolIntentClassifier.IntentDecision decision =
                new ToolIntentClassifier.IntentDecision(true, "calculateSubsidy", "", "参数充分");
        SessionFactCacheService.SessionFacts facts = new SessionFactCacheService.SessionFacts();
        facts.getCategories().add("电视");
        facts.setLatestPrice(5999D);
        ChatClient reactClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class))).thenReturn(facts);
        when(planningService.createPlan(anyString(), anyString())).thenReturn(plan);
        when(toolIntentClassifier.classify(anyString(), eq(plan))).thenReturn(decision);
        when(toolIntentClassifier.applyDecision(eq(plan), eq(decision))).thenReturn(plan);
        when(dynamicAgentConfigHolder.getSystemPrompt()).thenReturn("你是山东省智能政策咨询助手。");
        when(dynamicChatClientFactory.create(true, false)).thenReturn(reactClient);
        when(reactClient.prompt().system(anyString()).user(anyString()).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()).call().chatClientResponse())
                .thenReturn(chatClientResponse("我已进入补贴计算流程，请确认成交价后继续。"));
        when(knowledgeReferenceService.buildReferences(any())).thenReturn(List.of());

        ChatResponse response = chatService.chat(ChatRequest.builder()
                .conversationId("conversation-4")
                .message("电视 5999元补多少")
                .build());

        assertTrue(response.getContent().contains("补贴计算流程"));
        verify(dynamicChatClientFactory).create(true, false);
    }

    @Test
    void shouldNotPreInvokeWebSearchForCombinedToolPlan() {
        AgentExecutionPlan plan = new AgentExecutionPlan(
                "缺少成交价，先联网查价再计算补贴",
                true,
                List.of(
                        new AgentExecutionPlan.AgentStep(1, "调用 webSearch 查询该型号近期价格区间", "webSearch"),
                        new AgentExecutionPlan.AgentStep(2, "根据检索到的成交价调用 calculateSubsidy 计算补贴", "calculateSubsidy")
                )
        );
        ToolIntentClassifier.IntentDecision decision =
                new ToolIntentClassifier.IntentDecision(true, "webSearch", "", "先查价再计算");
        SessionFactCacheService.SessionFacts factsWithoutPrice = new SessionFactCacheService.SessionFacts();
        factsWithoutPrice.getCategories().add("笔记本");
        factsWithoutPrice.getDeviceModels().add("macbook pro m5pro");
        factsWithoutPrice.getIntentHints().add("补贴测算");

        ChatClient reactClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        when(sessionFactCacheService.mergeFacts(anyString(), any(ChatRequest.class))).thenReturn(factsWithoutPrice);
        when(planningService.createPlan(anyString(), anyString())).thenReturn(plan);
        when(toolIntentClassifier.classify(anyString(), eq(plan))).thenReturn(decision);
        when(toolIntentClassifier.applyDecision(eq(plan), eq(decision))).thenReturn(plan);
        when(dynamicAgentConfigHolder.getSystemPrompt()).thenReturn("你是山东省智能政策咨询助手。");
        when(dynamicChatClientFactory.create(true, false)).thenReturn(reactClient);
        when(reactClient.prompt().system(anyString()).user(anyString()).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()).call().chatClientResponse())
                .thenReturn(chatClientResponse("我会先向您确认品牌型号和成交价，再决定是否联网检索。"));
        when(knowledgeReferenceService.buildReferences(any())).thenReturn(List.of());

        ChatResponse response = chatService.chat(ChatRequest.builder()
                .conversationId("conversation-5")
                .message("我想购买 MacBook Pro m5pro，我能享受多少优惠")
                .build());

        assertTrue(response.getContent().contains("确认品牌型号和成交价"));
        verify(dynamicChatClientFactory).create(true, false);
    }

    private ChatClientResponse chatClientResponse(String content) {
        return new ChatClientResponse(
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage(content)))
                ),
                Map.of()
        );
    }
}
