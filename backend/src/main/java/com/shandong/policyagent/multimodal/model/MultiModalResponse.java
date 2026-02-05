package com.shandong.policyagent.multimodal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiModalResponse {
    private boolean success;
    private String type;
    private String content;
    private String conversationId;
    private String errorMessage;

    public static MultiModalResponse success(String content, String type, String conversationId) {
        return MultiModalResponse.builder()
                .success(true)
                .type(type)
                .content(content)
                .conversationId(conversationId)
                .build();
    }

    public static MultiModalResponse error(String errorMessage) {
        return MultiModalResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static MultiModalResponse success(String type, String result) {
        return MultiModalResponse.builder()
                .success(true)
                .type(type)
                .content(result)
                .build();
    }

    public static MultiModalResponse error(String type, String errorMessage) {
        return MultiModalResponse.builder()
                .success(false)
                .type(type)
                .errorMessage(errorMessage)
                .build();
    }
}
