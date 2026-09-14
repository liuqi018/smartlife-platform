package com.smartlife.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiModelRequest;
import com.smartlife.agent.config.AiProperties;
import com.smartlife.agent.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResponsesApiModelClientTest {
    @Test void missingApiKeyReturnsClearError(){AiProperties p=new AiProperties();p.setBaseUrl("http://localhost");p.setModel("test");p.setApiKey("");ResponsesApiModelClient client=new ResponsesApiModelClient(p,new ObjectMapper());AgentException e=assertThrows(AgentException.class,()->client.respond(new AiModelRequest("",Collections.emptyList(),Collections.emptyList())));assertEquals("AI_API_KEY is not configured",e.getMessage());}

    @Test void parsesCompletedFunctionCallAndCallId() throws Exception {ResponsesApiModelClient client=client();
        String json="{\"id\":\"resp_1\",\"status\":\"completed\",\"output\":[{\"type\":\"function_call\",\"call_id\":\"call_1\",\"name\":\"get_shop_detail\",\"arguments\":\"{\\\"shop_id\\\":1}\"}]}";
        com.smartlife.agent.client.model.AiModelResponse response=client.parse(new ObjectMapper().readTree(json));
        assertEquals("resp_1",response.getResponseId());assertEquals("call_1",response.getToolCalls().get(0).getCallId());assertEquals("{\"shop_id\":1}",response.getToolCalls().get(0).getArguments());}

    @Test void parsesCompletedOrdinaryText() throws Exception {ResponsesApiModelClient client=client();
        String json="{\"id\":\"resp_2\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"hello\"}]}]}";
        assertEquals("hello",client.parse(new ObjectMapper().readTree(json)).getText());}

    @Test void rejectsFailedAndIncompleteResponses() throws Exception {ResponsesApiModelClient client=client();ObjectMapper mapper=new ObjectMapper();
        AgentException failed=assertThrows(AgentException.class,()->client.parse(mapper.readTree("{\"status\":\"failed\",\"error\":{\"code\":\"server_error\"}}")));
        AgentException incomplete=assertThrows(AgentException.class,()->client.parse(mapper.readTree("{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}")));
        assertTrue(failed.getMessage().contains("server_error"));assertTrue(incomplete.getMessage().contains("max_output_tokens"));}

    @Test void buildsFunctionOutputWithPreviousResponseId(){ResponsesApiModelClient client=client();
        com.smartlife.agent.client.model.AiMessage output=new com.smartlife.agent.client.model.AiMessage("tool","[]","call_1");
        com.fasterxml.jackson.databind.node.ObjectNode body=client.buildBody(new AiModelRequest("i",Collections.singletonList(output),Collections.emptyList(),"resp_1"));
        assertEquals("resp_1",body.path("previous_response_id").asText());assertEquals("function_call_output",body.path("input").get(0).path("type").asText());assertEquals("call_1",body.path("input").get(0).path("call_id").asText());}

    private ResponsesApiModelClient client(){AiProperties p=new AiProperties();p.setBaseUrl("http://localhost");p.setModel("test");p.setApiKey("test-only");return new ResponsesApiModelClient(p,new ObjectMapper());}
}
