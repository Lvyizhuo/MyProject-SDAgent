package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlImportConfirmRequest {
    private Long folderId;
    private String title;
    private String category;
    private List<String> tags;
    private LocalDate publishDate;
    private String source;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String summary;
}