package com.smartlife.utils;
import cn.hutool.core.lang.UUID;
import com.smartlife.service.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimplerRedisLock implements ILock {
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX="lock";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"";
    //提前定义好释放锁的脚本 脚本初始化
    private static  final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        //指定脚本
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //配置返回值
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    //基于构造函数注入 锁类
    public SimplerRedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name=name;
        this.stringRedisTemplate=stringRedisTemplate;
    }
    @Override
    public boolean tryLock(long timeOutSec) {
        //获取线程的标识
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name,threadId,timeOutSec, TimeUnit.MINUTES
        );
        //拆箱会出问题
        return Boolean.TRUE.equals(success);
    }
      @Override
       public void unlock(){
        //调用lua脚本执行释放锁的过程
          stringRedisTemplate.execute(UNLOCK_SCRIPT,
                  Collections.singletonList(KEY_PREFIX+name),
                  ID_PREFIX+Thread.currentThread().getId());
      }
 /*   public void unlock.lua() {
        //获取线程标识
          String threadId=ID_PREFIX+Thread.currentThread().getId();
          //获取锁中的标识
          String id=stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
          //判断标识是否一致
          if(threadId.equals(id)){
              //释放锁
              stringRedisTemplate.delete(KEY_PREFIX+name);
          }
    }*/
}
