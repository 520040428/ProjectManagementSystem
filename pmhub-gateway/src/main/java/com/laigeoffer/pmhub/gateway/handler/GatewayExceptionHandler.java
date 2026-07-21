package com.laigeoffer.pmhub.gateway.handler;

import com.laigeoffer.pmhub.base.core.utils.ServletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一异常处理
 *
 *
 * @author JingYi
 */
// SpringBoot默认提供了一个一场处理器叫做DefaultErrorWebExceptionHandler，它的默认Order值通常是0或者更高
@Order(-1)
@Configuration
public class GatewayExceptionHandler implements ErrorWebExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex)
    {
        ServerHttpResponse response = exchange.getResponse();

        // 如果响应已经提交，我们无法自定义错误信息，直接异常抛出
        if (exchange.getResponse().isCommitted())
        {
            return Mono.error(ex);
        }

        String msg;

        // 通常是404错误
        if (ex instanceof NotFoundException)
        {
            msg = "服务未找到";
        }
        // 通常是Spring的通用响应状态异常，通常包含了具体的业务错误信息
        else if (ex instanceof ResponseStatusException)
        {
            ResponseStatusException responseStatusException = (ResponseStatusException) ex;
            msg = responseStatusException.getMessage();
        }
        // 兜底逻辑，如果是其他未预料的系统错误，提示"内部服务器错误"
        else
        {
            msg = "内部服务器错误";
        }

        log.error("[网关异常处理]请求路径:{},异常信息:{}", exchange.getRequest().getPath(), ex.getMessage());

        return ServletUtils.webFluxResponseWriter(response, msg);
    }
}