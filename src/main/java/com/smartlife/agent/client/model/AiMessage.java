package com.smartlife.agent.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiMessage {
    private String role;
    private String content;
    private String callId;
    private String name;

    public AiMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public AiMessage(String role, String content, String callId) {
        this.role = role;
        this.content = content;
        this.callId = callId;
    }

    public AiMessage(String role, String content, String callId, String name) {
        this.role = role; this.content = content; this.callId = callId; this.name = name;
    }
}
