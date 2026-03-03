package com.shandong.policyagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderTreeResponse {
    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private String path;
    private Integer depth;
    @Builder.Default
    private List<FolderTreeResponse> children = new ArrayList<>();
}
