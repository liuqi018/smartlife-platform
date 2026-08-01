package com.smartlife.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
        } catch (Exception e) {
            log.error("redis cache write failed,key={},ttl={},unit={},errorType={},error={}",
                    key, time, unit, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    public void setWithLogicalTime(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(System.currentTimeMillis() + unit.toMillis(time));
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
        } catch (Exception e) {
            log.error("redis logical cache write failed,key={},ttl={},unit={},errorType={},error={}",
                    key, time, unit, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json;
        try {
            json = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("redis cache query failed,key={},businessId={},errorType={},error={}",
                    key, id, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        R result;
        try {
            result = dbFallback.apply(id);
        } catch (Exception e) {
            log.error("mysql fallback query failed,key={},businessId={},errorType={},error={}",
                    key, id, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }

        try {
            if (result == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.set(key, result, time, unit);
            return result;
        } catch (Exception e) {
            log.error("redis cache rebuild write failed,key={},businessId={},errorType={},error={}",
                    key, id, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json;
        try {
            json = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("redis logical cache query failed,key={},businessId={},errorType={},error={}",
                    key, id, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }

        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R result = JSONUtil.toBean(JSONUtil.parseObj(redisData.getData()), type);
        Long expireTime = redisData.getExpireTime();
        if (expireTime > System.currentTimeMillis()) {
            return result;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        if (lock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R refreshed = dbFallback.apply(id);
                    this.setWithLogicalTime(key, refreshed, time, unit);
                    log.info("cache rebuild success,key={},businessId={}", key, id);
                } catch (Exception e) {
                    log.error("cache rebuild failed,key={},businessId={},errorType={},error={}",
                            key, id, e.getClass().getSimpleName(), e.getMessage(), e);
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return result;
    }

    private boolean tryLock(String key) {
        try {
            Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
            return BooleanUtil.isTrue(flag);
        } catch (Exception e) {
            log.error("redis lock acquire failed,key={},errorType={},error={}",
                    key, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    private void unlock(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("redis lock release failed,key={},errorType={},error={}",
                    key, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }
}
