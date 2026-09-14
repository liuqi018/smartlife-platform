package com.smartlife.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.agent.client.model.AiToolDefinition;
import com.smartlife.agent.dto.BlogToolDto;
import com.smartlife.agent.exception.AgentException;
import com.smartlife.entity.Blog;
import com.smartlife.service.IBlogService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SearchShopBlogsTool implements AgentTool {
    private final IBlogService blogs; private final ObjectMapper mapper;
    public SearchShopBlogsTool(IBlogService blogs,ObjectMapper mapper){this.blogs=blogs;this.mapper=mapper;}
    public String name(){return "search_shop_blogs";}
    public AiToolDefinition definition(){return new AiToolDefinition(name(),"按商户 ID 或关键词查询真实探店笔记",schema("{\"type\":\"object\",\"properties\":{\"shop_id\":{\"type\":\"integer\"},\"keyword\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}},\"additionalProperties\":false}"));}
    public ToolExecutionResult execute(JsonNode args,ToolExecutionContext context){
        Long shopId=args.hasNonNull("shop_id")?ToolArguments.requiredPositiveLong(args,"shop_id"):null;
        String keyword=ToolArguments.optionalText(args,"keyword",100); int limit=ToolArguments.intInRange(args,"limit",10,1,20);
        if(shopId==null&&keyword==null)throw new AgentException("invalid tool argument: shop_id or keyword is required");
        QueryWrapper<Blog> query=new QueryWrapper<>(); query.eq("visibility", 0); if(shopId!=null)query.eq("shop_id",shopId);
        if(keyword!=null)query.and(q->q.like("title",keyword).or().like("content",keyword)); query.orderByDesc("liked").last("LIMIT "+limit);
        List<BlogToolDto> result=blogs.list(query).stream().map(this::dto).collect(Collectors.toList());
        return new ToolExecutionResult(mapper.valueToTree(result),result.size());
    }
    private BlogToolDto dto(Blog b){BlogToolDto d=new BlogToolDto();d.setId(b.getId());d.setShopId(b.getShopId());d.setTitle(b.getTitle());
        String c=b.getContent();d.setContentExcerpt(c==null?null:c.substring(0,Math.min(c.length(),300)));d.setLiked(b.getLiked());d.setComments(b.getComments());d.setCreateTime(b.getCreateTime());return d;}
    private JsonNode schema(String s){try{return mapper.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
}
