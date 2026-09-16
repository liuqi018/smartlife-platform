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
    /** A successful aggregate with zero rows is still evidence for an answer. */
    private boolean validEmptyResult;

    public ToolExecutionResult(JsonNode data, int resultCount) {
        this(data, resultCount, Collections.emptyList(), false);
    }

    public ToolExecutionResult(JsonNode data, int resultCount, List<ShopToolDto> shops) {
        this(data, resultCount, shops, false);
    }

    public ToolExecutionResult(JsonNode data, int resultCount, boolean validEmptyResult) {
        this(data, resultCount, Collections.emptyList(), validEmptyResult);
    }
}
