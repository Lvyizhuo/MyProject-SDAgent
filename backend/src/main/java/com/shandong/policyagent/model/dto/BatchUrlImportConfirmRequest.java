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
public class BatchUrlImportConfirmRequest {
    private List<Long> ids;
    // 兼容旧参数：批量确认固定使用各任务绑定知识库，不支持覆盖。
    private Long folderId;
}