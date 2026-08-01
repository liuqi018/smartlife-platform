package com.smartlife.service.impl;
import cn.hutool.core.bean.BeanUtil;
import com.smartlife.dto.Result;
import com.smartlife.config.RedisStreamInitializer;
import com.smartlife.entity.VoucherOrder;
import com.smartlife.mapper.VoucherOrderMapper;
import com.smartlife.service.ISeckillVoucherService;
import com.smartlife.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlife.utils.RedisIdWorker;
import com.smartlife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    //这里的针对的秒杀优惠券
    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisStreamInitializer redisStreamInitializer;
    //将代理对象定义成员变量
    private  IVoucherOrderService proxy;

    //准备线程池  一个单线程就行因为符合条件的时候要另外开启一个线程去执行下单操作
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    //线程任务定义  由于当这个类启动的时候就要执行这个初始化因此使用注解@PostConstruct 提交事务
    @PostConstruct
    private void init(){
        redisStreamInitializer.initialize();
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    @PreDestroy
    public void destroy() {
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }
    //基于消息队列的业务流程的改造
    private class VoucherOrderHandler implements Runnable {
        String queueName="stream.orders";//消息队列
        @Override
        public void run() {
            while(true){
                try {
                    //1.获取消息队列中的订单信息
                    //XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    //Java代码中依次对应上述命令
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().
                            read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if(list==null||list.size()==0){
                        //2.1如果消息获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //2.2如果获取成功，可以下单
                    //2.3解析消息队列中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //3.ACK确认 XACK stream.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("seckill order stream consume failed,queueName={},consumerGroup={},consumer={},errorType={},error={}",
                            queueName, "g1", "c1", e.getClass().getSimpleName(), e.getMessage(), e);
                    //抛出异常 订单没进行确认而是进入了pendinglist 因此需要去pendinglist中拿到订单信息进行确认
                    handlePendingList();
                }
            }
        }
      //处理异常
        private void handlePendingList() {
            while(true){
                try {
                    //1.获取消息队列中的订单信息
                    //XREADGROUP GROUP g1 c1 COUNT 1 STREAMS streams.order 0
                    //Java代码中依次对应上述命令
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().
                            read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if(list==null||list.size()==0){
                        //2.1如果消息获取失败，说明pendinglist中没有异常消息，结束循环
                        break;
                    }
                    //2.2如果获取成功，可以下单
                    //2.3解析消息队列中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //3.ACK确认 XACK stream.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("seckill order pending-list consume failed,queueName={},consumerGroup={},consumer={},errorType={},error={}",
                            queueName, "g1", "c1", e.getClass().getSimpleName(), e.getMessage(), e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }
    /*//创建阻塞队列
    private BlockingQueue<VoucherOrder> orderTask = new LinkedBlockingQueue<>(1024*1024);
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while(true){
                //1.获取队列中的订单信息
                try {
                    VoucherOrder voucherOrder = orderTask.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                  log.error("订单处理异常",e);
                }
            }

        }
    }*/
    //注入Redisson 用它获取锁执行后续操作
    @Resource
    private RedissonClient redissonClient;
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        String lockKey = "locl:order" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断是否获取成功
        if(!isLock){
            log.warn("seckill duplicate order blocked,userId={},voucherId={},orderId={},lockKey={}",
                    userId, voucherOrder.getVoucherId(), voucherOrder.getId(), lockKey);
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("seckill order create failed,userId={},voucherId={},orderId={},errorType={},error={}",
                    userId, voucherOrder.getVoucherId(), voucherOrder.getId(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
         finally {
            //释放锁
            lock.unlock();
        }
    }
    //初始化lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }
    //用改造后的lua脚本+消息队列实现优惠券秒杀
    public Result seckillVoucher(Long voucherId){
        proxy=(IVoucherOrderService) AopContext.currentProxy();
       //获取用户id因为执行lua脚本需要这个参数
       Long userId=UserHolder.getUser().getId();
       //1.执行lua脚本(进行传参) 得到有没有购买的资格如果有资格向消息队列中发送消息
        // 获取订单Id
        long orderId=redisIdWorker.nextId("order");
       Long result;
       try {
           result = stringRedisTemplate.execute(
                   SECKILL_SCRIPT, Collections.emptyList(),//名称 空集合
                   voucherId.toString(), userId.toString(), String.valueOf(orderId));//lua脚本所需要的参数 string类型
       } catch (Exception e) {
           log.error("seckill redis lua execute failed,userId={},voucherId={},orderId={},errorType={},error={}",
                   userId, voucherId, orderId, e.getClass().getSimpleName(), e.getMessage(), e);
           throw e;
       }
        if (result == null) {
            log.error("seckill redis lua execute returned null,userId={},voucherId={},orderId={}",
                    userId, voucherId, orderId);
            return Result.fail("秒杀失败");
        }
        //2.判断结果是否为0
        //将String类型转int型之后再做判断
        int r=result.intValue();
        if(r!=0){
            //3.非0就返回异常信息 1 2
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        //4.返回订单id
        return Result.ok(orderId);
    }
    //用lua脚本进行能否成功下单的判断
//   public Result seckillVoucher(Long voucherId){
//       //获取用户id因为执行lua脚本需要这个参数
//       Long userId=UserHolder.getUser().getId();
//       //1.执行lua脚本 得到有没有购买的资格
//       Long result= stringRedisTemplate.execute(
//                SECKILL_SCRIPT, Collections.emptyList(),//名称 空集合
//                voucherId.toString(), userId.toString());//lua脚本所需要的参数 string类型
//        //2.判断结果是否为0
//        //将String类型转int型之后再做判断
//        int r=result.intValue();
//        if(r!=0){
//            //3.非0就返回异常信息 1 2
//            return Result.fail(r==1?"库存不足":"不能重复下单");
//        }
//        //3.为0 能成功下单 把下单信息保存到阻塞队列
//        long orderId=redisIdWorker.nextId("order");
//        //先把信息封装到用户里，阻塞队列直接接受对象就行
//        //3.1.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //3.2订单id
//        voucherOrder.setId(orderId);
//        //3.3 用户id
//        voucherOrder.setUserId(userId);
//        //3.4 代金券id
//        voucherOrder.setVoucherId(voucherId);
//         //3.5创建阻塞队列
//        //使用代理才会生效 类使用自己的方法事务不会生效 因此应该交给一个代理
//        //获取代理对象 保证事务的执行 因为事物的获取是基于父线程的 子线程不能获取 所以必须提前获取---我们把代理对象初始化成员变量方便后续成员调用
//         proxy=(IVoucherOrderService) AopContext.currentProxy();
//        //3.6写入阻塞队列
//        orderTask.add(voucherOrder);
//        //4.返回订单id
//        return Result.ok(orderId);
//
//    }
    @Override
   public  void createVoucherOrder(VoucherOrder voucherOrder){
       //5.一人一单 子线程只能从订单中获得用户id
       Long userId =voucherOrder.getUserId();
       //5.1 查询订单
       int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
       //5.2判断是否存在
       if (count > 0) {
           //用户已经购买过了
           log.warn("seckill order duplicated,userId={},voucherId={},orderId={}",
                   userId, voucherOrder.getVoucherId(), voucherOrder.getId());
           return;
       }
       //6.扣减库存
       boolean success;
       try {
           success = iSeckillVoucherService.update()
                   .setSql("stock=stock-1")
                   .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                   .update();
       } catch (Exception e) {
           log.error("mysql seckill stock update failed,userId={},voucherId={},orderId={},errorType={},error={}",
                   userId, voucherOrder.getVoucherId(), voucherOrder.getId(),
                   e.getClass().getSimpleName(), e.getMessage(), e);
           throw e;
       }
       if (!success) {
           //扣减失败
           log.warn("seckill stock insufficient,userId={},voucherId={},orderId={}",
                   userId, voucherOrder.getVoucherId(), voucherOrder.getId());
           return;
       }
       try {
           save(voucherOrder);
           log.info("seckill order saved,userId={},voucherId={},orderId={}",
                   userId, voucherOrder.getVoucherId(), voucherOrder.getId());
       } catch (Exception e) {
           log.error("mysql seckill order save failed,userId={},voucherId={},orderId={},errorType={},error={}",
                   userId, voucherOrder.getVoucherId(), voucherOrder.getId(),
                   e.getClass().getSimpleName(), e.getMessage(), e);
           throw e;
       }
    }
}

   /* public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动还未开始");
        }
        //3.判断秒杀是否结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已经结束");
        }
        //4.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        //SimplerRedisLock lock = new SimplerRedisLock("order:" + userId, stringRedisTemplate);

        使用Redisson执行锁

        RLock lock1 = redissonClient.getLock("order:" + userId);
        //获取锁
        boolean islock = lock1.tryLock();
        //获取锁--自己实现的
        //boolean islock = lock.tryLock(1200);
        if(!islock){
            //获取锁失败 返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try {
            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
            //使用代理才会生效 类使用自己的方法事务不会生效 因此应该交给一个代理
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            lock1.unlock();
        }
    }*/
        //加悲观锁
       /* @Transactional
        public  Result createVoucherOrder(Long voucherId) {
            //5.一人一单
            Long userId = UserHolder.getUser().getId();
            //5.1 查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //5.2判断是否存在
            if (count > 0) {
                //用户已经购买过了
                    return Result.fail("用户已经购买过一次了");
            }
            //6.扣减库存
            boolean success = iSeckillVoucherService.update()
                        .setSql("stock=stock-1")
                        .eq("voucher_id", voucherId).gt("stock", 0)
                        .update();
            if (!success) {
                    //扣减失败
                    return Result.fail("库存不足！");
                }
                //7.创建订单
                VoucherOrder voucherOrder = new VoucherOrder();
                //7.1订单id
                long orderId = redisIdWorker.nextId("order");
                voucherOrder.setId(orderId);
                //7.2 用户id
                voucherOrder.setUserId(userId);
                //7.3 代金券id
                voucherOrder.setVoucherId(voucherId);
                //8.写入数据库
                save(voucherOrder);
                //9.返回订单
                return Result.ok(voucherOrder);
            }
        }*/

