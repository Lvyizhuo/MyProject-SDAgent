package com.shandong.policyagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 识别可通过关闭 RAG 检索后恢复的运行时异常，避免检索链路故障拖垮整轮对话。
 * <p>
 * 设计原则：先按异常类型/类名判断（精确），消息匹配降级为辅助手段（收窄关键词避免误伤）。
 * </p>
 */
@Slf4j
@Component
public class RagFailureDetector {

    /**
     * 精确 RAG 基础设施标记（表名、API 端点、已知错误特征），仅作辅助手段。
     * 移除了过泛词"embedding""vector store""similaritysearch""questionansweradvisor""rerank"——这些会命中正常 LLM 输出文本。
     */
    private static final List<String> RECOVERABLE_RAG_MARKERS = List.of(
            "model requires more system memory",
            "input length exceeds the context length",
            "/api/embed",
            "pgvector",
            "vector_store"
    );

    /**
     * 裸词"context length"过于泛（可能出现在正常政策文本中），仅在伴随 HTTP 状态码时视为 RAG 故障。
     */
    private static boolean isContextLengthOverflowMessage(String normalizedMessage) {
        return normalizedMessage.contains("context length")
                && (normalizedMessage.contains("400") || normalizedMessage.contains("exceeds"));
    }

    public boolean isRecoverable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            // 第一优先级：异常类型/类名判断
            if (isRecoverableByType(current)) {
                return true;
            }
            // 第二优先级（降级辅助）：收窄后的消息关键词匹配
            if (matchesRecoverableRagFailure(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 按异常类型判断是否为可恢复的 RAG 故障。
     * <ul>
     *     <li>{@link org.springframework.dao.DataAccessException} — pgvector JDBC 层（连接失败、表不存在等）</li>
     *     <li>OpenAiApiClientErrorException — 嵌入/LLM API 调用异常（Spring AI OpenAI 客户端）</li>
     *     <li>HttpClientErrorException / ResourceAccessException — HTTP 层面调用嵌入/重排序服务失败，仅当消息含 embedding/vector 相关特征时才判定</li>
     * </ul>
     */
    private boolean isRecoverableByType(Throwable t) {
        String className = t.getClass().getName();

        // Spring DAO 层异常：pgvector JDBC 操作失败（连接断开、表损坏等）
        if (t instanceof org.springframework.dao.DataAccessException) {
            log.debug("识别到 DataAccessException，判定为可恢复 RAG 故障: {}", className);
            return true;
        }

        // OpenAI API 客户端异常：嵌入调用通过 OpenAI 兼容协议，失败即阻断检索
        if (className.contains("OpenAiApiClientErrorException")) {
            log.debug("识别到 OpenAiApiClientErrorException，判定为可恢复 RAG 故障: {}", className);
            return true;
        }

        // HTTP 客户端异常（RestClient 层）：仅当消息含嵌入/向量相关端点特征时判定
        // 避免将非 RAG 的 HTTP 调用错误误判为 RAG 故障
        if (className.contains("HttpClientErrorException") || className.contains("ResourceAccessException")) {
            if (matchesEmbeddingOrVectorMessage(t.getMessage())) {
                log.debug("HTTP 客户端异常且消息匹配嵌入/向量特征，判定为可恢复 RAG 故障: {}", className);
                return true;
            }
        }

        return false;
    }

    /**
     * 收窄后的消息关键词匹配。仅保留精确表名、API 端点、已知错误特征。
     * 移除过泛词以降低误判风险。
     */
    private boolean matchesRecoverableRagFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return RECOVERABLE_RAG_MARKERS.stream().anyMatch(normalized::contains)
                || isContextLengthOverflowMessage(normalized);
    }

    /**
     * 辅助判断：HTTP 异常消息是否与嵌入/向量操作相关。
     * 仅匹配精确端点路径或表名，不匹配泛词"embedding""vector"。
     */
    private boolean matchesEmbeddingOrVectorMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("/api/embed")
                || normalized.contains("pgvector")
                || normalized.contains("vector_store")
                || normalized.contains("embedding model");
    }
}
