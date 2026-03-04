package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkResponse {
    private String chunkId;
    private Integer chunkIndex;
    private Integer chunkChars;
    private String splitStrategy;
    private String content;
}
