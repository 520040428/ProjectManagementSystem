package com.laigeoffer.pmhub.base.security.annotation;

import org.springframework.cloud.openfeign.EnableFeignClients;

import java.lang.annotation.*;

/**
 * 自定义feign注解
 * 添加basePackages路径
 * 
 * @author canghe
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableFeignClients
public @interface EnablePmFeignClients
{
    String[] value() default {};

    // 这里的作用是在@EnableFeignClients生效的时候，我们回扫描这个"com.laigeoffer.pmhub"目录下的所有包及其子包下的Feign接口
    String[] basePackages() default { "com.laigeoffer.pmhub" };

    Class<?>[] basePackageClasses() default {};

    Class<?>[] defaultConfiguration() default {};

    Class<?>[] clients() default {};
}
