package com.shandong.policyagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {
    private String type;
    private String content;
    private String message;
    private List<ChatResponse.Reference> references;

    public static ChatStreamEvent delta(String content) {
        return ChatStreamEvent.builder()
                .type("delta")
                .content(content)
                .build();
    }

    public static ChatStreamEvent references(List<ChatResponse.Reference> references) {
        return ChatStreamEvent.builder()
                .type("references")
                .references(references)
                .build();
    }

    public static ChatStreamEvent error(String message) {
        return ChatStreamEvent.builder()
                .type("error")
                .message(message)
                .build();
    }
}
