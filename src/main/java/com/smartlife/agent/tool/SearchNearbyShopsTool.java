package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiToolDefinition;
import com.smartlife.agent.dto.ShopToolDto;
import com.smartlife.agent.exception.AgentException;
import com.smartlife.entity.Shop;
import com.smartlife.entity.ShopType;
import com.smartlife.service.IShopService;
import com.smartlife.service.IShopTypeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SearchNearbyShopsTool implements AgentTool {
    private final IShopService shopService;
    private final IShopTypeService shopTypeService;
    private final ObjectMapper mapper;

    public SearchNearbyShopsTool(IShopService shopService, IShopTypeService shopTypeService, ObjectMapper mapper) {
        this.shopService = shopService;
        this.shopTypeService = shopTypeService;
        this.mapper = mapper;
    }

    public String name() { return "search_nearby_shops"; }

    public AiToolDefinition definition() {
        return new AiToolDefinition(name(), "按商户类型和坐标查询附近真实商户，结果按距离排序",
                schema("{\"type\":\"object\",\"properties\":{\"type_id\":{\"type\":\"integer\"},\"type_name\":{\"type\":\"string\"},\"longitude\":{\"type\":\"number\"},\"latitude\":{\"type\":\"number\"},\"distance_meters\":{\"type\":\"number\"},\"page\":{\"type\":\"integer\"},\"page_size\":{\"type\":\"integer\"}},\"additionalProperties\":false}"));
    }

    public ToolExecutionResult execute(JsonNode args, ToolExecutionContext context) {
        double longitude = ToolArguments.doubleInRange(args, "longitude", context.getLongitude(), -180, 180);
        double latitude = ToolArguments.doubleInRange(args, "latitude", context.getLatitude(), -90, 90);
        double distance = ToolArguments.doubleInRange(args, "distance_meters", 5000D, 100, 20000);
        int page = ToolArguments.intInRange(args, "page", 1, 1, 20);
        int pageSize = ToolArguments.intInRange(args, "page_size", 10, 1, 20);
        List<ShopType> types = shopTypeService.list();
        Integer typeId = resolveTypeId(args, types);
        Map<Long, String> typeNames = types.stream().collect(Collectors.toMap(ShopType::getId, ShopType::getName, (a, b) -> a));
        List<ShopToolDto> shops = shopService.searchNearbyShops(typeId, longitude, latitude, distance, page, pageSize)
                .stream().map(s -> toDto(s, typeNames.get(s.getTypeId()))).collect(Collectors.toList());
        return new ToolExecutionResult(mapper.valueToTree(shops), shops.size(), shops);
    }

    private Integer resolveTypeId(JsonNode args, List<ShopType> types) {
        if (args.hasNonNull("type_id") && args.get("type_id").canConvertToInt() && args.get("type_id").asInt() > 0) {
            int id = args.get("type_id").asInt();
            if (types.stream().anyMatch(t -> t.getId().intValue() == id)) return id;
            throw new AgentException("invalid tool argument: type_id does not exist");
        }
        String name = ToolArguments.optionalText(args, "type_name", 30);
        if (name == null) throw new AgentException("invalid tool argument: type_id or type_name is required");
        return types.stream().filter(t -> t.getName().contains(name) || name.contains(t.getName()))
                .map(t -> t.getId().intValue()).findFirst()
                .orElseThrow(() -> new AgentException("shop type not found: " + name));
    }

    private ShopToolDto toDto(Shop shop, String typeName) {
        ShopToolDto dto = new ShopToolDto();
        dto.setId(shop.getId()); dto.setName(shop.getName()); dto.setTypeId(shop.getTypeId()); dto.setTypeName(typeName);
        dto.setArea(shop.getArea()); dto.setAddress(shop.getAddress()); dto.setAvgPrice(shop.getAvgPrice());
        dto.setScore(shop.getScore() == null ? null : shop.getScore() / 10D); dto.setComments(shop.getComments());
        dto.setOpenHours(shop.getOpenHours()); dto.setDistanceMeters(shop.getDistance());
        return dto;
    }

    private JsonNode schema(String json) { try { return mapper.readTree(json); } catch (Exception e) { throw new IllegalStateException(e); } }
}
