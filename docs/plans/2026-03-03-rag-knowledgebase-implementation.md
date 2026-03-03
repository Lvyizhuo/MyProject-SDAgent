# RAG 知识库重构实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现完整的管理员控制台知识库管理功能，包括文档上传、文件夹管理、多嵌入模型支持、MinIO 存储、RAG 检索集成。

**Architecture:** 基于现有代码进行轻量级增强，复用 DocumentLoaderService、TextSplitterService，新增 KnowledgeService、StorageService、EmbeddingService，支持多 VectorStore 按模型隔离存储。

**Tech Stack:** Spring Boot 3.4, Spring AI 1.0.3, PostgreSQL + pgvector, MinIO, React 19, Ollama

---

## 阶段 1：基础设施与数据模型

### Task 1.1: 更新 docker-compose.yml 添加 MinIO

**Files:**
- Modify: `backend/docker-compose.yml`

**Step 1:** 读取现有 docker-compose.yml

**Step 2:** 添加 MinIO 服务配置

```yaml
  minio:
    image: minio/minio:latest
    container_name: policy-agent-minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3
```

**Step 3:** 添加 minio_data 到 volumes 部分

```yaml
volumes:
  postgres_data:
  redis_data:
  minio_data:
```

**Step 4:** 验证 docker-compose.yml 语法

**Step 5:** Commit

```bash
git add backend/docker-compose.yml
git commit -m "feat: add MinIO service to docker-compose"
```

---

### Task 1.2: 添加 MinIO Maven 依赖

**Files:**
- Modify: `backend/pom.xml`

**Step 1:** 在 dependencies 部分添加 MinIO 客户端

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.11</version>
</dependency>
```

**Step 2:** 验证 pom.xml 语法

**Step 3:** Commit

```bash
git add backend/pom.xml
git commit -m "feat: add MinIO client dependency"
```

---

### Task 1.3: 创建 JPA 实体 - KnowledgeFolder

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/entity/KnowledgeFolder.java`

**Step 1:** 创建实体类

```java
package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "knowledge_folders")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private KnowledgeFolder parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<KnowledgeFolder> children = new ArrayList<>();

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true, length = 500)
    private String path;

    @Column(nullable = false)
    private Integer depth = 1;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/entity/KnowledgeFolder.java
git commit -m "feat: add KnowledgeFolder entity"
```

---

### Task 1.4: 创建 JPA 实体 - KnowledgeDocument 和 DocumentStatus 枚举

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/entity/KnowledgeDocument.java`
- Create: `backend/src/main/java/com/shandong/policyagent/entity/DocumentStatus.java`

**Step 1:** 创建 DocumentStatus 枚举

```java
package com.shandong.policyagent.entity;

public enum DocumentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
```

**Step 2:** 创建 KnowledgeDocument 实体

```java
package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "knowledge_documents")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private KnowledgeFolder folder;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 100)
    private String fileType;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "storage_bucket", nullable = false, length = 100)
    private String storageBucket;

    @Column(name = "embedding_model", nullable = false, length = 200)
    private String embeddingModel;

    @Column(name = "vector_table_name", nullable = false, length = 100)
    private String vectorTableName;

    @Column(length = 200)
    private String category;

    @Column(columnDefinition = "VARCHAR(500)[]")
    private List<String> tags;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(length = 500)
    private String source;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**Step 3:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/entity/DocumentStatus.java
git add backend/src/main/java/com/shandong/policyagent/entity/KnowledgeDocument.java
git commit -m "feat: add KnowledgeDocument entity and DocumentStatus enum"
```

---

### Task 1.5: 创建 JPA 实体 - KnowledgeConfig

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/entity/KnowledgeConfig.java`

**Step 1:** 创建实体类

```java
package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_config")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeConfig {

    @Id
    private Long id = 1L;

    @Column(name = "chunk_size")
    @Builder.Default
    private Integer chunkSize = 1600;

    @Column(name = "chunk_overlap")
    @Builder.Default
    private Integer chunkOverlap = 300;

    @Column(name = "min_chunk_size_chars")
    @Builder.Default
    private Integer minChunkSizeChars = 350;

    @Column(name = "no_split_max_chars")
    @Builder.Default
    private Integer noSplitMaxChars = 6000;

    @Column(name = "default_embedding_model", length = 200)
    @Builder.Default
    private String defaultEmbeddingModel = "ollama:qwen3-embedding";

    @Column(name = "minio_endpoint", length = 500)
    private String minioEndpoint;

    @Column(name = "minio_access_key", length = 200)
    private String minioAccessKey;

    @Column(name = "minio_secret_key", length = 200)
    private String minioSecretKey;

    @Column(name = "minio_bucket_name", length = 100)
    @Builder.Default
    private String minioBucketName = "policy-documents";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/entity/KnowledgeConfig.java
git commit -m "feat: add KnowledgeConfig entity"
```

---

