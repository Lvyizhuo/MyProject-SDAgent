package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunksPageResponse {
    private Long documentId;
    private String title;
    private String vectorTableName;
    private Integer chunkCount;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private List<DocumentChunkResponse> content;
}
