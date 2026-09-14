package com.smartlife.agent.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
public class AiModelResponse {
    private String responseId;
    private String text;
    private List<AiToolCall> toolCalls = Collections.emptyList();

    public AiModelResponse(String text, List<AiToolCall> toolCalls) {
        this(null, text, toolCalls);
    }

    public AiModelResponse(String responseId, String text, List<AiToolCall> toolCalls) {
        this.responseId = responseId;
        this.text = text;
        this.toolCalls = toolCalls;
    }
}
