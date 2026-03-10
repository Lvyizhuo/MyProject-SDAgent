package com.shandong.policyagent.model.dto;

import com.shandong.policyagent.entity.UrlImportJobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlImportJobResponse {
    private Long id;
    private String title;
    private String sourceUrl;
    private String sourceSite;
    private Long targetFolderId;
    private String targetFolderPath;
    private String embeddingModel;
    private UrlImportJobStatus status;
    private Integer discoveredCount;
    private Integer candidateCount;
    private Integer importedCount;
    private Integer rejectedCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}