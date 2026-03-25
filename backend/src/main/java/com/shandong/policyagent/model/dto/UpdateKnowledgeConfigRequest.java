package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateKnowledgeConfigRequest {
    @Min(100)
    @Max(10000)
    private Integer chunkSize;

    @Min(0)
    @Max(2000)
    private Integer chunkOverlap;

    @Min(50)
    @Max(2000)
    private Integer minChunkSizeChars;

    @Min(100)
    @Max(10000)
    private Integer noSplitMaxChars;

    @Size(max = 200)
    private String defaultEmbeddingModel;

    @Size(max = 500)
    private String minioEndpoint;

    @Size(max = 200)
    private String minioAccessKey;

    @Size(max = 200)
    private String minioSecretKey;

    @Size(max = 100)
    private String minioBucketName;
}
