package com.smartlife.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.AiModelClient;
import com.smartlife.agent.client.model.*;
import com.smartlife.agent.config.AiProperties;
import com.smartlife.agent.dto.AgentChatRequest;
import com.smartlife.agent.dto.AgentChatResponse;
import com.smartlife.agent.exception.AgentException;
import com.smartlife.agent.tool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentServiceTest {
    private AiModelClient model;private AgentToolRegistry tools;private ConversationMemory memory;private AiProperties properties;private AgentChatRequest request;
    @BeforeEach void setUp(){model=mock(AiModelClient.class);tools=mock(AgentToolRegistry.class);memory=mock(ConversationMemory.class);properties=new AiProperties();properties.setMaxToolRounds(2);request=new AgentChatRequest();request.setMessage("推荐餐厅");when(memory.loadOrCreate(any(),eq(1L))).thenReturn(new ConversationMemory.Conversation("c1",1L,new ArrayList<>()));}
    @Test void plainTextEndsNormally(){when(model.respond(any())).thenReturn(new AiModelResponse("请提供位置",Collections.emptyList()));AgentChatResponse r=service().chat(request,1L);assertEquals("请提供位置",r.getAnswer());verify(memory).save(any());}
    @Test void executesToolThenContinuesToAnswer(){AiToolCall call=new AiToolCall("call-1","search_nearby_shops","{}");when(model.respond(any())).thenReturn(new AiModelResponse("resp-1","",Collections.singletonList(call)),new AiModelResponse("找到一家",Collections.emptyList()));when(tools.execute(eq("search_nearby_shops"),eq("{}"),any())).thenReturn(new ToolExecutionResult(new ObjectMapper().createArrayNode().add("real"),1));AgentChatResponse r=service().chat(request,1L);assertEquals("找到一家",r.getAnswer());assertEquals(1,r.getToolCalls().size());verify(model,times(2)).respond(any());}
    @Test void emptyToolResultsCannotBecomeHallucinatedRecommendation(){AiToolCall call=new AiToolCall("call-1","search_nearby_shops","{}");when(model.respond(any())).thenReturn(new AiModelResponse("resp-1","",Collections.singletonList(call)),new AiModelResponse("虚构的店铺A很好",Collections.emptyList()));when(tools.execute(any(),any(),any())).thenReturn(new ToolExecutionResult(new ObjectMapper().createArrayNode(),0));AgentChatResponse r=service().chat(request,1L);assertFalse(r.getAnswer().contains("店铺A"));assertTrue(r.getRecommendedShops().isEmpty());}
    @Test void validZeroOrderAggregateKeepsModelExplanation(){AiToolCall call=new AiToolCall("call-1","get_my_shop_order_overview","{}");when(model.respond(any())).thenReturn(new AiModelResponse("resp-1","",Collections.singletonList(call)),new AiModelResponse("本周有0笔代金券订单",Collections.emptyList()));when(tools.execute(any(),any(),any())).thenReturn(new ToolExecutionResult(new ObjectMapper().createObjectNode().put("totalOrders",0),0,true));AgentChatResponse r=service().chat(request,1L);assertEquals("本周有0笔代金券订单",r.getAnswer());}
    @Test void enforcesMaximumToolRounds(){AiToolCall call=new AiToolCall("call-1","search_nearby_shops","{}");when(model.respond(any())).thenReturn(new AiModelResponse("resp-1","",Collections.singletonList(call)));when(tools.execute(any(),any(),any())).thenReturn(new ToolExecutionResult(new ObjectMapper().createArrayNode(),1));AgentException e=assertThrows(AgentException.class,()->service().chat(request,1L));assertTrue(e.getMessage().contains("maximum tool call rounds"));verify(tools,times(2)).execute(any(),any(),any());}
    private AgentService service(){return new AgentService(model,tools,memory,properties);}
}
