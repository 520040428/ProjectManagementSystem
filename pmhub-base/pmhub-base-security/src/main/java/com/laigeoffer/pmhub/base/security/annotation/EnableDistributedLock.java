package com.laigeoffer.pmhub.base.security.annotation;

import com.laigeoffer.pmhub.base.security.aspect.DistributedLockAspect;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author JingYi
 * @description EnableDistributedLock 元注解，开启分布式锁功能
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({DistributedLockAspect.class})
public @interface EnableDistributedLock {
}
