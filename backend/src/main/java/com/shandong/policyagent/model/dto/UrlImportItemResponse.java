package com.shandong.policyagent.model.dto;

import com.shandong.policyagent.entity.UrlImportItemType;
import com.shandong.policyagent.entity.UrlImportParseStatus;
import com.shandong.policyagent.entity.UrlImportReviewStatus;
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
public class UrlImportItemResponse {
    private Long id;
    private Long jobId;
    private String sourceUrl;
    private String sourcePage;
    private String sourceSite;
    private UrlImportItemType itemType;
    private String title;
    private LocalDate publishDate;
    private Integer qualityScore;
    private UrlImportParseStatus parseStatus;
    private UrlImportReviewStatus reviewStatus;
    private Boolean suspectedDuplicate;
    private String category;
    private List<String> tags;
    private String summary;
    private String cleanedText;
    private String reviewComment;
    private String errorMessage;
    private Long defaultFolderId;
    private String defaultFolderPath;
    private LocalDateTime createdAt;
    private List<UrlImportAttachmentResponse> attachments;
}