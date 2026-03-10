package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_document_sources")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_document_id", nullable = false)
    private KnowledgeDocument knowledgeDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_item_id", nullable = false)
    private UrlImportItem importItem;

    @Column(name = "source_site", nullable = false, length = 200)
    private String sourceSite;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "source_page", length = 1000)
    private String sourcePage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}