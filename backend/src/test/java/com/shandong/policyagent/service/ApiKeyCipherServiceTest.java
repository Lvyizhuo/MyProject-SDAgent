package com.shandong.policyagent.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyCipherServiceTest {

    @Test
    void shouldDecryptWithLegacySecret() {
        ApiKeyCipherService legacyCipherService = new ApiKeyCipherService();
        ReflectionTestUtils.setField(legacyCipherService, "encryptionSecret", "");
        ReflectionTestUtils.setField(legacyCipherService, "legacyEncryptionSecrets", "");
        ReflectionTestUtils.setField(legacyCipherService, "jwtSecret", "legacy-jwt-secret");
        ReflectionTestUtils.invokeMethod(legacyCipherService, "init");

        String encrypted = legacyCipherService.encrypt("test-api-key");

        ApiKeyCipherService currentCipherService = new ApiKeyCipherService();
        ReflectionTestUtils.setField(currentCipherService, "encryptionSecret", "current-model-secret");
        ReflectionTestUtils.setField(currentCipherService, "legacyEncryptionSecrets", "legacy-jwt-secret");
        ReflectionTestUtils.setField(currentCipherService, "jwtSecret", "current-jwt-secret");
        ReflectionTestUtils.invokeMethod(currentCipherService, "init");

        ApiKeyCipherService.DecryptionResult result = currentCipherService.decryptWithMetadata(encrypted);

        assertEquals("test-api-key", result.getPlainText());
        assertTrue(result.isUsedLegacySecret());
    }
}
