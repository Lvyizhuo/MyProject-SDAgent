package com.shandong.policyagent.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 链路耗时指标。
 *
 * <p>把 {@link StageTimer} 采集到的阶段耗时、token 用量、缓存命中情况暴露为 Micrometer 指标，
 * 便于用 Prometheus / Grafana 持续观察「优化前 vs 优化后」的链路耗时分布。
 *
 * <p>指标清单：
 * <table border="1">
 *   <caption>指标</caption>
 *   <tr><th>指标</th><th>类型</th><th>标签</th><th>含义</th></tr>
 *   <tr><td>{@code policy.chat.stage}</td><td>Timer</td><td>scope, stage</td>
 *       <td>单个阶段的耗时（scope=chat/agentChain/ragRetrieve）</td></tr>
 *   <tr><td>{@code policy.chat.total}</td><td>Timer</td><td>scope</td><td>整轮请求耗时</td></tr>
 *   <tr><td>{@code policy.chat.tokens}</td><td>DistributionSummary</td><td>type</td>
 *       <td>token 用量（type=prompt/completion）</td></tr>
 *   <tr><td>{@code policy.chat.cache}</td><td>Counter</td><td>result</td>
 *       <td>缓存命中（result=hit/miss/semantic_hit/semantic_miss）</td></tr>
 *   <tr><td>{@code policy.chat.llm.call}</td><td>Counter</td><td>outcome</td>
 *       <td>模型调用次数（outcome=success/failure）</td></tr>
 *   <tr><td>{@code policy.chat.tool.failure}</td><td>Counter</td><td>tool</td>
 *       <td>工具调用失败次数</td></tr>
 * </table>
 */
@Slf4j
@Component
public class ChatMetrics {

    private final MeterRegistry meterRegistry;

    public ChatMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 阶段耗时观察者：交给 {@link StageTimer}，每记录一个阶段就上报一次。
     */
    public StageTimer.Observer observer(String scope) {
        return (stage, nanos) -> recordStage(scope, stage, nanos);
    }

    public void recordStage(String scope, String stage, long nanos) {
        try {
            Timer.builder("policy.chat.stage")
                    .description("对话链路单阶段耗时")
                    .tag("scope", scope)
                    .tag("stage", stage)
                    .register(meterRegistry)
                    .record(nanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("上报阶段指标失败 | scope={} | stage={} | error={}", scope, stage, e.getMessage());
        }
    }

    /**
     * 上报整轮请求耗时。
     */
    public void recordTotal(String scope, long nanos) {
        try {
            Timer.builder("policy.chat.total")
                    .description("对话整轮耗时")
                    .tag("scope", scope)
                    .register(meterRegistry)
                    .record(nanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("上报总耗时指标失败 | scope={} | error={}", scope, e.getMessage());
        }
    }

    public void recordTokens(String type, long count) {
        try {
            DistributionSummary.builder("policy.chat.tokens")
                    .description("模型调用 token 用量")
                    .tag("type", type)
                    .register(meterRegistry)
                    .record(count);
        } catch (Exception e) {
            log.debug("上报 token 指标失败 | type={} | error={}", type, e.getMessage());
        }
    }

    public void recordCache(String result) {
        try {
            Counter.builder("policy.chat.cache")
                    .description("检索缓存命中情况")
                    .tag("result", result)
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("上报缓存指标失败 | result={} | error={}", result, e.getMessage());
        }
    }

    public void recordLlmCall(String outcome) {
        try {
            Counter.builder("policy.chat.llm.call")
                    .description("模型调用次数")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("上报模型调用指标失败 | outcome={} | error={}", outcome, e.getMessage());
        }
    }

    public void recordToolFailure(String tool) {
        try {
            Counter.builder("policy.chat.tool.failure")
                    .description("工具调用失败次数")
                    .tag("tool", tool == null ? "unknown" : tool)
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("上报工具失败指标失败 | tool={} | error={}", tool, e.getMessage());
        }
    }
}
