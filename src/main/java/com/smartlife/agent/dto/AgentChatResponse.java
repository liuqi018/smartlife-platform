package com.smartlife.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AgentChatResponse {
    private String conversationId;
    private String answer;
    private List<ShopToolDto> recommendedShops;
    private List<ToolCallRecord> toolCalls;
}
