package com.smartlife;

import com.smartlife.entity.Shop;
import com.smartlife.service.impl.ShopServiceImpl;
import com.smartlife.utils.CacheClient;
import com.smartlife.utils.RedisConstants;
import com.smartlife.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class SmartLifeApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop=shopService.getById(1L);
        cacheClient.setWithLogicalTime(RedisConstants.CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.MINUTES);
    }
    @Resource
    private RedisIdWorker redisIdWorker;
    //线程池
    private ExecutorService es= Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);//主线程要等300个线程全部执行完才继续往下走
        Runnable task=()->{//每一个线程要干的事情 循环生成ID
            for(int i=0;i<100;i++){//决定每个线程干几次
                long id = redisIdWorker.nextId("order");
                System.out.println("id="+id);
            }
            latch.countDown();//我这个线程干完了
        };
        long begintime=System.currentTimeMillis();//开始计时
        for(int i=0;i<300;i++){//决定线程数
            es.submit(task);//提交同一各task300次
        }
        latch.await();//等待所有线程完成
        long endtime=System.currentTimeMillis();
        System.out.println("所需时间为"+(endtime-begintime));
    }
    //商户导入---附近商户功能实现
    @Test
    void loadShopData(){
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long,List<Shop>> map =list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批完成写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1 获取类型id
            Long typeId=entry.getKey();
            String key=RedisConstants.SHOP_GEO_KEY+typeId;
            //3.2获取同类型的店铺集合
            List<Shop> value = entry.getValue();
            //一个店铺装成一个location然后最后传location集合
            List<RedisGeoCommands.GeoLocation<String>> locations= new ArrayList<>(value.size());
            //3.3写入redis GEOADD key 经度 维度 member
            for(Shop shop:value){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
    @Test
    void testHyperLogLog(){
        String values[]=new String[1000];
        int j=0;
        for(int i=0;i<1000000;i++){
            j=i%1000;
            values[j]="user_"+i;
            if(j==999){
                //HyperLogLog 不存原始元素，只记录统计信息（hash 后的标记），
                //所以它只能统计“不同元素的数量”（基数），而不会存具体内容，也不会被覆盖。
                stringRedisTemplate.opsForHyperLogLog().add("h12",values);
            }
        }
        //统计数量
        Long count=stringRedisTemplate.opsForHyperLogLog().size("h12");
        System.out.println("count="+count);
    }
}
