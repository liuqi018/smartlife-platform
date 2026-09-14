package com.smartlife.agent.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiToolCall {
    private String callId;
    private String name;
    private String arguments;
}