### Task 1.6: 创建 Repository 接口

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/repository/KnowledgeFolderRepository.java`
- Create: `backend/src/main/java/com/shandong/policyagent/repository/KnowledgeDocumentRepository.java`
- Create: `backend/src/main/java/com/shandong/policyagent/repository/KnowledgeConfigRepository.java`

**Step 1:** 创建 KnowledgeFolderRepository

```java
package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.KnowledgeFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeFolderRepository extends JpaRepository<KnowledgeFolder, Long> {

    List<KnowledgeFolder> findByParentIsNullOrderBySortOrderAsc();

    Optional<KnowledgeFolder> findByPath(String path);

    @Query("SELECT f FROM KnowledgeFolder f LEFT JOIN FETCH f.children WHERE f.parent IS NULL ORDER BY f.sortOrder ASC")
    List<KnowledgeFolder> findAllRootFoldersWithChildren();
}
```

**Step 2:** 创建 KnowledgeDocumentRepository

```java
package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Page<KnowledgeDocument> findByFolderId(Long folderId, Pageable pageable);

    Page<KnowledgeDocument> findByStatus(DocumentStatus status, Pageable pageable);

    Page<KnowledgeDocument> findByCategory(String category, Pageable pageable);

    @Query("SELECT d FROM KnowledgeDocument d WHERE d.embeddingModel = :embeddingModel")
    List<KnowledgeDocument> findByEmbeddingModel(@Param("embeddingModel") String embeddingModel);

    @Query("SELECT d FROM KnowledgeDocument d WHERE :tag MEMBER OF d.tags")
    Page<KnowledgeDocument> findByTag(@Param("tag") String tag, Pageable pageable);
}
```

**Step 3:** 创建 KnowledgeConfigRepository

```java
package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.KnowledgeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeConfigRepository extends JpaRepository<KnowledgeConfig, Long> {
}
```

**Step 4:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/repository/KnowledgeFolderRepository.java
git add backend/src/main/java/com/shandong/policyagent/repository/KnowledgeDocumentRepository.java
git add backend/src/main/java/com/shandong/policyagent/repository/KnowledgeConfigRepository.java
git commit -m "feat: add Knowledge repositories"
```

---

### Task 1.7: 更新 application.yml 配置

**Files:**
- Modify: `backend/src/main/resources/application.yml`

**Step 1:** 更新 pgvector dimensions 为 4096

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        dimensions: 4096
```

**Step 2:** 添加 knowledge 配置

```yaml
app:
  knowledge:
    minio:
      endpoint: http://localhost:9000
      access-key: minioadmin
      secret-key: minioadmin
      bucket-name: policy-documents

    embedding:
      default-model: ollama:qwen3-embedding
      models:
        - id: ollama:qwen3-embedding
          provider: ollama
          base-url: http://localhost:11434
          model-name: qwen3-embedding:latest
          dimensions: 4096
          vector-table: vector_store_ollama_qwen3
        - id: dashscope:text-embedding-v3
          provider: dashscope
          api-key: ${DASHSCOPE_API_KEY}
          model-name: text-embedding-v3
          dimensions: 1024
          vector-table: vector_store_dashscope_v3
```

**Step 3:** 禁用旧的 rag 自动加载

```yaml
app:
  rag:
    bootstrap:
      auto-load-on-startup: false
    scraped:
      enabled: false
```

**Step 4:** Commit

```bash
git add backend/src/main/resources/application.yml
git commit -m "feat: update application.yml for knowledge base"
```

---

## 阶段 2：后端核心服务

### Task 2.1: 创建 MinIO 配置类

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/config/MinioConfig.java`

**Step 1:** 创建配置类

```java
package com.shandong.policyagent.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.knowledge.minio")
public class MinioConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/config/MinioConfig.java
git commit -m "feat: add MinioConfig"
```

---

### Task 2.2: 创建 EmbeddingModel 配置类

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/config/EmbeddingModelConfig.java`

**Step 1:** 创建配置类

```java
package com.shandong.policyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.knowledge.embedding")
public class EmbeddingModelConfig {

    private String defaultModel;
    private List<EmbeddingModel> models = new ArrayList<>();

    @Data
    public static class EmbeddingModel {
        private String id;
        private String provider;
        private String baseUrl;
        private String modelName;
        private String apiKey;
        private Integer dimensions;
        private String vectorTable;
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/config/EmbeddingModelConfig.java
git commit -m "feat: add EmbeddingModelConfig"
```

---

### Task 2.3: 创建 StorageService

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/rag/StorageService.java`

**Step 1:** 创建服务类

```java
package com.shandong.policyagent.rag;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucketName()).build()
            );
            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioConfig.getBucketName()).build()
                );
                log.info("Created MinIO bucket: {}", minioConfig.getBucketName());
            }
        } catch (Exception e) {
            log.error("Failed to ensure MinIO bucket exists", e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    public String storeFile(MultipartFile file, String folderPath) {
        ensureBucketExists();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeFolderPath = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
        safeFolderPath = safeFolderPath.replace("/", "_");
        String storagePath = String.format("%s/%s_%s_%s",
                safeFolderPath, timestamp, uuid, file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Stored file to MinIO: {}", storagePath);
            return storagePath;
        } catch (Exception e) {
            log.error("Failed to store file to MinIO", e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public InputStream getFile(String storagePath) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get file from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to get file", e);
        }
    }

    public String getPresignedUrl(String storagePath, int expirationMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .method(Method.GET)
                            .expiry(expirationMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", storagePath, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    public void deleteFile(String storagePath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", storagePath);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/rag/StorageService.java
git commit -m "feat: add StorageService for MinIO"
```

---

### Task 2.4: 创建 EmbeddingService

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/rag/EmbeddingService.java`

**Step 1:** 创建服务类

```java
package com.shandong.policyagent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModelConfig embeddingModelConfig;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    private final Map<String, EmbeddingModel> modelCache = new HashMap<>();

