package com.smartlife.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /** Temporary local-test counter for actual hot-cache database rebuild queries. */
    private final AtomicInteger hotShopRebuildCount = new AtomicInteger();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

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
        if (value == null) {
            stringRedisTemplate.opsForValue().set(
                    key, "", RedisConstants.CACHE_SHOP_NULL_TTL, TimeUnit.MINUTES);
            return;
        }
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
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_SHOP_NULL_TTL, TimeUnit.MINUTES);
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

        // An empty string is a cached null value; do not penetrate to MySQL again.
        if (json != null && StrUtil.isBlank(json)) {
            return null;
        }

        // Logical-expire caches normally have no physical TTL. If a hot key is
        // unexpectedly absent, rebuild it synchronously under the same mutex so
        // concurrent requests cannot all fall back to MySQL.
        if (json == null) {
            return loadLogicalCacheOnMiss(key, id, type, dbFallback, time, unit);
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Object cachedData = redisData.getData();
        if (cachedData == null) {
            stringRedisTemplate.opsForValue().set(
                    key, "", RedisConstants.CACHE_SHOP_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        R result = JSONUtil.toBean(JSONUtil.parseObj(cachedData), type);
        Long expireTime = redisData.getExpireTime();
        if (expireTime != null && expireTime > System.currentTimeMillis()) {
            return result;
        }

        CACHE_REBUILD_EXECUTOR.submit(() -> {
            RLock lock = redissonClient.getLock(RedisConstants.LOCK_SHOP_KEY + id);
            if (!lock.tryLock()) {
                return;
            }
            try {
                // A queued task may run after another thread has already rebuilt
                // the value. Recheck under the lock before querying MySQL.
                String latest = stringRedisTemplate.opsForValue().get(key);
                if (isLogicalCacheFresh(latest)) {
                    return;
                }
                try {
                    int rebuildCount = hotShopRebuildCount.incrementAndGet();
                    log.info("hot shop cache database fallback executing,key={},businessId={},rebuildCount={}",
                            key, id, rebuildCount);
                    R refreshed = dbFallback.apply(id);
                    if (refreshed == null) {
                        stringRedisTemplate.opsForValue().set(
                                key, "", RedisConstants.CACHE_SHOP_NULL_TTL, TimeUnit.MINUTES);
                    } else {
                        this.setWithLogicalTime(key, refreshed, time, unit);
                    }
                    log.info("cache rebuild success,key={},businessId={}", key, id);
                } catch (Exception e) {
                    log.error("cache rebuild failed,key={},businessId={},errorType={},error={}",
                            key, id, e.getClass().getSimpleName(), e.getMessage(), e);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
        return result;
    }

    public int getHotShopRebuildCount() {
        return hotShopRebuildCount.get();
    }

    public int resetHotShopRebuildCount() {
        return hotShopRebuildCount.getAndSet(0);
    }

    private boolean isLogicalCacheFresh(String json) {
        if (StrUtil.isBlank(json)) {
            return json != null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Long expireTime = redisData.getExpireTime();
        return expireTime != null && expireTime > System.currentTimeMillis();
    }

    private <R, ID> R loadLogicalCacheOnMiss(String key, ID id, Class<R> type,
                                              Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_SHOP_KEY + id);
        while (true) {
            if (lock.tryLock()) {
                try {
                    // Double-check after acquiring the lock: another request may
                    // already have rebuilt the cache while this request waited.
                    String latest = stringRedisTemplate.opsForValue().get(key);
                    if (latest != null) {
                        if (StrUtil.isBlank(latest)) {
                            return null;
                        }
                        RedisData redisData = JSONUtil.toBean(latest, RedisData.class);
                        if (redisData.getData() == null) {
                            return null;
                        }
                        return JSONUtil.toBean(JSONUtil.parseObj(redisData.getData()), type);
                    }

                    R loaded = dbFallback.apply(id);
                    if (loaded == null) {
                        stringRedisTemplate.opsForValue().set(
                                key, "", RedisConstants.CACHE_SHOP_NULL_TTL, TimeUnit.MINUTES);
                        return null;
                    }
                    this.setWithLogicalTime(key, loaded, time, unit);
                    return loaded;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for cache rebuild", e);
            }
        }
    }

}
