package com.laigeoffer.pmhub.gateway.config;

import com.laigeoffer.pmhub.gateway.handler.ValidateCodeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;

/**
 * 路由配置信息
 *
 * @author JingYi
 */
@Configuration
public class RouterFunctionConfiguration
{
    @Autowired
    private ValidateCodeHandler validateCodeHandler;

    @SuppressWarnings("rawtypes")
    @Bean
    public RouterFunction routerFunction()
    {
        // 它定义了一个简单的路由规则：当请求路径为 /code 且接受 text/plain 响应类型时，交由 validateCodeHandler 处理。
        return RouterFunctions.route(
                RequestPredicates.GET("/code").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)),
                validateCodeHandler);
    }
}
