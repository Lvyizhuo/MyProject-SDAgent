package com.shandong.policyagent.support;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 轻量级阶段计时器：把一次请求内的各阶段耗时累积起来，最后统一输出结构化摘要日志。
 *
 * <p>设计目标：
 * <ul>
 *   <li>零依赖：只用 {@link System#nanoTime()}，不引入 Micrometer 等额外组件；</li>
 *   <li>低开销：计时点之间不打印日志，避免污染业务日志；</li>
 *   <li>可复用：{@link #time(String, Supplier)} 包裹任意有返回值的调用，
 *       {@link #time(String, Runnable)} 包裹无返回值的调用。</li>
 * </ul>
 *
 * <p>用法：
 * <pre>{@code
 * StageTimer timer = new StageTimer();
 * String facts = timer.time("facts", () -> sessionFactCacheService.mergeFacts(...));
 * ...
 * timer.logSummary("chat", conversationId);
 * }</pre>
 */
@Slf4j
public final class StageTimer {

    /**
     * 阶段观察者：每记录一个阶段就回调一次，用于把耗时上报到指标系统。
     */
    @FunctionalInterface
    public interface Observer {
        void onStage(String stage, long nanos);

        static Observer noop() {
            return (stage, nanos) -> { };
        }
    }

    private final Map<String, Long> durations = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final long startNanos = System.nanoTime();
    private final Observer observer;
    private final String scope;

    public StageTimer() {
        this("default", Observer.noop());
    }

    public StageTimer(String scope, Observer observer) {
        this.scope = scope == null ? "default" : scope;
        this.observer = observer == null ? Observer.noop() : observer;
    }

    /**
     * 计时并执行一段有返回值的逻辑。
     */
    public <T> T time(String stage, Supplier<T> action) {
        long begin = System.nanoTime();
        try {
            return action.get();
        } finally {
            record(stage, System.nanoTime() - begin);
        }
    }

    /**
     * 计时并执行一段无返回值的逻辑。
     */
    public void time(String stage, Runnable action) {
        long begin = System.nanoTime();
        try {
            action.run();
        } finally {
            record(stage, System.nanoTime() - begin);
        }
    }

    /**
     * 累加一个外部测得的耗时（例如从 HTTP 响应头或已有日志里拿到的毫秒数）。
     */
    public void addMillis(String stage, long millis) {
        durations.merge(stage, millis * 1_000_000L, Long::sum);
        counts.merge(stage, 1, Integer::sum);
    }

    /**
     * 累加一个以纳秒计的外部耗时。
     */
    public void addNanos(String stage, long nanos) {
        durations.merge(stage, nanos, Long::sum);
        counts.merge(stage, 1, Integer::sum);
    }

    /**
     * 计时器创建时刻的纳秒时间戳，用于计算"自创建以来经过的时长"。
     */
    public long startNanos() {
        return startNanos;
    }

    private void record(String stage, long elapsedNanos) {
        durations.merge(stage, elapsedNanos, Long::sum);
        counts.merge(stage, 1, Integer::sum);
        try {
            observer.onStage(stage, elapsedNanos);
        } catch (Exception e) {
            // 指标上报失败不影响主链路
            log.debug("上报阶段指标失败 | scope={} | stage={} | error={}", scope, stage, e.getMessage());
        }
    }

    /**
     * 某个阶段累计耗时（毫秒）。未记录过返回 0。
     */
    public long millisOf(String stage) {
        Long nanos = durations.get(stage);
        return nanos == null ? 0L : nanos / 1_000_000L;
    }

    /**
     * 总耗时（毫秒）。
     */
    public long totalMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 输出结构化摘要日志：总耗时 + 各阶段耗时 + 占比。
     *
     * <p>输出形如：
     * <pre>
     * [StageTimer] chat | conversationId=xxx | total=46087ms | facts=12ms(0.03%) | model=31234ms(67.8%) | ...
     * </pre>
     */
    public void logSummary(String label, String conversationId) {
        long total = totalMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(label).append("] 阶段耗时 | conversationId=").append(conversationId)
                .append(" | total=").append(total).append("ms");
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            long millis = entry.getValue() / 1_000_000L;
            double ratio = total <= 0 ? 0 : millis * 100.0 / total;
            sb.append(" | ").append(entry.getKey()).append('=').append(millis).append("ms(")
                    .append(String.format("%.1f", ratio)).append("%)");
        }
        log.info(sb.toString());
    }    /**
     * 输出阶段耗时明细（不含占比），便于紧凑查看。
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue() / 1_000_000L).append("ms")
                    .append("x").append(counts.getOrDefault(entry.getKey(), 1));
        }
        return sb.toString();
    }
}
