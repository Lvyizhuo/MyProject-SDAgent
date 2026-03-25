package com.shandong.policyagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 识别可通过关闭 RAG 检索后恢复的运行时异常，避免检索链路故障拖垮整轮对话。
 */
@Slf4j
@Component
public class RagFailureDetector {

    private static final List<String> RECOVERABLE_RAG_MARKERS = List.of(
            "model requires more system memory",
            "/api/embed",
            "embedding",
            "vector store",
            "vectorstore",
            "similaritysearch",
            "vector table",
            "pgvector",
            "questionansweradvisor",
            "rerank"
    );

    public boolean isRecoverable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (matchesRecoverableRagFailure(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean matchesRecoverableRagFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return RECOVERABLE_RAG_MARKERS.stream().anyMatch(normalized::contains);
    }
}
