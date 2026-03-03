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
