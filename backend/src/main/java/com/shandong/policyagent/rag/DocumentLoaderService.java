package com.shandong.policyagent.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.shandong.policyagent.multimodal.service.VisionService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLoaderService {
    private static final int OCR_MAX_PAGES = 12;
    private static final long OCR_PROCESS_TIMEOUT_SECONDS = 20;
    private static final String PDF_OCR_PROMPT = """
            请执行严格 OCR，完整提取图片中的全部中文、数字、标点和条目编号。
            要求：
            1. 只输出识别出的正文文本，不要解释，不要总结。
            2. 保持原有段落和条目顺序。
            3. 对看不清的个别字符可以跳过，但不要编造。
            4. 如果整页几乎没有可识别文本，请返回空字符串。
            """;

    private final RagConfig ragConfig;
    private final ObjectMapper objectMapper;
    private final VisionService visionService;

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
        return loadDocumentFromResource(resource, fileName, true);
    }

    public List<Document> loadDocumentFromResource(Resource resource, String fileName, boolean allowPdfOcrFallback) {
        byte[] resourceBytes = readResourceBytes(resource, fileName);
        Resource namedResource = new ByteArrayResource(resourceBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        TikaDocumentReader reader = new TikaDocumentReader(namedResource);
        List<Document> documents = reader.get();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", fileName);
        metadata.put("fileName", fileName);
        documents.forEach(doc -> doc.getMetadata().putAll(metadata));

        if (hasExtractableText(documents) || !isPdf(fileName) || !allowPdfOcrFallback) {
            if (!allowPdfOcrFallback && isPdf(fileName) && !hasExtractableText(documents)) {
                log.info("跳过 PDF OCR 兜底，使用快速提取模式: {}", fileName);
            }
            return documents;
        }

        String ocrText = extractPdfTextWithVision(resourceBytes, fileName);
        if (ocrText.isBlank()) {
            log.warn("PDF 文本提取失败且 OCR 未获得可用文本: {}", fileName);
            return documents;
        }

        log.info("PDF OCR 兜底成功: {} | textLength={}", fileName, ocrText.length());
        return List.of(new Document(ocrText, metadata));
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

    private byte[] readResourceBytes(Resource resource, String fileName) {
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取文档资源失败: " + fileName, e);
        }
    }

    private boolean hasExtractableText(List<Document> documents) {
        return documents.stream()
                .map(Document::getText)
                .anyMatch(text -> text != null && !text.isBlank());
    }

    private boolean isPdf(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    private String extractPdfTextWithVision(byte[] pdfBytes, String fileName) {
        if (pdfBytes.length == 0) {
            return "";
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("policy-agent-pdf-ocr-");
            Path pdfPath = tempDir.resolve("source.pdf");
            Files.write(pdfPath, pdfBytes);

            Path outputPrefix = tempDir.resolve("page");
            Process process = new ProcessBuilder(
                    "pdftoppm",
                    "-png",
                    "-f", "1",
                    "-l", String.valueOf(OCR_MAX_PAGES),
                    pdfPath.toString(),
                    outputPrefix.toString()
            ).start();

            boolean finished = process.waitFor(OCR_PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("pdftoppm 执行超时，已中止 OCR 兜底: {}", fileName);
                return "";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String error = new String(process.getErrorStream().readAllBytes());
                log.warn("pdftoppm 执行失败: {} | exitCode={} | error={}", fileName, exitCode, error);
                return "";
            }

            List<Path> pageImages;
            try (var stream = Files.list(tempDir)) {
                pageImages = stream
                        .filter(path -> path.getFileName().toString().startsWith("page-"))
                        .filter(path -> path.getFileName().toString().endsWith(".png"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }

            if (pageImages.isEmpty()) {
                log.warn("PDF OCR 未生成页面图片: {}", fileName);
                return "";
            }

            StringBuilder combinedText = new StringBuilder();
            for (int i = 0; i < pageImages.size(); i++) {
                byte[] imageBytes = Files.readAllBytes(pageImages.get(i));
                String pageText = visionService.analyzeBase64Image(
                        Base64.getEncoder().encodeToString(imageBytes),
                        "png",
                        PDF_OCR_PROMPT
                ).trim();
                if (pageText.isBlank()) {
                    continue;
                }
                if (!combinedText.isEmpty()) {
                    combinedText.append("\n\n");
                }
                combinedText.append("第").append(i + 1).append("页\n").append(pageText);
            }

            return combinedText.toString().trim();
        } catch (Exception e) {
            log.warn("PDF OCR 兜底失败: {}", fileName, e);
            return "";
        } finally {
            if (tempDir != null) {
                try (var walk = Files.walk(tempDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
                } catch (IOException ignored) {
                }
            }
        }
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
