package com.smartlife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.smartlife.entity.ShopType;
import com.smartlife.mapper.ShopTypeMapper;
import com.smartlife.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryTypeList(){
        //1.从缓存中查询店铺的类别信息
        String shopTypeKey= RedisConstants.CACHE_SHOP_KEY;
        String shopTypeJson=stringRedisTemplate.opsForValue().get(shopTypeKey);
        //2.如果有直接返回
        if(StrUtil.isNotBlank(shopTypeJson)){
            System.out.println("从缓存中获取店铺类别！");
            return JSONUtil.toList(shopTypeJson,ShopType.class);
        }
        //3.没有就查数据库
        List<ShopType> typeList=query().orderByAsc("sort").list();
        //4.写入Redis
        stringRedisTemplate.opsForValue().set(shopTypeKey,JSONUtil.toJsonStr(typeList),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
     return typeList;
    }
}
