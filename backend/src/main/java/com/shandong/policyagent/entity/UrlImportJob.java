package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "url_import_jobs")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "source_site", nullable = false, length = 200)
    private String sourceSite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_folder_id")
    private KnowledgeFolder targetFolder;

    @Column(name = "embedding_model", length = 200)
    private String embeddingModel;

    @Column(name = "title_override", length = 500)
    private String titleOverride;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private UrlImportJobStatus status = UrlImportJobStatus.PENDING;

    @Column(name = "discovered_count", nullable = false)
    @Builder.Default
    private Integer discoveredCount = 0;

    @Column(name = "candidate_count", nullable = false)
    @Builder.Default
    private Integer candidateCount = 0;

    @Column(name = "imported_count", nullable = false)
    @Builder.Default
    private Integer importedCount = 0;

    @Column(name = "rejected_count", nullable = false)
    @Builder.Default
    private Integer rejectedCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

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