package com.smartlife.agent.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiModelRequest {
    private String instructions;
    private List<AiMessage> messages;
    private List<AiToolDefinition> tools;
    private String previousResponseId;

    public AiModelRequest(String instructions, List<AiMessage> messages, List<AiToolDefinition> tools) {
        this(instructions, messages, tools, null);
    }
}
