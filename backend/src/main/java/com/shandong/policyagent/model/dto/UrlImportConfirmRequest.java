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
    // 兼容旧参数：网站导入确认入库固定使用任务绑定知识库，不支持通过该字段修改。
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