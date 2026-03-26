package com.shandong.policyagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 响应ID
     */
    private String id;

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * AI 回复内容
     */
    private String content;

    /**
     * 引用来源列表（RAG 检索结果）
     */
    private List<Reference> references;

    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 引用来源
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reference {
        private Long documentId;
        private String title;
        private String url;
        private String sourceSite;
        private String publishedAt;
        private String scope;
        private List<String> keywords;
        private String snippet;
        private String documentName;
        private String content;
        private Integer pageNumber;
        private Double score;
    }
}