    public List<float[]> embedDocuments(String modelId, List<Document> documents) {
        List<String> texts = documents.stream()
                .map(Document::getText)
                .toList();
        return embedTexts(modelId, texts);
    }

    public List<float[]> embedTexts(String modelId, List<String> texts) {
        EmbeddingModelConfig.EmbeddingModel modelConfig = getModelConfig(modelId);

        if ("ollama".equalsIgnoreCase(modelConfig.getProvider())) {
            return embedWithOllama(modelConfig, texts);
        } else if ("dashscope".equalsIgnoreCase(modelConfig.getProvider())) {
            return embedWithDashScope(modelConfig, texts);
        }

        throw new IllegalArgumentException("Unsupported embedding provider: " + modelConfig.getProvider());
    }

    public EmbeddingModel getSpringAiEmbeddingModel(String modelId) {
        return modelCache.computeIfAbsent(modelId, id -> {
            EmbeddingModelConfig.EmbeddingModel modelConfig = getModelConfig(id);
            if ("dashscope".equalsIgnoreCase(modelConfig.getProvider())) {
                String apiKey = resolveApiKey(modelConfig.getApiKey());
                OpenAiApi openAiApi = new OpenAiApi(modelConfig.getBaseUrl(), apiKey);
                return new OpenAiEmbeddingModel(openAiApi,
                        OpenAiEmbeddingOptions.builder()
                                .withModel(modelConfig.getModelName())
                                .build());
            }
            throw new IllegalArgumentException("Spring AI EmbeddingModel not supported for provider: " + modelConfig.getProvider());
        });
    }

    public List<EmbeddingModelConfig.EmbeddingModel> getAvailableModels() {
        return embeddingModelConfig.getModels();
    }

    public EmbeddingModelConfig.EmbeddingModel getDefaultModel() {
        return getModelConfig(embeddingModelConfig.getDefaultModel());
    }

    private EmbeddingModelConfig.EmbeddingModel getModelConfig(String modelId) {
        return embeddingModelConfig.getModels().stream()
                .filter(m -> m.getId().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Embedding model not found: " + modelId));
    }

    private List<float[]> embedWithOllama(EmbeddingModelConfig.EmbeddingModel modelConfig, List<String> texts) {
        RestClient restClient = restClientBuilder.baseUrl(modelConfig.getBaseUrl()).build();
        List<float[]> embeddings = new ArrayList<>();

        for (String text : texts) {
            OllamaEmbeddingRequest request = new OllamaEmbeddingRequest();
            request.setModel(modelConfig.getModelName());
            request.setPrompt(text);

            OllamaEmbeddingResponse response = restClient.post()
                    .uri("/api/embeddings")
                    .body(request)
                    .retrieve()
                    .body(OllamaEmbeddingResponse.class);

            if (response != null && response.getEmbedding() != null) {
                embeddings.add(toFloatArray(response.getEmbedding()));
            } else {
                throw new RuntimeException("Failed to get embedding from Ollama");
            }
        }

        return embeddings;
    }

    private List<float[]> embedWithDashScope(EmbeddingModelConfig.EmbeddingModel modelConfig, List<String> texts) {
        EmbeddingModel embeddingModel = getSpringAiEmbeddingModel(modelConfig.getId());
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            float[] embedding = embeddingModel.embed(text);
            embeddings.add(embedding);
        }
        return embeddings;
    }

    private String resolveApiKey(String apiKey) {
        if (apiKey != null && apiKey.startsWith("${") && apiKey.endsWith("}")) {
            String envVar = apiKey.substring(2, apiKey.length() - 1);
            String value = System.getenv(envVar);
            if (value != null) {
                return value;
            }
        }
        return apiKey;
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }

    @Data
    private static class OllamaEmbeddingRequest {
        private String model;
        private String prompt;
    }

