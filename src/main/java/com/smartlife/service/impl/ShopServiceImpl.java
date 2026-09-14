package com.smartlife.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlife.dto.Result;
import com.smartlife.dto.NearbyShopDTO;
import com.smartlife.entity.Shop;
import com.smartlife.mapper.ShopMapper;
import com.smartlife.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.utils.CacheClient;
import com.smartlife.utils.RedisConstants;
import com.smartlife.utils.RedisData;
import com.smartlife.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    //用工具类的方法
    @Resource
    private CacheClient cacheClient;
   //根据id查询店铺添加缓存
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Override
    public Result queryById(Long id) {
        //缓存穿透的代码
        //Shop shop=queryWithPassThrough(id);

        //用互斥锁实现缓存击穿
        //Shop shop=queryWithPassMutex(id);

        //用逻辑过期解决缓存击穿
        //Shop shop =queryWithLogicalExpire(id);

        //用工具类中的存入空值方法实现解决缓存穿透
//        Shop shop=cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,id2->getById(id2),
//                RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //用工具类实现基于逻辑过期值的方法解决缓存击穿
        Shop shop;
        try {
            if (isHotShop(id)) {
                shop = cacheClient.queryWithLogicalExpire(
                        RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                        RedisConstants.CACHE_HOT_SHOP_LOGICAL_TTL, TimeUnit.MINUTES);
            } else {
                shop = cacheClient.queryWithPassThrough(
                        RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                        RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.error("shop cache query failed,shopId={},errorType={},error={}",
                    id, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
        if(shop==null){
            return  Result.fail("店铺不存在！");
        }
        //返回
        return Result.ok(shop);
    }
    //项目启动时初始化缓存 把所有的店铺信息预热到缓存中 否则直接用逻辑过期策略店铺信息不显示
    @PostConstruct
    public void initShopCache() {
        // 1. 查询所有店铺（这里只查一次数据库）
        Set<String> hotShopIds;
        List<Shop> shops;
        try {
            hotShopIds = stringRedisTemplate.opsForSet().members(RedisConstants.HOT_SHOP_IDS_KEY);
            if (hotShopIds == null || hotShopIds.isEmpty()) {
                log.info("shop cache preload skipped,reason=no hot shop configured");
                return;
            }
            shops = listByIds(hotShopIds);
        } catch (Exception e) {
            log.error("mysql shop preload query failed,errorType={},error={}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
        if (shops == null || shops.isEmpty()) {
            log.info("shop cache preload skipped,reason=no shop data");
            return;
        }
        // 2. 遍历写入 Redis
        for (Shop shop : shops) {
            RedisData redisData = new RedisData();
            redisData.setData(shop);  // 存店铺对象
            redisData.setExpireTime(System.currentTimeMillis()
                    + TimeUnit.MINUTES.toMillis(RedisConstants.CACHE_HOT_SHOP_LOGICAL_TTL));
            String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
            try {
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
            } catch (Exception e) {
                log.error("redis shop cache preload failed,shopId={},key={},errorType={},error={}",
                        shop.getId(), key, e.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
        }
        log.info("shop cache preload completed,configured={},count={}", hotShopIds.size(), shops.size());
    }

    private boolean isHotShop(Long id) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                .isMember(RedisConstants.HOT_SHOP_IDS_KEY, String.valueOf(id)));
    }
    //解决缓存击穿的代码
    public Shop queryWithPassThrough(Long id){
        String shopKey=RedisConstants.CACHE_SHOP_KEY+id;
        //1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(shopKey);

        //2.判断是否存在 命中且有值
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            //既然用的是String保存的对象就要反序列化成商户对象
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //判断命中的是否是空值
        if(shopJson!=null){//因为前面已经判断了是否是命中且正常 这句话意思是Redis中有这个key但是是空值占位
            //是空值就返回一个错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        Shop shop=getById(id);
        //5.数据库中也不存在就返回错误
        if(shop==null){
            //6.将空值写入redis
            stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_SHOP_NULL_TTL,TimeUnit.MINUTES);
            return  null;
        }
        //7.存在，写入redis并返回信息
        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    //缓存穿透
    public Shop queryWithPassMutex(Long id){
        String shopKey=RedisConstants.CACHE_SHOP_KEY+id;
        //1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(shopKey);
        //2.判断是否存在 命中且有值
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            //既然用的是String保存的对象就要反序列化成商户对象
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //判断命中的是否是空值
        if(shopJson!=null){//因为前面已经判断了是否是命中且正常 这句话意思是Redis中有这个key但是是空值占位
            //是空值就返回一个错误信息
            return null;
        }
        //4.未命中 实现缓存重建
        //4.1获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        Shop shop= null;
        try {
            boolean lock=tryLock(lockKey);//锁的key和前面的shopkey不是一个
            //4.2判断是否获取成功
            if(!lock){
                //4.3失败则休眠并重试
                Thread.sleep(50);
                return queryWithPassMutex(id);//重试
            }
            //4.4 成功就根据id查询数据库
            shop = getById(id);
            //模拟线程安全问题
            Thread.sleep(200);
            //5.数据库中也不存在就返回错误
            if(shop==null){
                //6.将空值写入redis
                stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_SHOP_NULL_TTL,TimeUnit.MINUTES);
                return  null;
            }
            //7.存在，写入redis并返回信息
            stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //8.释放锁
            unlock(lockKey);
        }
        //9.返回
        return shop;
    }
    //获取锁和释放锁的两个方法
    private boolean tryLock(String key){
        //Boolean是包装类不要直接拆箱，否则可能会拆成null
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


    //基于逻辑过期时间来处理缓存击穿问题
    // 将店铺数据写入 Redis，并设置逻辑过期时间（毫秒时间戳）
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200); // 模拟延迟

        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(System.currentTimeMillis() + expireSeconds * 1000); // 毫秒时间戳

        // 3. 写入 Redis
        stringRedisTemplate.opsForValue().set(
                RedisConstants.CACHE_SHOP_KEY + id,
                JSONUtil.toJsonStr(redisData)
        );
    }
    //新开线程进行缓存重建 利用线程池来做
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id){
        String shopKey=RedisConstants.CACHE_SHOP_KEY+id;
        //1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(shopKey);
        //2.判断是否存在,没命中返回错误
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        // 3. 命中，先把 JSON 反序列化为对象并取出逻辑过期时间
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);

       // 逻辑过期时间改成 Long
        Long expireTime = (Long) redisData.getExpireTime(); // 毫秒时间戳

        // 4. 判断是否过期
        if (expireTime > System.currentTimeMillis()) {
            // 4.1 未过期，直接返回店铺信息
            return shop;
        }

        //4.2已过期，需要缓存重建
        //5.缓存重建
        //5.1获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        boolean lock=tryLock(lockKey);
        //5.2判断是否获取成功
        if(lock){
            //5.3获取成功就开启独立线程实现缓存重建 用线程池做
            CACHE_REBUILD_EXECUTOR.submit(()->{
               //重建缓存
                try {
                    this.saveShop2Redis(id, TimeUnit.MINUTES.toSeconds(
                            RedisConstants.CACHE_HOT_SHOP_LOGICAL_TTL));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return shop;
        //5.4获取失败就返回商铺信息

    }

    //根据id更新缓存
    @Override
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        boolean hotShop = isHotShop(id);
        RLock hotShopLock = hotShop
                ? redissonClient.getLock(RedisConstants.LOCK_SHOP_KEY + id)
                : null;
        if (hotShopLock != null) {
            hotShopLock.lock();
        }

        try {
            updateById(shop);
            String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
            if (hotShop) {
                Shop latest = getById(id);
                if (latest == null) {
                    stringRedisTemplate.opsForValue().set(
                            cacheKey, "", RedisConstants.CACHE_SHOP_NULL_TTL, TimeUnit.MINUTES);
                } else {
                    cacheClient.setWithLogicalTime(
                            cacheKey, latest, RedisConstants.CACHE_HOT_SHOP_LOGICAL_TTL, TimeUnit.MINUTES);
                }
            } else {
                stringRedisTemplate.delete(cacheKey);
            }
        } catch (Exception e) {
            log.error("shop update or cache refresh failed,shopId={},errorType={},error={}",
                    id, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        } finally {
            if (hotShopLock != null && hotShopLock.isHeldByCurrentThread()) {
                hotShopLock.unlock();
            }
        }
        return Result.ok();
    }
    //根据类型查找店铺信息
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if(x==null||y==null){
            //不需要坐标查询，按数据库查询
            Page<Shop> page=query()
                    .eq("type_id",typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        return Result.ok(searchNearbyShops(typeId, x, y, 5000, current, SystemConstants.DEFAULT_PAGE_SIZE));
    }

    @Override
    public List<Shop> searchNearbyShops(Integer typeId, double longitude, double latitude,
                                        double distanceMeters, int page, int pageSize) {
        int from=(page-1)*pageSize;
        int end=page*pageSize;
        //3.查询redis、按照距离排序、分页 结果：shopId、distance  按照类型来查
        String key=RedisConstants.SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results;
        try {
            results = stringRedisTemplate.opsForGeo().search(
                    key, GeoReference.fromCoordinate(longitude, latitude),
                    new Distance(distanceMeters),
                    RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance()
                            .limit(end));
        } catch (Exception e) {
            log.error("redis geo query failed,typeId={},current={},x={},y={},key={},errorType={},error={}",
                    typeId, page, longitude, latitude, key, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
        //4.解析出id  由于limit只能给一个参数 所以需要自己截取下一个from 也就是说他现在的起始位置是0 每次都要从0开始查
        if(results==null){
            return Collections.emptyList();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if(content.size()<=from){
            return Collections.emptyList();
        }
        //4.1 截取from~end的部分
        List<Long> ids = new ArrayList<>(content.size());
        Map<String,Distance> distanceMap=new HashMap<>(content.size());
        content.stream().skip(from).limit(pageSize).forEach(result->{
            //4.2 获取店铺id
            String shopIdStr=result.getContent().getName();
            //把店铺id加入店铺列表
            ids.add(Long.valueOf(shopIdStr));
            //4.3 获取距离
            Distance distance=result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //5.根据id查询shop 保证有序性
        String idStr=StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        //店铺本身不具有距离这个属性 到那时有一个特意设立就是为了传给前端的 距离是double型
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6.返回
        return shops;
    }

    @Override
    public List<NearbyShopDTO> queryNearbyShops(double longitude, double latitude,
                                                double radiusMeters, Long typeId, int limit) {
        return list().stream()
                .filter(shop -> shop.getX() != null && shop.getY() != null)
                .filter(shop -> typeId == null || typeId.equals(shop.getTypeId()))
                .map(shop -> NearbyShopDTO.from(shop,
                        haversineMeters(longitude, latitude, shop.getX(), shop.getY())))
                .filter(shop -> shop.getDistance() <= radiusMeters)
                .sorted(Comparator.comparingDouble(NearbyShopDTO::getDistance))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    static double haversineMeters(double longitude1, double latitude1,
                                  double longitude2, double latitude2) {
        final double earthRadiusMeters = 6371000D;
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
