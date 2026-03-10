package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "url_import_items")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private UrlImportJob job;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "source_page", length = 1000)
    private String sourcePage;

    @Column(name = "source_site", nullable = false, length = 200)
    private String sourceSite;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private UrlImportItemType itemType;

    @Column(name = "source_title", length = 500)
    private String sourceTitle;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "raw_object_path", length = 1000)
    private String rawObjectPath;

    @Column(name = "cleaned_text_object_path", length = 1000)
    private String cleanedTextObjectPath;

    @Column(name = "cleaned_text", columnDefinition = "TEXT")
    private String cleanedText;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "quality_score", nullable = false)
    @Builder.Default
    private Integer qualityScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 50)
    @Builder.Default
    private UrlImportParseStatus parseStatus = UrlImportParseStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 50)
    @Builder.Default
    private UrlImportReviewStatus reviewStatus = UrlImportReviewStatus.WAITING_CONFIRM;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "suspected_duplicate", nullable = false)
    @Builder.Default
    private Boolean suspectedDuplicate = false;

    @Column(length = 200)
    private String category;

    @Column(columnDefinition = "VARCHAR(500)[]")
    private List<String> tags;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_document_id")
    private KnowledgeDocument knowledgeDocument;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UrlImportAttachment> attachments = new ArrayList<>();

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