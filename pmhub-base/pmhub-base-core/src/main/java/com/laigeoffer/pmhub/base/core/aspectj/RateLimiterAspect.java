package com.laigeoffer.pmhub.base.core.aspectj;

import com.laigeoffer.pmhub.base.core.annotation.RateLimiter;
import com.laigeoffer.pmhub.base.core.enums.LimitType;
import com.laigeoffer.pmhub.base.core.exception.ServiceException;
import com.laigeoffer.pmhub.base.core.utils.ServletUtils;
import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.base.core.utils.ip.IpUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 限流处理
 * @description @RateLimiter 注解的AOP切面实现，是限流功能的核心逻辑所在。负责在方法执行前，通过Redis+Lua脚本实现原子化的限流判断
 *
 * @author canghe
 */
@Aspect
@Component
public class RateLimiterAspect {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterAspect.class);

    private RedisTemplate<Object, Object> redisTemplate;

    // Spring Data Redis 提供的接口，用于执行 Lua 脚本并指定返回类型（这里是 Long）。
    private RedisScript<Long> limitScript;

    @Autowired
    public void setRedisTemplate1(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 有@Autowired，背后一定有配置类，通过Spring的依赖注入和自动配置加载进来的
    // 不在字段上直接Autowired的原因：
    // 使用 Setter 注入让我的代码具备了可测试性。在单元测试中，我可以直接实例化这个类并手动注入 Mock 对象，而不需要启动繁重的 Spring 上下文，大大提高了测试效率。
    @Autowired
    public void setLimitScript(RedisScript<Long> limitScript) {
        this.limitScript = limitScript;
    }

    /**
     * 执行方法前进行限流判断
     * @param point
     * @param rateLimiter
     * @throws Throwable
     */
    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) throws Throwable {
        // 从注解中读取time(窗口时间)和count(阈值)
        int time = rateLimiter.time();
        int count = rateLimiter.count();

        // 获得特定的Redis-key
        String combineKey = getCombineKey(rateLimiter, point);
        // 将上一步生成的字符串 combineKey 包装成一个只包含一个元素的 List。这是SpringDataRedis执行Lua脚本的规定格式
        List<Object> keys = Collections.singletonList(combineKey);
        try {
            // 执行 Redis Lua 脚本。参数(Lua脚本对象，RedisKey列表，限流阈值，时间窗口)
            // number是Lua 脚本执行后的返回值。通常表示当前时间窗口内的累计请求数
            Long number = redisTemplate.execute(limitScript, keys, count, time);
            // isNull(number)防御性变成，如果Redis挂了或者网络尝试，excute可能返回null
            if (StringUtils.isNull(number) || number.intValue() > count) {
                // 如果限流，就会在控制台打印“访问过于频繁，请稍后再试”
                throw new ServiceException("访问过于频繁，请稍候再试");
            }
            log.info("限制请求'{}',当前请求'{}',缓存key'{}'", count, number.intValue(), combineKey);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("服务器限流异常，请稍候再试");
        }
    }

    /**
     *
     * @param rateLimiter
     * @param point
     * @return
     */
    public String getCombineKey(RateLimiter rateLimiter, JoinPoint point) {
        StringBuffer stringBuffer = new StringBuffer(rateLimiter.key());
        // 如果是limitType为IP，则将ip地址拼接到key中
        // 这意味着限流的粒度变成了“针对这个 IP 地址”。如果换成 USER，这里可能会拼接用户 ID。
        if (rateLimiter.limitType() == LimitType.IP) {
            stringBuffer.append(IpUtils.getIpAddr(ServletUtils.getRequest())).append("-");
        }
        // 通过AOP的JointPoint对象获取被代理的方法签名
        MethodSignature signature = (MethodSignature) point.getSignature();
        // 获取方法
        Method method = signature.getMethod();
        // 获取方法所在类
        Class<?> targetClass = method.getDeclaringClass();
        // 将全限定类名（如 com.example.controller.UserController）和方法名（如 getUser）拼接到 Key 中。
        stringBuffer.append(targetClass.getName()).append("-").append(method.getName());
        // 这确保了限流是针对“特定接口”的。不同的接口会有不同的 Key，互不干扰。
        return stringBuffer.toString();
    }
}
