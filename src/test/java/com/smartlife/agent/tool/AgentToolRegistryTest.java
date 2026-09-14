package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiToolDefinition;
import com.smartlife.agent.exception.AgentException;
import com.smartlife.entity.ShopType;
import com.smartlife.service.IShopService;
import com.smartlife.service.IShopTypeService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentToolRegistryTest {
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void registersAndDispatchesWhitelistedTool(){AgentTool tool=mock(AgentTool.class);when(tool.name()).thenReturn("safe");when(tool.definition()).thenReturn(new AiToolDefinition("safe","",mapper.createObjectNode()));when(tool.execute(any(),any())).thenReturn(new ToolExecutionResult(mapper.createArrayNode(),0));
        AgentToolRegistry registry=new AgentToolRegistry(Collections.singletonList(tool),mapper);assertEquals(1,registry.definitions().size());registry.execute("safe","{}",new ToolExecutionContext(1L,null,null));verify(tool).execute(any(),any());}

    @Test void rejectsUnknownTool(){AgentToolRegistry registry=new AgentToolRegistry(Collections.emptyList(),mapper);AgentException e=assertThrows(AgentException.class,()->registry.execute("delete_shop","{}",new ToolExecutionContext(1L,null,null)));assertTrue(e.getMessage().contains("unknown or disallowed"));}

    @Test void nearbyToolRejectsIllegalCoordinates(){IShopService shops=mock(IShopService.class);IShopTypeService types=mock(IShopTypeService.class);ShopType type=new ShopType().setId(1L).setName("餐厅");when(types.list()).thenReturn(Collections.singletonList(type));SearchNearbyShopsTool tool=new SearchNearbyShopsTool(shops,types,mapper);
        assertThrows(AgentException.class,()->tool.execute(read("{\"type_id\":1,\"longitude\":181,\"latitude\":31}"),new ToolExecutionContext(1L,null,null)));verifyNoInteractions(shops);}

    private com.fasterxml.jackson.databind.JsonNode read(String s){try{return mapper.readTree(s);}catch(Exception e){throw new RuntimeException(e);}}
}
