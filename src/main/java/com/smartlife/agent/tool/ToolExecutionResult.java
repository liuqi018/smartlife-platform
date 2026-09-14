package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlife.agent.dto.ShopToolDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
public class ToolExecutionResult {
    private JsonNode data;
    private int resultCount;
    private List<ShopToolDto> shops;

    public ToolExecutionResult(JsonNode data, int resultCount) {
        this(data, resultCount, Collections.emptyList());
    }
}
