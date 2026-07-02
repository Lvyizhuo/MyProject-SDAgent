package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体 ↔ 模型 多对多绑定。
 * role: LLM / VISION / AUDIO / FUNCTION（对应前端 chip 上的标签）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_model_binding",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "role"}))
public class AgentModelBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private AgentInstance agent;

    /** LLM / VISION / AUDIO / FUNCTION */
    @Column(nullable = false, length = 30)
    private String role;

    /** 引用的模型 ID（指向 model_provider.id） */
    @Column(name = "model_id")
    private Long modelId;

    /** 引用的工具 key（role=FUNCTION 时使用，指向 tool_definition.code） */
    @Column(name = "tool_code", length = 100)
    private String toolCode;

    /** 显示用的模型/工具名（冗余存储，避免 N+1） */
    @Column(name = "display_name", length = 100)
    private String displayName;
}
