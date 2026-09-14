package com.smartlife.agent.controller;

import com.smartlife.agent.service.AgentService;
import com.smartlife.config.WebExceptionAdvice;
import com.smartlife.dto.UserDTO;
import com.smartlife.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AgentControllerValidationTest {
    private AgentService service;
    private MockMvc mvc;

    @BeforeEach void setUp(){service=mock(AgentService.class);mvc= MockMvcBuilders.standaloneSetup(new AgentController(service)).setControllerAdvice(new WebExceptionAdvice()).build();UserDTO u=new UserDTO();u.setId(1L);UserHolder.saveUser(u);}
    @AfterEach void clean(){UserHolder.removeUser();}

    @Test void rejectsBlankMessage() throws Exception {mvc.perform(post("/agent/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errorMsg").value("message must not be blank"));verifyNoInteractions(service);}
    @Test void rejectsInvalidLongitude() throws Exception {mvc.perform(post("/agent/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"food\",\"longitude\":181,\"latitude\":31}"))
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errorMsg").value("longitude must be between -180 and 180"));verifyNoInteractions(service);}
    @Test void rejectsInvalidLatitude() throws Exception {mvc.perform(post("/agent/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"food\",\"longitude\":121,\"latitude\":-91}"))
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errorMsg").value("latitude must be between -90 and 90"));verifyNoInteractions(service);}
}
