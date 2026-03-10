package com.shandong.policyagent.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDocumentOperationRequest {
    @NotEmpty
    private List<Long> ids;

    private Long targetFolderId;
}