package com.shandong.policyagent.advisor;

import com.shandong.policyagent.support.StageTimer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;

/**
 * 链路计时 Advisor：把「Advisor 前置工作」与「真正的模型调用」分开计量。
 *
 * <p>放在 advisor 链的倒数第二位（order 85，LoggingAdvisor 为 90）：
 * <ul>
 *   <li>到达本 Advisor 时，SecurityAdvisor / ChatMemory / ReReading / QuestionAnswerAdvisor(RAG)
 *       都已执行完毕 —— 这段耗时记为 {@code preModel}；</li>
 *   <li>本 Advisor 的 {@code adviseCall} 返回后即为模型调用结束 —— 记为 {@code llmCall}。</li>
 * </ul>
 *
 * <p>计时器由 {@code ExecutionFallbackChain.executePrompt} 创建后放入 Advisor 上下文，
 * 避免依赖 ThreadLocal。
 */
@Slf4j
public class TimingAdvisor implements CallAdvisor {

    public static final String TIMER_KEY = "stage_timer";

    private static final String NAME = "TimingAdvisor";
    private static final int DEFAULT_ORDER = 85;

    private final int order;

    public TimingAdvisor() {
        this(DEFAULT_ORDER);
    }

    public TimingAdvisor(int order) {
        this.order = order;
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
        StageTimer timer = resolveTimer(request);
        long begin = System.nanoTime();

        if (timer != null) {
            // 自计时器创建到进入本 Advisor 的耗时 = 安全检查 + 记忆加载 + ReReading + RAG 检索
            timer.addNanos("preModel", begin - timer.startNanos());
        }

        ChatClientResponse response = chain.nextCall(request);

        if (timer != null) {
            timer.addNanos("llmCall", System.nanoTime() - begin);
        }
        return response;
    }

    private StageTimer resolveTimer(ChatClientRequest request) {
        if (request == null || request.context() == null) {
            return null;
        }
        Object timer = request.context().get(TIMER_KEY);
        return timer instanceof StageTimer stageTimer ? stageTimer : null;
    }
}
