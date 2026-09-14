package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiToolDefinition;
import com.smartlife.agent.dto.VoucherToolDto;
import com.smartlife.dto.Result;
import com.smartlife.entity.Voucher;
import com.smartlife.service.IVoucherService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Component
public class GetAvailableVouchersTool implements AgentTool {
    private final IVoucherService vouchers;private final ObjectMapper mapper;
    public GetAvailableVouchersTool(IVoucherService vouchers,ObjectMapper mapper){this.vouchers=vouchers;this.mapper=mapper;}
    public String name(){return "get_available_vouchers";}
    public AiToolDefinition definition(){return new AiToolDefinition(name(),"查询指定商户当前状态可用的优惠券，仅供推荐展示",schema("{\"type\":\"object\",\"properties\":{\"shop_id\":{\"type\":\"integer\"}},\"required\":[\"shop_id\"],\"additionalProperties\":false}"));}
    @SuppressWarnings("unchecked") public ToolExecutionResult execute(JsonNode args,ToolExecutionContext context){long id=ToolArguments.requiredPositiveLong(args,"shop_id");Result r=vouchers.queryVoucherOfShop(id);
        List<Voucher> list=r.getData() instanceof List?(List<Voucher>)r.getData(): Collections.emptyList();LocalDateTime now=LocalDateTime.now();
        List<VoucherToolDto> result=list.stream().filter(v->v.getType()==null||v.getType()!=1||((v.getStock()==null||v.getStock()>0)&&(v.getBeginTime()==null||!now.isBefore(v.getBeginTime()))&&(v.getEndTime()==null||!now.isAfter(v.getEndTime())))).map(this::dto).collect(Collectors.toList());return new ToolExecutionResult(mapper.valueToTree(result),result.size());}
    private VoucherToolDto dto(Voucher v){VoucherToolDto d=new VoucherToolDto();d.setId(v.getId());d.setShopId(v.getShopId());d.setTitle(v.getTitle());d.setSubTitle(v.getSubTitle());d.setRules(v.getRules());d.setPayValue(v.getPayValue());d.setActualValue(v.getActualValue());d.setType(v.getType());d.setStock(v.getStock());d.setBeginTime(v.getBeginTime());d.setEndTime(v.getEndTime());return d;}
    private JsonNode schema(String s){try{return mapper.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
}
