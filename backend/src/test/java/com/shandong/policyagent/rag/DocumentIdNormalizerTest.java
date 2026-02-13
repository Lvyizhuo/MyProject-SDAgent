package com.shandong.policyagent.rag;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DocumentIdNormalizerTest {

    @Test
    void shouldKeepValidUuidUnchanged() {
        String uuid = UUID.randomUUID().toString();

        String normalized = DocumentIdNormalizer.normalize(uuid, "source", "text");

        assertEquals(uuid, normalized);
    }

    @Test
    void shouldConvertLongNonUuidToDeterministicUuid() {
        String longId = "this-is-a-very-long-id-" + "x".repeat(200);

        String normalized1 = DocumentIdNormalizer.normalize(longId, "source-a", "text-a");
        String normalized2 = DocumentIdNormalizer.normalize(longId, "source-a", "text-a");

        assertEquals(normalized1, normalized2);
        assertDoesNotThrow(() -> UUID.fromString(normalized1));
    }

    @Test
    void shouldGenerateDifferentUuidForDifferentContentWhenIdEmpty() {
        String id1 = DocumentIdNormalizer.normalize("", "source-a", "text-a");
        String id2 = DocumentIdNormalizer.normalize("", "source-b", "text-b");

        assertNotEquals(id1, id2);
        assertDoesNotThrow(() -> UUID.fromString(id1));
        assertDoesNotThrow(() -> UUID.fromString(id2));
    }
}
