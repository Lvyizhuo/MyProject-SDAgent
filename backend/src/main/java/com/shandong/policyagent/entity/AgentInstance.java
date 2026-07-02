package com.shandong.policyagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体实例（多智能体支持）。
 * 一行 = 一个独立的智能体配置（每个智能体可绑定不同模型）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_instance")
public class AgentInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 智能体显示名，例如「政策咨询助手」 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 内部编码（kebab-case），例如 policy-assistant */
    @Column(nullable = false, length = 100, unique = true)
    private String code;

    /** 描述 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 分类：chat / workflow / support */
    @Column(length = 50)
    private String category;

    /** 卡片图标（前端 lucide 名称）：MessageSquare / Calculator / FileSearch / BarChart3 / Mic */
    @Column(length = 50)
    private String icon;

    /** published / draft */
    @Column(length = 20)
    @Builder.Default
    private String status = "draft";

    /** 是否默认智能体（系统全局唯一） */
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    /** 版本号 */
    @Column(length = 50)
    private String version;

    /** 每日调用量（缓存/统计） */
    @Column(name = "daily_calls")
    @Builder.Default
    private Long dailyCalls = 0L;

    /** 模型绑定列表（独立子表 AgentModelBinding） */
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<AgentModelBinding> bindings = new java.util.ArrayList<>();

    /** 启用的工具 key 列表（简单 JSON 数组字符串） */
    @Column(name = "enabled_tools", columnDefinition = "TEXT")
    private String enabledTools;

    /** 知识库文件夹 ID（兼容旧版单绑定，可选） */
    @Column(name = "knowledge_folder_id")
    private Long knowledgeFolderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "draft";
        if (isDefault == null) isDefault = false;
        if (dailyCalls == null) dailyCalls = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
