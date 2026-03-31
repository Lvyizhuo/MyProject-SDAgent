package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFolderRequest {
    private Long parentId;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(max = 200)
    private String embeddingModel;

    @NotNull
    private Long rerankModelId;
}