    @Data
    private static class OllamaEmbeddingResponse {
        private List<Double> embedding;
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/rag/EmbeddingService.java
git commit -m "feat: add EmbeddingService for multi-model support"
```

---

### Task 2.5: 创建 MultiVectorStoreService

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/rag/MultiVectorStoreService.java`

**Step 1:** 创建服务类

```java
package com.shandong.policyagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiVectorStoreService {

    private final DataSource dataSource;
    private final EmbeddingModelConfig embeddingModelConfig;
    private final EmbeddingService embeddingService;

    private final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    public VectorStore getVectorStore(String modelId) {
        return vectorStoreCache.computeIfAbsent(modelId, id -> {
            EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(id);
            initializeVectorTable(modelConfig.getVectorTable(), modelConfig.getDimensions());
            return createVectorStore(modelConfig);
        });
    }

    public void addDocuments(String modelId, List<Document> documents) {
        VectorStore vectorStore = getVectorStore(modelId);
        vectorStore.add(documents);
        log.info("Added {} documents to vector store for model: {}", documents.size(), modelId);
    }

    public void deleteDocuments(String modelId, List<String> documentIds) {
        VectorStore vectorStore = getVectorStore(modelId);
        vectorStore.delete(documentIds);
        log.info("Deleted {} documents from vector store for model: {}", documentIds.size(), modelId);
    }

    public List<Document> similaritySearch(String modelId, String query, int topK) {
        VectorStore vectorStore = getVectorStore(modelId);
        return vectorStore.similaritySearch(query, topK);
    }

    private void initializeVectorTable(String tableName, int dimensions) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String checkTableSql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)";
        Boolean tableExists = jdbcTemplate.queryForObject(checkTableSql, Boolean.class, tableName);

        if (Boolean.FALSE.equals(tableExists)) {
            log.info("Creating vector table: {} with dimensions: {}", tableName, dimensions);
            String createTableSql = String.format("""
                CREATE TABLE %s (
                    id UUID PRIMARY KEY,
                    content TEXT,
                    metadata JSONB,
                    embedding vector(%d)
                )
                """, tableName, dimensions);
            jdbcTemplate.execute(createTableSql);

            String createIndexSql = String.format("""
                CREATE INDEX ON %s USING hnsw (embedding vector_cosine_ops)
                """, tableName);
            jdbcTemplate.execute(createIndexSql);

            log.info("Vector table created: {}", tableName);
        }
    }

    private VectorStore createVectorStore(EmbeddingModelConfig.EmbeddingModel modelConfig) {
        org.springframework.ai.embedding.EmbeddingModel springAiModel;
        if ("dashscope".equalsIgnoreCase(modelConfig.getProvider())) {
            springAiModel = embeddingService.getSpringAiEmbeddingModel(modelConfig.getId());
        } else {
            springAiModel = new org.springframework.ai.embedding.EmbeddingModel() {
                @Override
                public float[] embed(String text) {
                    return embeddingService.embedTexts(modelConfig.getId(), List.of(text)).get(0);
                }

                @Override
                public int dimensions() {
                    return modelConfig.getDimensions();
                }
            };
        }

        return PgVectorStore.builder(dataSource)
                .embeddingModel(springAiModel)
                .dimensions(modelConfig.getDimensions())
                .tableName(modelConfig.getVectorTable())
                .initializeSchema(false)
                .build();
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/rag/MultiVectorStoreService.java
git commit -m "feat: add MultiVectorStoreService for per-model vector stores"
```

---

### Task 2.6: 创建 KnowledgeService

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/rag/KnowledgeService.java`

**Step 1:** 创建服务类

```java
package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.config.MinioConfig;
import com.shandong.policyagent.entity.*;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import com.shandong.policyagent.repository.KnowledgeDocumentRepository;
import com.shandong.policyagent.repository.KnowledgeFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeFolderRepository folderRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeConfigRepository configRepository;

    private final StorageService storageService;
    private final DocumentLoaderService documentLoaderService;
    private final TextSplitterService textSplitterService;
    private final EmbeddingService embeddingService;
    private final MultiVectorStoreService multiVectorStoreService;
    private final KnowledgeConfig knowledgeConfig;

    @Transactional
    public KnowledgeFolder createFolder(Long parentId, String name, String description, User createdBy) {
        KnowledgeFolder parent = null;
        String path;
        int depth = 1;

        if (parentId != null) {
            parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
            path = parent.getPath() + "/" + name;
            depth = parent.getDepth() + 1;
        } else {
            path = "/" + name;
        }

        if (folderRepository.findByPath(path).isPresent()) {
            throw new IllegalArgumentException("Folder path already exists: " + path);
        }

        KnowledgeFolder folder = KnowledgeFolder.builder()
                .parent(parent)
                .name(name)
                .description(description)
                .path(path)
                .depth(depth)
                .createdBy(createdBy)
                .build();

        return folderRepository.save(folder);
    }

    public List<KnowledgeFolder> getFolderTree() {
        return folderRepository.findAllRootFoldersWithChildren();
    }

