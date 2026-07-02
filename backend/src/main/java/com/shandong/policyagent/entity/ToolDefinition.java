package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具定义（卡片化展示与集中开关）。
 * 既存的 WebSearchTool / SubsidyCalculatorTool / FileParserTool 仍然存在作为执行实现，
 * 本表负责「配置元数据 + 启用开关 + 统计」的可视化层。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tool_definition")
public class ToolDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 内部编码（与 Spring bean / enum 保持一致） */
    @Column(nullable = false, length = 100, unique = true)
    private String code;

    /** 显示名 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 描述 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** web / calc / parse / external */
    @Column(nullable = false, length = 30)
    private String category;

    /** 前端 lucide 名称：Globe / Calculator / FileText / Code2 */
    @Column(length = 50)
    private String icon;

    /** 是否全局启用 */
    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    /** 每日调用量（统计） */
    @Column(name = "daily_calls")
    @Builder.Default
    private Long dailyCalls = 0L;

    /** 引用此工具的 agent 数量（统计，可由定时任务刷新） */
    @Column(name = "used_by_agent_count")
    @Builder.Default
    private Integer usedByAgentCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isEnabled == null) isEnabled = true;
        if (dailyCalls == null) dailyCalls = 0L;
        if (usedByAgentCount == null) usedByAgentCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
