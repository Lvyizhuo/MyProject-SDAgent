package com.shandong.policyagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession implements Serializable {
    private String id;
    private String title;
    private Long userId;
    private List<ChatMessage> messages;
    private Long createdAt;
    private Long updatedAt;
}
