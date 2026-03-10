package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "url_import_attachments")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlImportAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private UrlImportItem item;

    @Column(name = "attachment_url", nullable = false, length = 1000)
    private String attachmentUrl;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "parsed_text_path", length = 1000)
    private String parsedTextPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 50)
    @Builder.Default
    private UrlImportParseStatus parseStatus = UrlImportParseStatus.PENDING;

    @Column(name = "ocr_used", nullable = false)
    @Builder.Default
    private Boolean ocrUsed = false;

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