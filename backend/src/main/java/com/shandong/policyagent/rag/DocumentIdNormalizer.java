package com.shandong.policyagent.rag;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 将任意文档 ID 规范化为 PGVector 可接受的 UUID。
 */
public final class DocumentIdNormalizer {

    private DocumentIdNormalizer() {
    }

    public static String normalize(String rawId, String source, String text) {
        if (isValidUuid(rawId)) {
            return rawId;
        }
        String seed = (safe(rawId) + "|" + safe(source) + "|" + safe(text));
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
