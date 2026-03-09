package com.shandong.policyagent.service;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ApiKeyCipherService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final String PREFIX = "enc::";
    private static final String DEFAULT_JWT_SECRET =
            "c2VjdXJlU2VjcmV0S2V5Rm9yUG9saWN5QWdlbnRBcHBsaWNhdGlvbjIwMjZKV1Q=";
    private static final String LEGACY_MODEL_PROVIDER_SECRET = "policy-agent-model-provider-secret";

    @Value("${app.model-provider.encryption-secret:}")
    private String encryptionSecret;

    @Value("${app.model-provider.legacy-encryption-secrets:}")
    private String legacyEncryptionSecrets;

    @Value("${app.jwt.secret:" + DEFAULT_JWT_SECRET + "}")
    private String jwtSecret;

    private SecretKeySpec primarySecretKeySpec;
    private List<SecretKeySpec> candidateKeySpecs;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void init() {
        try {
            Set<String> secrets = new LinkedHashSet<>();
            addSecret(secrets, encryptionSecret);
            addSecret(secrets, jwtSecret);
            addSecret(secrets, DEFAULT_JWT_SECRET);
            addSecret(secrets, LEGACY_MODEL_PROVIDER_SECRET);

            if (legacyEncryptionSecrets != null && !legacyEncryptionSecrets.isBlank()) {
                for (String secret : legacyEncryptionSecrets.split(",")) {
                    addSecret(secrets, secret);
                }
            }

            if (secrets.isEmpty()) {
                throw new IllegalStateException("未配置可用的 API Key 加密密钥");
            }

            List<SecretKeySpec> keySpecs = new ArrayList<>(secrets.size());
            for (String secret : secrets) {
                keySpecs.add(buildSecretKey(secret));
            }

            primarySecretKeySpec = keySpecs.getFirst();
            candidateKeySpecs = List.copyOf(keySpecs);
        } catch (Exception ex) {
            throw new IllegalStateException("初始化 API Key 加密器失败", ex);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank() || isEncrypted(plainText)) {
            return plainText;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, primarySecretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            log.error("API Key 加密失败", ex);
            throw new IllegalStateException("API Key 加密失败", ex);
        }
    }

    public String decrypt(String cipherText) {
        return decryptWithMetadata(cipherText).getPlainText();
    }

    public DecryptionResult decryptWithMetadata(String cipherText) {
        if (cipherText == null || cipherText.isBlank() || !isEncrypted(cipherText)) {
            return new DecryptionResult(cipherText, false);
        }

        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
        } catch (Exception ex) {
            log.error("API Key 解密失败", ex);
            throw new IllegalStateException("API Key 解密失败", ex);
        }

        Exception lastException = null;
        for (int i = 0; i < candidateKeySpecs.size(); i++) {
            try {
                return new DecryptionResult(decryptWithKey(payload, candidateKeySpecs.get(i)), i > 0);
            } catch (Exception ex) {
                lastException = ex;
            }
        }

        log.error("API Key 解密失败", lastException);
        throw new IllegalStateException("API Key 解密失败", lastException);
    }

    private SecretKeySpec buildSecretKey(String secret) throws Exception {
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secretBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String decryptWithKey(byte[] payload, SecretKeySpec keySpec) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[payload.length - IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
        System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private void addSecret(Set<String> secrets, String secret) {
        if (secret != null && !secret.isBlank()) {
            secrets.add(secret.trim());
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    @Getter
    @AllArgsConstructor
    public static class DecryptionResult {

        private final String plainText;

        private final boolean usedLegacySecret;
    }
}
