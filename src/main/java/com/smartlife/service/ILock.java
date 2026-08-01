package com.smartlife.service;

public interface ILock {
/*

* 尝试获取锁
锁持有的超时时间，过期自动释放
return true代表获取锁成功，false代表获取锁失败
*/
    boolean tryLock(long timeOutSec);
    //释放锁
    void unlock();
}
