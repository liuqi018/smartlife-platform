package com.smartlife.agent.controller;

import com.smartlife.agent.dto.AgentChatRequest;
import com.smartlife.agent.service.AgentService;
import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.utils.UserHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/agent")
public class AgentController {
    private final AgentService agentService;
    public AgentController(AgentService agentService){this.agentService=agentService;}

    @PostMapping("/chat")
    public Result chat(@Valid @RequestBody AgentChatRequest request){
        UserDTO user= UserHolder.getUser();
        return Result.ok(agentService.chat(request,user==null?null:user.getId()));
    }
}
