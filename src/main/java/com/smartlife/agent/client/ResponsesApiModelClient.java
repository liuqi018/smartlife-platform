package com.smartlife.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlife.agent.client.model.*;
import com.smartlife.agent.config.AiProperties;
import com.smartlife.agent.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ResponsesApiModelClient implements AiModelClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ResponsesApiModelClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutSeconds() * 1000);
        factory.setReadTimeout(properties.getTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public AiModelResponse respond(AiModelRequest request) {
        validateConfiguration();
        long started = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.getApiKey());
            ResponseEntity<JsonNode> response = restTemplate.exchange(endpoint(), HttpMethod.POST,
                    new HttpEntity<>(buildBody(request), headers), JsonNode.class);
            AiModelResponse parsed = parse(response.getBody());
            log.info("agent model call completed,model={},durationMs={},toolCallCount={}",
                    properties.getModel(), System.currentTimeMillis() - started, parsed.getToolCalls().size());
            return parsed;
        } catch (ResourceAccessException e) {
            log.error("agent model call timed out or was unreachable,model={},durationMs={},errorType={}",
                    properties.getModel(), System.currentTimeMillis() - started, e.getClass().getSimpleName());
            throw new AgentException("AI model request timed out or is unreachable", e);
        } catch (HttpStatusCodeException e) {
            log.error("agent model call returned non-2xx,model={},durationMs={},httpStatus={}",
                    properties.getModel(), System.currentTimeMillis() - started, e.getRawStatusCode());
            throw new AgentException("AI model request failed with HTTP " + e.getRawStatusCode(), e);
        } catch (RestClientException e) {
            log.error("agent model call failed,model={},durationMs={},errorType={}",
                    properties.getModel(), System.currentTimeMillis() - started, e.getClass().getSimpleName());
            throw new AgentException("AI model request failed: " + e.getMessage(), e);
        }
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) throw new AgentException("AI agent is disabled (ai.enabled=false)");
        if (!StringUtils.hasText(properties.getApiKey())) throw new AgentException("AI_API_KEY is not configured");
        if (!StringUtils.hasText(properties.getBaseUrl())) throw new AgentException("AI_BASE_URL is not configured");
        if (!StringUtils.hasText(properties.getModel())) throw new AgentException("AI_MODEL is not configured");
    }

    private String endpoint() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        return base.endsWith("/responses") ? base : base + "/responses";
    }

    ObjectNode buildBody(AiModelRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("instructions", request.getInstructions());
        if (StringUtils.hasText(request.getPreviousResponseId())) {
            body.put("previous_response_id", request.getPreviousResponseId());
        }
        ArrayNode input = body.putArray("input");
        for (AiMessage message : request.getMessages()) {
            ObjectNode item = input.addObject();
            if ("function_call".equals(message.getRole())) {
                item.put("type", "function_call");
                item.put("call_id", message.getCallId());
                item.put("name", message.getName());
                item.put("arguments", message.getContent());
            } else if ("tool".equals(message.getRole())) {
                item.put("type", "function_call_output");
                item.put("call_id", message.getCallId());
                item.put("output", message.getContent());
            } else {
                item.put("role", message.getRole());
                item.put("content", message.getContent());
            }
        }
        ArrayNode tools = body.putArray("tools");
        for (AiToolDefinition definition : request.getTools()) {
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            tool.put("name", definition.getName());
            tool.put("description", definition.getDescription());
            tool.set("parameters", definition.getParameters());
            tool.put("strict", false);
        }
        return body;
    }

    AiModelResponse parse(JsonNode body) {
        if (body == null) throw new AgentException("AI model returned an empty response");
        String status = body.path("status").asText();
        if (!"completed".equals(status)) {
            if ("failed".equals(status)) {
                throw new AgentException("AI model response failed: " + body.path("error").path("code").asText("unknown"));
            }
            if ("incomplete".equals(status)) {
                throw new AgentException("AI model response incomplete: " + body.path("incomplete_details").path("reason").asText("unknown"));
            }
            throw new AgentException("AI model response has unexpected status: " + (status.isEmpty() ? "missing" : status));
        }
        StringBuilder text = new StringBuilder();
        List<AiToolCall> calls = new ArrayList<>();
        for (JsonNode item : body.path("output")) {
            if ("function_call".equals(item.path("type").asText())) {
                calls.add(new AiToolCall(item.path("call_id").asText(), item.path("name").asText(),
                        item.path("arguments").asText("{}")));
            } else if ("message".equals(item.path("type").asText())) {
                for (JsonNode content : item.path("content")) {
                    String contentType = content.path("type").asText();
                    if ("output_text".equals(contentType)) text.append(content.path("text").asText());
                    else if ("refusal".equals(contentType)) text.append(content.path("refusal").asText());
                }
            }
        }
        return new AiModelResponse(body.path("id").asText(null), text.toString(), calls);
    }
}
