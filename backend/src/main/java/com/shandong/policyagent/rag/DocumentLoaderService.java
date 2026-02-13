package com.shandong.policyagent.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLoaderService {

    private final RagConfig ragConfig;
    private final ObjectMapper objectMapper;

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".pdf", ".doc", ".docx", ".md", ".txt"
    );
    private static final Pattern MEDIA_URL_PATTERN = Pattern.compile(
            ".*\\.(jpg|jpeg|png|gif|bmp|webp|svg|mp4|mp3|avi|wmv|mov)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    public List<Document> loadDocumentsFromDirectory(String directoryPath) {
        List<Document> allDocuments = new ArrayList<>();
        Path basePath = resolveFirstExistingPath(directoryPath);

        if (basePath == null || !Files.exists(basePath)) {
            log.warn("文档目录不存在: {} (已尝试路径: {})", directoryPath, resolveCandidatePaths(directoryPath));
            return allDocuments;
        }

        try (Stream<Path> paths = Files.walk(basePath)) {
            List<Path> documentFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .toList();

            log.info("发现 {} 个待处理文档", documentFiles.size());

            for (Path filePath : documentFiles) {
                try {
                    List<Document> docs = loadDocument(filePath);
                    allDocuments.addAll(docs);
                    log.debug("加载文档成功: {} ({}个切片)", filePath.getFileName(), docs.size());
                } catch (Exception e) {
                    log.error("加载文档失败: {}", filePath, e);
                }
            }
        } catch (IOException e) {
            log.error("遍历文档目录失败: {}", directoryPath, e);
        }

        log.info("共加载 {} 个文档切片", allDocuments.size());
        return allDocuments;
    }

    public List<Document> loadDocument(Path filePath) {
        Resource resource = new FileSystemResource(filePath.toFile());
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        Map<String, Object> metadata = createMetadata(filePath);
        documents.forEach(doc -> doc.getMetadata().putAll(metadata));

        return documents;
    }

    public List<Document> loadDocumentFromResource(Resource resource, String fileName) {
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", fileName);
        metadata.put("fileName", fileName);
        documents.forEach(doc -> doc.getMetadata().putAll(metadata));

        return documents;
    }

    public List<Document> loadAllDefaultDocuments() {
        List<Document> localDocuments = loadDocumentsFromDirectory(ragConfig.getDocumentPath());
        List<Document> scrapedDocuments = loadScrapedPolicyDocuments();
        List<Document> documents = new ArrayList<>(localDocuments);
        documents.addAll(scrapedDocuments);
        log.info(
                "默认数据源加载完成: 本地文档 {} 条, 爬虫文档 {} 条, 合计 {} 条",
                localDocuments.size(),
                scrapedDocuments.size(),
                documents.size()
        );
        return documents;
    }

    public List<Document> loadScrapedPolicyDocuments() {
        List<Document> result = new ArrayList<>();
        RagConfig.Scraped scraped = ragConfig.getScraped();
        if (!scraped.isEnabled()) {
            return result;
        }

        for (String jsonPath : scraped.getPolicyJsonPaths()) {
            Path path = resolveFirstExistingPath(jsonPath);
            if (path == null || !Files.exists(path)) {
                continue;
            }
            result.addAll(loadScrapedPolicyDocumentsFromPath(path));
            log.info("已加载爬虫政策 JSON: {} -> {} (累计 {} 条)", jsonPath, path, result.size());
        }
        return result;
    }

    private boolean isSupportedFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private Map<String, Object> createMetadata(Path filePath) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filePath.toString());
        metadata.put("fileName", filePath.getFileName().toString());

        Path parent = filePath.getParent();
        if (parent != null) {
            metadata.put("category", parent.getFileName().toString());
        }

        return metadata;
    }

    private List<Document> loadScrapedPolicyDocumentsFromPath(Path jsonPath) {
        List<Document> documents = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    jsonPath.toFile(),
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );

            for (Map<String, Object> row : rows) {
                String url = stringVal(row.get("url"));
                String title = stringVal(row.get("title"));
                String type = stringVal(row.get("type")).toLowerCase();
                if ("other".equals(type) || isMediaUrl(url)) {
                    continue;
                }

                String content = sanitizeText(stringVal(row.get("content")));
                if (content.isBlank()) {
                    content = sanitizeText((title + "\n" + url).trim());
                }
                if (content.isBlank() || isLikelyBinaryContent(content)) {
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", url.isBlank() ? jsonPath.toString() : url);
                metadata.put("fileName", title.isBlank() ? "scraped-policy" : title);
                metadata.put("category", "scraped-policy");
                metadata.put("sourceType", "scraped-json");
                metadata.put("sourceJsonPath", jsonPath.toString());

                putIfPresent(metadata, "url", row.get("url"));
                putIfPresent(metadata, "title", row.get("title"));
                putIfPresent(metadata, "type", row.get("type"));
                putIfPresent(metadata, "source_region", row.get("source_region"));
                putIfPresent(metadata, "source_page", row.get("source_page"));
                putIfPresent(metadata, "local_path", row.get("local_path"));

                documents.add(new Document(content, metadata));
            }
        } catch (Exception e) {
            log.error("读取爬虫政策 JSON 失败: {}", jsonPath, e);
        }
        return documents;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        String stringValue = stringVal(value);
        if (!stringValue.isBlank()) {
            metadata.put(key, stringValue);
        }
    }

    private String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isMediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return MEDIA_URL_PATTERN.matcher(url.trim()).matches();
    }

    private boolean isLikelyBinaryContent(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        int controlCount = 0;
        int total = Math.min(text.length(), 2000);
        for (int i = 0; i < total; i++) {
            char c = text.charAt(i);
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                controlCount++;
            }
        }
        return ((double) controlCount / (double) total) > 0.05;
    }

    private String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u0000') {
                continue;
            }
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private Path resolveFirstExistingPath(String rawPath) {
        List<Path> candidates = resolveCandidatePaths(rawPath);
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<Path> resolveCandidatePaths(String rawPath) {
        Set<Path> candidates = new LinkedHashSet<>();
        if (rawPath == null || rawPath.isBlank()) {
            return new ArrayList<>();
        }

        Path configured = Path.of(rawPath).normalize();
        candidates.add(configured);

        if (!configured.isAbsolute()) {
            Path userDir = Path.of(System.getProperty("user.dir")).normalize();
            candidates.add(userDir.resolve(configured).normalize());
            Path parent = userDir.getParent();
            if (parent != null) {
                candidates.add(parent.resolve(configured).normalize());
            }
        }
        return new ArrayList<>(candidates);
    }
}
