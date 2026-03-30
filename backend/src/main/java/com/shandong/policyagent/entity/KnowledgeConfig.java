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
    @Builder.Default
    private Long id = 1L;

    @Column(name = "chunk_size")
    @Builder.Default
    private Integer chunkSize = 900;

    @Column(name = "chunk_overlap")
    @Builder.Default
    private Integer chunkOverlap = 150;

    @Column(name = "min_chunk_size_chars")
    @Builder.Default
    private Integer minChunkSizeChars = 250;

    @Column(name = "no_split_max_chars")
    @Builder.Default
    private Integer noSplitMaxChars = 900;

    @Column(name = "default_embedding_model", length = 200)
    @Builder.Default
    private String defaultEmbeddingModel = "ollama:nomic-embed-text";

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
