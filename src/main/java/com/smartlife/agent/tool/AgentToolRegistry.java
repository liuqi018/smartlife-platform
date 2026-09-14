package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiToolDefinition;
import com.smartlife.agent.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentToolRegistry {
    private final Map<String, AgentTool> tools;
    private final ObjectMapper mapper;

    public AgentToolRegistry(List<AgentTool> tools, ObjectMapper mapper) {
        this.mapper = mapper;
        Map<String, AgentTool> registered = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            if (registered.put(tool.name(), tool) != null) throw new IllegalStateException("duplicate agent tool: " + tool.name());
        }
        this.tools = Collections.unmodifiableMap(registered);
    }

    public List<AiToolDefinition> definitions() { return tools.values().stream().map(AgentTool::definition).collect(Collectors.toList()); }

    public ToolExecutionResult execute(String name, String arguments, ToolExecutionContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) throw new AgentException("unknown or disallowed tool: " + name);
        long started = System.currentTimeMillis();
        try {
            JsonNode args = mapper.readTree(arguments == null ? "{}" : arguments);
            if (args == null || !args.isObject()) throw new AgentException("tool arguments must be a JSON object");
            ToolExecutionResult result = tool.execute(args, context);
            log.info("agent tool call completed,userId={},tool={},durationMs={},resultCount={}", context.getUserId(), name,
                    System.currentTimeMillis()-started, result.getResultCount());
            return result;
        } catch (AgentException e) { throw e; }
        catch (Exception e) { throw new AgentException("invalid arguments for tool " + name + ": " + e.getMessage(), e); }
    }
}
