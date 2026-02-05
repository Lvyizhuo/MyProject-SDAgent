package com.shandong.policyagent.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".pdf", ".doc", ".docx", ".md", ".txt"
    );

    public List<Document> loadDocumentsFromDirectory(String directoryPath) {
        List<Document> allDocuments = new ArrayList<>();
        Path basePath = Path.of(directoryPath);

        if (!Files.exists(basePath)) {
            log.warn("文档目录不存在: {}", directoryPath);
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
        return loadDocumentsFromDirectory(ragConfig.getDocumentPath());
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
}
