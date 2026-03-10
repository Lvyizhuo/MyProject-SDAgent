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
public class BatchUrlImportConfirmResponse {
    private Integer requestedCount;
    private Integer successCount;
    private Integer failedCount;
    private List<Long> importedIds;
    private List<String> errors;
}