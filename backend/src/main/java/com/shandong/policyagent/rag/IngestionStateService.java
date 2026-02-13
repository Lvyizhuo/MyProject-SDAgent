package com.shandong.policyagent.rag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionStateService {

    private static final String EMPTY_HASH = "";
    private final RagConfig ragConfig;
    private final ObjectMapper objectMapper;

    public List<Document> filterNewDocuments(List<Document> documents) {
        if (!ragConfig.getIncremental().isEnabled()) {
            return documents;
        }

        Set<String> fingerprints = loadFingerprints();
        List<Document> newDocuments = new ArrayList<>();
        for (Document document : documents) {
            String fingerprint = fingerprint(document);
            if (fingerprint.isBlank() || fingerprints.contains(fingerprint)) {
                continue;
            }
            newDocuments.add(document);
        }
        return newDocuments;
    }

    public void markDocumentsProcessed(List<Document> documents) {
        if (!ragConfig.getIncremental().isEnabled() || documents.isEmpty()) {
            return;
        }

        Set<String> fingerprints = loadFingerprints();
        for (Document document : documents) {
            String fingerprint = fingerprint(document);
            if (!fingerprint.isBlank()) {
                fingerprints.add(fingerprint);
            }
        }
        saveFingerprints(fingerprints);
    }

    private Set<String> loadFingerprints() {
        Path statePath = resolveStatePath(false);
        if (!Files.exists(statePath)) {
            return new LinkedHashSet<>();
        }

        try {
            Map<?, ?> state = objectMapper.readValue(statePath.toFile(), Map.class);
            Object raw = state.get("fingerprints");
            if (!(raw instanceof List<?> list)) {
                return new LinkedHashSet<>();
            }

            Set<String> fingerprints = new LinkedHashSet<>();
            for (Object item : list) {
                if (item != null) {
                    String value = String.valueOf(item).trim();
                    if (!value.isBlank()) {
                        fingerprints.add(value);
                    }
                }
            }
            return fingerprints;
        } catch (Exception e) {
            log.warn("读取增量状态文件失败，将按空状态处理: {}", statePath, e);
            return new LinkedHashSet<>();
        }
    }

    private void saveFingerprints(Set<String> fingerprints) {
        Path statePath = resolveStatePath(true);
        try {
            Path parent = statePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> payload = Map.of(
                    "updatedAt", Instant.now().toString(),
                    "fingerprints", fingerprints
            );
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), payload);
        } catch (Exception e) {
            log.error("写入增量状态文件失败: {}", statePath, e);
        }
    }

    private String fingerprint(Document document) {
        String source = String.valueOf(document.getMetadata().getOrDefault("source", ""));
        String text = document.getText() == null ? "" : document.getText().trim();
        if (source.isBlank() && text.isBlank()) {
            return EMPTY_HASH;
        }
        return sha256(source + "\n" + text);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算文档指纹失败", e);
            return EMPTY_HASH;
        }
    }

    private Path resolveStatePath(boolean forWrite) {
        String rawPath = ragConfig.getIncremental().getStateFilePath();
        Path configured = Path.of(rawPath).normalize();
        if (configured.isAbsolute()) {
            return configured;
        }

        Path userDir = Path.of(System.getProperty("user.dir")).normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(configured);
        candidates.add(userDir.resolve(configured).normalize());
        Path parent = userDir.getParent();
        if (parent != null) {
            candidates.add(parent.resolve(configured).normalize());
        }

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        // 写入时优先项目根目录（父目录）路径，其次当前目录
        if (forWrite && parent != null) {
            return parent.resolve(configured).normalize();
        }
        return userDir.resolve(configured).normalize();
    }
}
