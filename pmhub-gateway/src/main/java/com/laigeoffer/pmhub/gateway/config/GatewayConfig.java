package com.laigeoffer.pmhub.gateway.config;


import com.laigeoffer.pmhub.gateway.handler.SentinelFallbackHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 网关限流配置
 * @description: Spring Cloud Gateway结合Alibaba Sentinel实现限流/熔断降级时的一个典型配置类
 * @author JingYi
 */
@Configuration
public class GatewayConfig
{
    @Bean
    // 指定该 Bean 的执行优先级为最高。在 Spring Cloud Gateway 中，这通常用于保证该异常处理器能在其他过滤器或处理器之前生效
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelFallbackHandler sentinelGatewayExceptionHandler()
    {
        return new SentinelFallbackHandler();
    }
}