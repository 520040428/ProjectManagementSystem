package com.laigeoffer.pmhub.base.security.pojo;

import com.laigeoffer.pmhub.base.security.service.redisson.IDistributedLock;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * @description 这是一个锁的包装类，主要用于实现分布式锁的自动释放
 */
@AllArgsConstructor
//实现AutoCloseable接口后允许对象使用 try-with-resources 语法（即 try (...) { ... }）。
//实现这个接口必须重写 close() 方法。当 try 代码块执行完毕（无论是正常结束还是抛出异常）时，JVM 会自动调用 close() 方法
public class ILock implements AutoCloseable {
    /**
     * 持有的锁对象
     */
    @Getter
    private Object lock;

    /**
     * 分布式锁接口
     */
    @Getter
    private IDistributedLock distributedLock;

    @Override
    public void close() throws Exception {
        if (Objects.nonNull(lock)) {
            distributedLock.unLock(lock);
        }
    }
}
