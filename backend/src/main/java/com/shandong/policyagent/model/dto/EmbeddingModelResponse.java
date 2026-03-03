package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingModelResponse {
    private String id;
    private String name;
    private String provider;
    private Integer dimensions;
    private boolean isDefault;
}
