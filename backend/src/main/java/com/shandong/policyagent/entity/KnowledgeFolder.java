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
    @Builder.Default
    private Integer depth = 1;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "embedding_model", nullable = false, length = 200)
    private String embeddingModel;

    @Column(name = "vector_table_name", nullable = false, length = 100)
    private String vectorTableName;

    @Column(name = "rerank_model_id")
    private Long rerankModelId;

    @Column(name = "rerank_model_name", length = 200)
    private String rerankModelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "init_status", length = 32)
    private KnowledgeBaseInitStatus initStatus;

    @Column(name = "init_error", columnDefinition = "TEXT")
    private String initError;

    @Column(name = "initialized_at")
    private LocalDateTime initializedAt;

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
