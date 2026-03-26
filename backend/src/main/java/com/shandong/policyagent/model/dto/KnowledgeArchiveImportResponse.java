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
public class KnowledgeArchiveImportResponse {
    private Integer importedCount;
    private Integer skippedCount;
    private Integer failedCount;
    private List<String> messages;
}
