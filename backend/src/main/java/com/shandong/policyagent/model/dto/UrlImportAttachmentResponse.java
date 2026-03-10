package com.shandong.policyagent.model.dto;

import com.shandong.policyagent.entity.UrlImportParseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlImportAttachmentResponse {
    private Long id;
    private String attachmentUrl;
    private String fileName;
    private String fileType;
    private UrlImportParseStatus parseStatus;
    private Boolean ocrUsed;
}