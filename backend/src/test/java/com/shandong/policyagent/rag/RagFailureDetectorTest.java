package com.shandong.policyagent.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagFailureDetectorTest {

    private final RagFailureDetector detector = new RagFailureDetector();

    @Test
    void shouldDetectRecoverableOllamaMemoryFailure() {
        RuntimeException error = new RuntimeException(
                "500 Internal Server Error: {\"error\":\"model requires more system memory (1.3 GiB) than is available (1.2 GiB)\"}"
        );

        assertTrue(detector.isRecoverable(error));
    }

    @Test
    void shouldDetectContextLengthFailureAsRecoverable() {
        RuntimeException error = new RuntimeException(
                "400 Bad Request: {\"error\":\"the input length exceeds the context length\"}"
        );

        assertTrue(detector.isRecoverable(error));
    }

    @Test
    void shouldIgnoreUnrelatedErrors() {
        RuntimeException error = new RuntimeException("401 Unauthorized");

        assertFalse(detector.isRecoverable(error));
    }
}
