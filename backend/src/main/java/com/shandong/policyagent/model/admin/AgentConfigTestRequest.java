package com.shandong.policyagent.model.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 智能体配置测试请求 DTO
 *
 * 用于 POST /api/admin/agent-config/test 接口，
 * 验证当前运行时配置是否能正常响应用户消息。
 */
@Data
public class AgentConfigTestRequest {

    /**
     * 测试消息内容（必填）
     */
    @NotBlank(message = "测试消息不能为空")
    private String message;

    /**
     * 会话 ID（可选，不填则使用临时会话）
     */
    private String sessionId;
}
