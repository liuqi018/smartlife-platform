package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiToolDefinition;
import com.smartlife.agent.dto.ShopToolDto;
import com.smartlife.dto.Result;
import com.smartlife.entity.Shop;
import com.smartlife.entity.ShopType;
import com.smartlife.service.IShopService;
import com.smartlife.service.IShopTypeService;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GetShopDetailTool implements AgentTool {
    private final IShopService shops; private final IShopTypeService types; private final ObjectMapper mapper;
    public GetShopDetailTool(IShopService shops, IShopTypeService types, ObjectMapper mapper) { this.shops=shops; this.types=types; this.mapper=mapper; }
    public String name() { return "get_shop_detail"; }
    public AiToolDefinition definition() { return new AiToolDefinition(name(), "根据商户 ID 查询真实商户详情", schema("{\"type\":\"object\",\"properties\":{\"shop_id\":{\"type\":\"integer\"}},\"required\":[\"shop_id\"],\"additionalProperties\":false}")); }
    public ToolExecutionResult execute(JsonNode args, ToolExecutionContext context) {
        long id=ToolArguments.requiredPositiveLong(args,"shop_id"); Result result=shops.queryById(id);
        Shop shop=result.getSuccess() && result.getData() instanceof Shop ? (Shop)result.getData() : null;
        if(shop==null) return new ToolExecutionResult(mapper.createArrayNode(),0);
        ShopType type=types.getById(shop.getTypeId()); ShopToolDto dto=new ShopToolDto();
        dto.setId(shop.getId()); dto.setName(shop.getName()); dto.setTypeId(shop.getTypeId()); dto.setTypeName(type==null?null:type.getName());
        dto.setArea(shop.getArea()); dto.setAddress(shop.getAddress()); dto.setAvgPrice(shop.getAvgPrice()); dto.setScore(shop.getScore()==null?null:shop.getScore()/10D);
        dto.setComments(shop.getComments()); dto.setOpenHours(shop.getOpenHours()); dto.setDistanceMeters(shop.getDistance());
        return new ToolExecutionResult(mapper.valueToTree(dto),1, Collections.singletonList(dto));
    }
    private JsonNode schema(String s){try{return mapper.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
}
