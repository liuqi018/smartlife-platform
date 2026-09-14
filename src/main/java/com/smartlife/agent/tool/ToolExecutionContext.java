package com.smartlife.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToolExecutionContext {
    private Long userId;
    private Double longitude;
    private Double latitude;
}
