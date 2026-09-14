package com.smartlife.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToolCallRecord {
    private String name;
    private String arguments;
    private boolean success;
    private int resultCount;
}
