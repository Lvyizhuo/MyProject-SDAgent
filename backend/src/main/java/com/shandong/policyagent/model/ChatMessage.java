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
public class ChatMessage implements Serializable {
    private String id;
    private String role;
    private String content;
    private List<ChatResponse.Reference> references;
    private Long timestamp;
}
