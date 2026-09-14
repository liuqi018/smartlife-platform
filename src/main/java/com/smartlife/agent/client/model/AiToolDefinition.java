package com.smartlife.agent.client.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiToolDefinition {
    private String name;
    private String description;
    private JsonNode parameters;
}
