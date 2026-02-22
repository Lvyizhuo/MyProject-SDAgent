package com.shandong.policyagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 工具失败策略中心：统一管理重试、退避与兜底提示模板。
 */
@Slf4j
@Component
public class ToolFailurePolicyCenter {

    @Value("${app.tools.retry.max-attempts:2}")
    private int maxAttempts = 2;

    @Value("${app.tools.retry.backoff-millis:300}")
    private long backoffMillis = 300L;

    public <T> T executeWithRetry(String toolName,
                                  Supplier<T> action,
                                  Predicate<Throwable> retryable,
                                  Supplier<T> fallbackAction) {
        Throwable lastError = null;
        int attempts = Math.max(1, maxAttempts);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.get();
            } catch (Throwable error) {
                lastError = error;
                boolean canRetry = attempt < attempts
                        && (retryable == null || retryable.test(error));
                if (!canRetry) {
                    break;
                }
                log.warn("工具调用失败，准备重试 | tool={} | attempt={}/{} | error={}",
                        toolName, attempt, attempts, error.getMessage());
                sleepBackoff();
            }
        }

        log.warn("工具调用最终失败，执行兜底 | tool={} | error={}",
                toolName, lastError == null ? "unknown" : lastError.getMessage());
        return fallbackAction == null ? null : fallbackAction.get();
    }

    public String fallbackMessage(String toolName, String reason) {
        String detail = (reason == null || reason.isBlank()) ? "未知原因" : reason;
        return switch (toolName) {
            case "webSearch" -> "联网搜索暂时不可用（" + detail + "）。我可以先基于已知政策和历史信息给出参考结论。";
            case "amap-mcp" -> "地图服务暂时不可用（" + detail + "）。请提供更具体的区县/地标，我先给出人工兜底方案。";
            case "calculateSubsidy" -> "补贴计算工具暂时不可用（" + detail + "）。请提供商品类型和价格，我将按规则手动估算。";
            default -> "工具调用失败（" + detail + "）。我将切换为保守回答并提示你补充关键信息。";
        };
    }

    private void sleepBackoff() {
        if (backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
