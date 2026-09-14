package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlife.agent.client.model.AiToolDefinition;

public interface AgentTool {
    String name();
    AiToolDefinition definition();
    ToolExecutionResult execute(JsonNode arguments, ToolExecutionContext context);
}
