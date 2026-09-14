package com.smartlife.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiMessage;
import com.smartlife.agent.config.AiProperties;
import com.smartlife.agent.exception.AgentException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ConversationMemory {
    private static final String PREFIX="agent:conversation:";
    private final StringRedisTemplate redis; private final ObjectMapper mapper; private final AiProperties properties;
    public ConversationMemory(StringRedisTemplate redis,ObjectMapper mapper,AiProperties properties){this.redis=redis;this.mapper=mapper;this.properties=properties;}

    public Conversation loadOrCreate(String requestedId,Long userId){
        boolean create=requestedId==null||requestedId.trim().isEmpty();
        String id=create?UUID.randomUUID().toString():requestedId;
        try{String json=redis.opsForValue().get(PREFIX+id);if(json==null){
                if(!create)throw new AgentException("conversation not found or expired");
                return new Conversation(id,userId,new ArrayList<>());
            }
            Conversation c=mapper.readValue(json,Conversation.class);if(!userId.equals(c.getUserId()))throw new AgentException("conversation does not belong to current user");return c;
        }catch(AgentException e){throw e;}catch(Exception e){throw unavailable(e);}
    }

    public void save(Conversation conversation){
        int max=properties.getMaxConversationMessages();List<AiMessage> messages=new ArrayList<>();
        for(AiMessage message:conversation.getMessages())if("user".equals(message.getRole())||"assistant".equals(message.getRole()))messages.add(message);
        if(messages.size()>max)conversation.setMessages(new ArrayList<>(messages.subList(messages.size()-max,messages.size())));
        else conversation.setMessages(messages);
        try{redis.opsForValue().set(PREFIX+conversation.getId(),mapper.writeValueAsString(conversation),properties.getConversationTtlMinutes(),TimeUnit.MINUTES);}
        catch(Exception e){throw unavailable(e);}
    }
    private AgentException unavailable(Exception e){return new AgentException("conversation memory is unavailable (Redis): "+e.getClass().getSimpleName(),e);}

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Conversation { private String id; private Long userId; private List<AiMessage> messages; }
}
