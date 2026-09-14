package com.smartlife.agent.service;

import com.smartlife.agent.client.AiModelClient;
import com.smartlife.agent.client.model.*;
import com.smartlife.agent.config.AiProperties;
import com.smartlife.agent.dto.*;
import com.smartlife.agent.exception.AgentException;
import com.smartlife.agent.prompt.AgentSystemPrompt;
import com.smartlife.agent.tool.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AgentService {
    private final AiModelClient model; private final AgentToolRegistry tools; private final ConversationMemory memory; private final AiProperties properties;
    public AgentService(AiModelClient model,AgentToolRegistry tools,ConversationMemory memory,AiProperties properties){this.model=model;this.tools=tools;this.memory=memory;this.properties=properties;}

    public AgentChatResponse chat(AgentChatRequest request,Long userId){
        if(userId==null)throw new AgentException("authentication is required");
        long started=System.currentTimeMillis();ConversationMemory.Conversation conversation=memory.loadOrCreate(request.getConversationId(),userId);
        List<AiMessage> conversationMessages=new ArrayList<>(conversation.getMessages());
        conversationMessages.add(new AiMessage("user",locationContext(request)+request.getMessage()));
        List<AiMessage> modelInput=new ArrayList<>(conversationMessages);String previousResponseId=null;
        List<ToolCallRecord> records=new ArrayList<>();Map<Long,ShopToolDto> recommended=new LinkedHashMap<>();int toolRounds=0;boolean anyToolResult=false;
        while(true){
            AiModelResponse response=model.respond(new AiModelRequest(AgentSystemPrompt.TEXT,modelInput,tools.definitions(),previousResponseId));
            List<AiToolCall> calls=response.getToolCalls()==null?Collections.emptyList():response.getToolCalls();
            if(calls.isEmpty()){
                String answer=response.getText();if(answer==null||answer.trim().isEmpty())throw new AgentException("AI model returned neither text nor tool calls");
                if(!records.isEmpty()&&!anyToolResult)answer="没有根据当前条件查询到真实的商户、探店笔记或优惠券。你可以调整商户类型、距离或关键词后再试。";
                conversationMessages.add(new AiMessage("assistant",answer));conversation.setMessages(conversationMessages);memory.save(conversation);
                log.info("agent chat completed,userId={},conversationId={},durationMs={},toolRounds={}",userId,conversation.getId(),System.currentTimeMillis()-started,toolRounds);
                return new AgentChatResponse(conversation.getId(),answer,new ArrayList<>(recommended.values()),records);
            }
            if(toolRounds>=properties.getMaxToolRounds())throw new AgentException("maximum tool call rounds exceeded: "+properties.getMaxToolRounds());
            toolRounds++;
            List<AiMessage> toolOutputs=new ArrayList<>();
            for(AiToolCall call:calls){
                if(call.getCallId()==null||call.getCallId().trim().isEmpty())throw new AgentException("model tool call is missing call_id");
                ToolExecutionResult result=tools.execute(call.getName(),call.getArguments(),new ToolExecutionContext(userId,request.getLongitude(),request.getLatitude()));
                toolOutputs.add(new AiMessage("tool",result.getData().toString(),call.getCallId()));records.add(new ToolCallRecord(call.getName(),call.getArguments(),true,result.getResultCount()));
                anyToolResult|=result.getResultCount()>0;for(ShopToolDto shop:result.getShops())recommended.put(shop.getId(),shop);
            }
            if(response.getResponseId()==null||response.getResponseId().trim().isEmpty())throw new AgentException("model tool response is missing response id");
            previousResponseId=response.getResponseId();modelInput=toolOutputs;
        }
    }
    private String locationContext(AgentChatRequest r){return r.getLongitude()!=null&&r.getLatitude()!=null?"[用户坐标 longitude="+r.getLongitude()+", latitude="+r.getLatitude()+"] ":"";}
}
