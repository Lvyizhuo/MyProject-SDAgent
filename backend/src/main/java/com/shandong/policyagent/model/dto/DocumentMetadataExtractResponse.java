package com.shandong.policyagent.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentMetadataExtractResponse {

    private String title;
    private String category;
    private List<String> tags;
    private String source;
    private String summary;
}