    @Transactional
    public KnowledgeFolder updateFolder(Long id, String name, String description) {
        KnowledgeFolder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        folder.setName(name);
        folder.setDescription(description);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(Long id) {
        folderRepository.deleteById(id);
    }

    @Transactional
    public KnowledgeDocument uploadDocument(
            MultipartFile file,
            Long folderId,
            String title,
            String embeddingModelId,
            String category,
            List<String> tags,
            LocalDate publishDate,
            String source,
            LocalDate validFrom,
            LocalDate validTo,
            String summary,
            User createdBy) {

        KnowledgeFolder folder = folderId != null ? folderRepository.findById(folderId).orElse(null) : null;
        String folderPath = folder != null ? folder.getPath() : "/";

        EmbeddingModelConfig.EmbeddingModel modelConfig = embeddingService.getModelConfig(embeddingModelId);

        String storagePath = storageService.storeFile(file, folderPath);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .folder(folder)
                .title(title != null ? title : file.getOriginalFilename())
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .storagePath(storagePath)
                .storageBucket(knowledgeConfig.getBucketName())
                .embeddingModel(embeddingModelId)
                .vectorTableName(modelConfig.getVectorTable())
                .category(category)
                .tags(tags)
                .publishDate(publishDate)
                .source(source)
                .validFrom(validFrom)
                .validTo(validTo)
                .summary(summary)
                .status(DocumentStatus.PENDING)
                .createdBy(createdBy)
                .build();

        document = documentRepository.save(document);

        processDocumentAsync(document.getId());

        return document;
    }

    @Transactional
    public void processDocumentAsync(Long documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        try {
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            InputStream fileStream = storageService.getFile(document.getStoragePath());
            org.springframework.core.io.Resource resource = new InputStreamResource(fileStream) {
                @Override
                public String getFilename() {
                    return document.getFileName();
                }
            };

            List<Document> loadedDocs = documentLoaderService.loadDocumentFromResource(resource, document.getFileName());

            List<Document> splitDocs = textSplitterService.splitDocuments(loadedDocs);

            for (Document doc : splitDocs) {
                doc.getMetadata().put("knowledgeDocumentId", documentId);
                doc.getMetadata().put("sourceTitle", document.getTitle());
                if (document.getFolder() != null) {
                    doc.getMetadata().put("folderPath", document.getFolder().getPath());
                }
            }

            multiVectorStoreService.addDocuments(document.getEmbeddingModel(), splitDocs);

            document.setStatus(DocumentStatus.COMPLETED);
            document.setChunkCount(splitDocs.size());
            documentRepository.save(document);

            log.info("Document processed successfully: {} ({} chunks)", documentId, splitDocs.size());

        } catch (Exception e) {
            log.error("Failed to process document: {}", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        }
    }

    public Page<KnowledgeDocument> listDocuments(Long folderId, String category, String tag, DocumentStatus status, Pageable pageable) {
        if (folderId != null) {
            return documentRepository.findByFolderId(folderId, pageable);
        } else if (status != null) {
            return documentRepository.findByStatus(status, pageable);
        } else if (category != null) {
            return documentRepository.findByCategory(category, pageable);
        } else if (tag != null) {
            return documentRepository.findByTag(tag, pageable);
        }
        return documentRepository.findAll(pageable);
    }

    public Optional<KnowledgeDocument> getDocument(Long id) {
        return documentRepository.findById(id);
    }

    public InputStream downloadDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return storageService.getFile(document.getStoragePath());
    }

    public String getDocumentPreviewUrl(Long id, int expirationMinutes) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return storageService.getPresignedUrl(document.getStoragePath(), expirationMinutes);
    }

    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        List<String> vectorIds = new ArrayList<>();
        multiVectorStoreService.deleteDocuments(document.getEmbeddingModel(), vectorIds);

        storageService.deleteFile(document.getStoragePath());

        documentRepository.deleteById(id);
    }

    @Transactional
    public void reingestDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        document.setStatus(DocumentStatus.PENDING);
        document.setErrorMessage(null);
        documentRepository.save(document);
        processDocumentAsync(id);
    }

    public KnowledgeConfig getConfig() {
        return configRepository.findById(1L)
                .orElseGet(() -> configRepository.save(KnowledgeConfig.builder().build()));
    }

