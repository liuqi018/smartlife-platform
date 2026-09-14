package com.smartlife.controller.test;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.smartlife.dto.Result;
import com.smartlife.utils.CacheClient;
import com.smartlife.utils.RedisConstants;
import com.smartlife.utils.RedisData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Local-only endpoints used to verify hot-cache breakdown protection. */
@Slf4j
@RestController
@RequestMapping("/shop/test")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shop.cache-test.enabled", havingValue = "true")
public class ShopCacheTestController {

    private static final long EXPIRED_OFFSET_MILLIS = 10_000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;

    @PostMapping("/expire-hot/{id}")
    public Result expireHotShop(@PathVariable("id") Long id) {
        boolean hotShop = Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                .isMember(RedisConstants.HOT_SHOP_IDS_KEY, String.valueOf(id)));
        if (!hotShop) {
            return Result.fail("shopId is not configured in " + RedisConstants.HOT_SHOP_IDS_KEY);
        }

        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isBlank(json)) {
            return Result.fail("hot shop cache does not contain RedisData: " + cacheKey);
        }

        RedisData redisData;
        try {
            redisData = JSONUtil.toBean(json, RedisData.class);
        } catch (RuntimeException e) {
            log.warn("hot shop cache test found invalid RedisData,shopId={},key={}", id, cacheKey, e);
            return Result.fail("hot shop cache is not valid RedisData: " + cacheKey);
        }
        if (redisData.getData() == null) {
            return Result.fail("hot shop cache RedisData has no Shop data: " + cacheKey);
        }

        long beforeExpireTime = redisData.getExpireTime();
        long afterExpireTime = System.currentTimeMillis() - EXPIRED_OFFSET_MILLIS;
        redisData.setExpireTime(afterExpireTime);
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(redisData));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shopId", id);
        response.put("beforeExpireTime", beforeExpireTime);
        response.put("afterExpireTime", afterExpireTime);
        return Result.ok(response);
    }

    @GetMapping("/rebuild-count")
    public Result getRebuildCount() {
        return Result.ok(cacheClient.getHotShopRebuildCount());
    }

    @PostMapping("/rebuild-count/reset")
    public Result resetRebuildCount() {
        int previousCount = cacheClient.resetHotShopRebuildCount();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("previousCount", previousCount);
        response.put("rebuildCount", 0);
        return Result.ok(response);
    }
}