    @Transactional
    public KnowledgeConfig updateConfig(KnowledgeConfig config) {
        config.setId(1L);
        return configRepository.save(config);
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/rag/KnowledgeService.java
git commit -m "feat: add KnowledgeService"
```

---

### Task 2.7: 创建 KnowledgeConfigInitializer

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/rag/KnowledgeConfigInitializer.java`

**Step 1:** 创建初始化器

```java
package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.MinioConfig;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.repository.KnowledgeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeConfigInitializer implements CommandLineRunner {

    private final KnowledgeConfigRepository configRepository;
    private final StorageService storageService;
    private final MinioConfig minioConfig;

    @Override
    public void run(String... args) {
        if (configRepository.findById(1L).isEmpty()) {
            KnowledgeConfig config = KnowledgeConfig.builder()
                    .chunkSize(1600)
                    .chunkOverlap(300)
                    .minChunkSizeChars(350)
                    .noSplitMaxChars(6000)
                    .defaultEmbeddingModel("ollama:qwen3-embedding")
                    .minioEndpoint(minioConfig.getEndpoint())
                    .minioAccessKey(minioConfig.getAccessKey())
                    .minioSecretKey(minioConfig.getSecretKey())
                    .minioBucketName(minioConfig.getBucketName())
                    .build();
            configRepository.save(config);
            log.info("Initialized default knowledge config");
        }

        try {
            storageService.ensureBucketExists();
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket - is MinIO running?", e);
        }
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/rag/KnowledgeConfigInitializer.java
git commit -m "feat: add KnowledgeConfigInitializer"
```

---

## 阶段 3：后端 API

### Task 3.1: 创建 DTO 类

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/model/dto/CreateFolderRequest.java`
- Create: `backend/src/main/java/com/shandong/policyagent/model/dto/UpdateFolderRequest.java`
- Create: `backend/src/main/java/com/shandong/policyagent/model/dto/FolderTreeResponse.java`
- Create: `backend/src/main/java/com/shandong/policyagent/model/dto/DocumentResponse.java`
- Create: `backend/src/main/java/com/shandong/policyagent/model/dto/UpdateKnowledgeConfigRequest.java`
- Create: `backend/src/main/java/com/shandong/policyagent/model/dto/EmbeddingModelResponse.java`

**Step 1:** 创建 CreateFolderRequest

```java
package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFolderRequest {
    private Long parentId;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;
}
```

**Step 2:** 创建 UpdateFolderRequest

```java
package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFolderRequest {
    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;
}
```

**Step 3:** 创建 FolderTreeResponse

```java
package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderTreeResponse {
    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private String path;
    private Integer depth;
    @Builder.Default
    private List<FolderTreeResponse> children = new ArrayList<>();
}
```

**Step 4:** 创建 DocumentResponse

```java
package com.shandong.policyagent.model.dto;

import com.shandong.policyagent.entity.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private Long folderId;
    private String folderPath;
    private String title;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String embeddingModel;
    private String category;
    private List<String> tags;
    private LocalDate publishDate;
    private String source;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String summary;
    private DocumentStatus status;
    private String errorMessage;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Step 5:** 创建 UpdateKnowledgeConfigRequest

```java
package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateKnowledgeConfigRequest {
    @Min(100)
    @Max(10000)
    private Integer chunkSize;

    @Min(0)
    @Max(2000)
    private Integer chunkOverlap;

    @Min(50)
    @Max(2000)
    private Integer minChunkSizeChars;

    @Min(1000)
    @Max(50000)
    private Integer noSplitMaxChars;

    @Size(max = 200)
    private String defaultEmbeddingModel;

    @Size(max = 500)
    private String minioEndpoint;

    @Size(max = 200)
    private String minioAccessKey;

    @Size(max = 200)
    private String minioSecretKey;

    @Size(max = 100)
    private String minioBucketName;
}
```

**Step 6:** 创建 EmbeddingModelResponse

```java
package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingModelResponse {
    private String id;
    private String name;
    private String provider;
    private Integer dimensions;
    private boolean isDefault;
}
```

**Step 7:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/model/dto/CreateFolderRequest.java
git add backend/src/main/java/com/shandong/policyagent/model/dto/UpdateFolderRequest.java
git add backend/src/main/java/com/shandong/policyagent/model/dto/FolderTreeResponse.java
git add backend/src/main/java/com/shandong/policyagent/model/dto/DocumentResponse.java
git add backend/src/main/java/com/shandong/policyagent/model/dto/UpdateKnowledgeConfigRequest.java
git add backend/src/main/java/com/shandong/policyagent/model/dto/EmbeddingModelResponse.java
git commit -m "feat: add Knowledge DTOs"
```

---

### Task 3.2: 创建 AdminKnowledgeController

**Files:**
- Create: `backend/src/main/java/com/shandong/policyagent/controller/AdminKnowledgeController.java`

**Step 1:** 创建 Controller

```java
package com.shandong.policyagent.controller;

import com.shandong.policyagent.config.EmbeddingModelConfig;
import com.shandong.policyagent.entity.DocumentStatus;
import com.shandong.policyagent.entity.KnowledgeConfig;
import com.shandong.policyagent.entity.KnowledgeDocument;
import com.shandong.policyagent.entity.KnowledgeFolder;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.dto.*;
import com.shandong.policyagent.rag.EmbeddingService;
import com.shandong.policyagent.rag.KnowledgeService;
import com.shandong.policyagent.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;
    private final EmbeddingService embeddingService;

    @PostMapping("/folders")
    public ResponseEntity<FolderTreeResponse> createFolder(@Valid @RequestBody CreateFolderRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        KnowledgeFolder folder = knowledgeService.createFolder(
                request.getParentId(),
                request.getName(),
                request.getDescription(),
                currentUser
        );
        return ResponseEntity.ok(toFolderTreeResponse(folder));
    }

    @GetMapping("/folders")
    public ResponseEntity<Map<String, Object>> getFolderTree() {
        List<KnowledgeFolder> folders = knowledgeService.getFolderTree();
        List<FolderTreeResponse> responses = folders.stream()
                .map(this::toFolderTreeResponse)
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("folders", responses);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<FolderTreeResponse> updateFolder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFolderRequest request) {
        KnowledgeFolder folder = knowledgeService.updateFolder(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(toFolderTreeResponse(folder));
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable Long id) {
        knowledgeService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "publishDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishDate,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "validFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @RequestParam(value = "validTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
            @RequestParam(value = "summary", required = false) String summary) {

        User currentUser = SecurityUtils.getCurrentUser();

        if (embeddingModel == null) {
            embeddingModel = knowledgeService.getConfig().getDefaultEmbeddingModel();
        }

        KnowledgeDocument document = knowledgeService.uploadDocument(
                file, folderId, title, embeddingModel, category, tags,
                publishDate, source, validFrom, validTo, summary, currentUser
        );

        return ResponseEntity.ok(toDocumentResponse(document));
    }

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
        Page<KnowledgeDocument> documentPage = knowledgeService.listDocuments(
                folderId, category, tag, status, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("content", documentPage.getContent().stream()
                .map(this::toDocumentResponse).toList());
        result.put("page", documentPage.getNumber());
        result.put("size", documentPage.getSize());
        result.put("totalElements", documentPage.getTotalElements());
        result.put("totalPages", documentPage.getTotalPages());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        return knowledgeService.getDocument(id)
                .map(doc -> ResponseEntity.ok(toDocumentResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long id) {
        KnowledgeDocument document = knowledgeService.getDocument(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        InputStream inputStream = knowledgeService.downloadDocument(id);
        InputStreamResource resource = new InputStreamResource(inputStream);

        String encodedFileName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .contentLength(document.getFileSize())
                .body(resource);
    }

    @GetMapping("/documents/{id}/preview")
    public ResponseEntity<Map<String, String>> getDocumentPreview(@PathVariable Long id) {
        String previewUrl = knowledgeService.getDocumentPreviewUrl(id, 60);
        Map<String, String> result = new HashMap<>();
        result.put("previewUrl", previewUrl);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/{id}/reingest")
    public ResponseEntity<DocumentResponse> reingestDocument(@PathVariable Long id) {
        knowledgeService.reingestDocument(id);
        return knowledgeService.getDocument(id)
                .map(doc -> ResponseEntity.ok(toDocumentResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/documents/batch-delete")
    public ResponseEntity<Void> batchDeleteDocuments(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        for (Long id : ids) {
            knowledgeService.deleteDocument(id);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config")
    public ResponseEntity<KnowledgeConfig> getConfig() {
        return ResponseEntity.ok(knowledgeService.getConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<KnowledgeConfig> updateConfig(@Valid @RequestBody UpdateKnowledgeConfigRequest request) {
        KnowledgeConfig config = knowledgeService.getConfig();
        if (request.getChunkSize() != null) config.setChunkSize(request.getChunkSize());
        if (request.getChunkOverlap() != null) config.setChunkOverlap(request.getChunkOverlap());
        if (request.getMinChunkSizeChars() != null) config.setMinChunkSizeChars(request.getMinChunkSizeChars());
        if (request.getNoSplitMaxChars() != null) config.setNoSplitMaxChars(request.getNoSplitMaxChars());
        if (request.getDefaultEmbeddingModel() != null) config.setDefaultEmbeddingModel(request.getDefaultEmbeddingModel());
        if (request.getMinioEndpoint() != null) config.setMinioEndpoint(request.getMinioEndpoint());
        if (request.getMinioAccessKey() != null) config.setMinioAccessKey(request.getMinioAccessKey());
        if (request.getMinioSecretKey() != null) config.setMinioSecretKey(request.getMinioSecretKey());
        if (request.getMinioBucketName() != null) config.setMinioBucketName(request.getMinioBucketName());
        return ResponseEntity.ok(knowledgeService.updateConfig(config));
    }

    @GetMapping("/embedding-models")
    public ResponseEntity<Map<String, Object>> getEmbeddingModels() {
        EmbeddingModelConfig.EmbeddingModel defaultModel = embeddingService.getDefaultModel();
        List<EmbeddingModelResponse> models = embeddingService.getAvailableModels().stream()
                .map(m -> EmbeddingModelResponse.builder()
                        .id(m.getId())
                        .name(m.getProvider() + " - " + m.getModelName())
                        .provider(m.getProvider())
                        .dimensions(m.getDimensions())
                        .isDefault(m.getId().equals(defaultModel.getId()))
                        .build())
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("models", models);
        return ResponseEntity.ok(result);
    }

    private FolderTreeResponse toFolderTreeResponse(KnowledgeFolder folder) {
        return FolderTreeResponse.builder()
                .id(folder.getId())
                .parentId(folder.getParent() != null ? folder.getParent().getId() : null)
                .name(folder.getName())
                .description(folder.getDescription())
                .path(folder.getPath())
                .depth(folder.getDepth())
                .children(folder.getChildren() != null
                        ? folder.getChildren().stream().map(this::toFolderTreeResponse).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private DocumentResponse toDocumentResponse(KnowledgeDocument doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .folderId(doc.getFolder() != null ? doc.getFolder().getId() : null)
                .folderPath(doc.getFolder() != null ? doc.getFolder().getPath() : "/")
                .title(doc.getTitle())
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .fileType(doc.getFileType())
                .embeddingModel(doc.getEmbeddingModel())
                .category(doc.getCategory())
                .tags(doc.getTags())
                .publishDate(doc.getPublishDate())
                .source(doc.getSource())
                .validFrom(doc.getValidFrom())
                .validTo(doc.getValidTo())
                .summary(doc.getSummary())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .chunkCount(doc.getChunkCount())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
```

**Step 2:** Commit

```bash
git add backend/src/main/java/com/shandong/policyagent/controller/AdminKnowledgeController.java
git commit -m "feat: add AdminKnowledgeController"
```

---

## 阶段 4：前端开发

### Task 4.1: 创建 adminKnowledgeApi.js

**Files:**
- Create: `frontend/src/services/adminKnowledgeApi.js`

**Step 1:** 创建 API 服务

```javascript
import api from './api';

const ADMIN_KNOWLEDGE_BASE = '/api/admin/knowledge';

const adminKnowledgeApi = {
  getFolderTree: async () => {
    const response = await api.get(`${ADMIN_KNOWLEDGE_BASE}/folders`);
    return response.data;
  },

  createFolder: async (data) => {
    const response = await api.post(`${ADMIN_KNOWLEDGE_BASE}/folders`, data);
    return response.data;
  },

  updateFolder: async (id, data) => {
    const response = await api.put(`${ADMIN_KNOWLEDGE_BASE}/folders/${id}`, data);
    return response.data;
  },

  deleteFolder: async (id) => {
    await api.delete(`${ADMIN_KNOWLEDGE_BASE}/folders/${id}`);
  },

  listDocuments: async (params = {}) => {
    const response = await api.get(`${ADMIN_KNOWLEDGE_BASE}/documents`, { params });
    return response.data;
  },

  getDocument: async (id) => {
    const response = await api.get(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}`);
    return response.data;
  },

  uploadDocument: async (formData, onUploadProgress) => {
    const response = await api.post(`${ADMIN_KNOWLEDGE_BASE}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onUploadProgress
    });
    return response.data;
  },

  downloadDocument: async (id) => {
    const response = await api.get(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}/download`, {
      responseType: 'blob'
    });
    return response;
  },

  getDocumentPreview: async (id) => {
    const response = await api.get(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}/preview`);
    return response.data;
  },

  deleteDocument: async (id) => {
    await api.delete(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}`);
  },

  reingestDocument: async (id) => {
    const response = await api.post(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}/reingest`);
    return response.data;
  },

  batchDeleteDocuments: async (ids) => {
    await api.post(`${ADMIN_KNOWLEDGE_BASE}/documents/batch-delete`, { ids });
  },

  getConfig: async () => {
    const response = await api.get(`${ADMIN_KNOWLEDGE_BASE}/config`);
    return response.data;
  },

  updateConfig: async (data) => {
    const response = await api.put(`${ADMIN_KNOWLEDGE_BASE}/config`, data);
    return response.data;
  },

  getEmbeddingModels: async () => {
    const response = await api.get(`${ADMIN_KNOWLEDGE_BASE}/embedding-models`);
    return response.data;
  }
};

export default adminKnowledgeApi;
```

**Step 2:** Commit

```bash
git add frontend/src/services/adminKnowledgeApi.js
git commit -m "feat: add adminKnowledgeApi"
```

---

### Task 4.2: 更新 KnowledgeBaseTab.jsx

**Files:**
- Modify: `frontend/src/components/admin/KnowledgeBaseTab.jsx`
- Create: `frontend/src/components/admin/KnowledgeBaseTab.css`
- Create: `frontend/src/components/admin/knowledge/FolderTree.jsx`
- Create: `frontend/src/components/admin/knowledge/FolderTree.css`
- Create: `frontend/src/components/admin/knowledge/DocumentList.jsx`
- Create: `frontend/src/components/admin/knowledge/DocumentList.css`
- Create: `frontend/src/components/admin/knowledge/UploadDialog.jsx`
- Create: `frontend/src/components/admin/knowledge/UploadDialog.css`
- Create: `frontend/src/components/admin/knowledge/KnowledgeConfigPanel.jsx`
- Create: `frontend/src/components/admin/knowledge/KnowledgeConfigPanel.css`

（由于代码量较大，这里仅列出任务，实施时参照设计文档中的界面布局）

---

## 阶段 5：RAG 检索集成

### Task 5.1: 修改 RAG 检索逻辑

**Files:**
- Modify: `backend/src/main/java/com/shandong/policyagent/rag/RagRetrievalService.java`

**Step 1:** 更新检索服务支持知识库隔离

（具体实现根据后续需求细化）

---

## 阶段 6：测试与优化

### Task 6.1: 完整功能测试

- 测试文件夹 CRUD
- 测试文档上传、下载、预览
- 测试向量化处理
- 测试 RAG 检索

### Task 6.2: 性能测试

- 大文件上传测试
- 批量向量化测试

---

**计划结束**
