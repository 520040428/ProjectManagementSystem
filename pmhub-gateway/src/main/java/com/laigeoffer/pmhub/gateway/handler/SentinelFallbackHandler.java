package com.laigeoffer.pmhub.gateway.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.laigeoffer.pmhub.base.core.utils.ServletUtils;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 自定义限流异常处理
 *
 * @author canghe
 */
public class SentinelFallbackHandler implements WebExceptionHandler
{
    /**
     * Mono<Void>是响应式编程中表示异步操作完成的信号
     * @description 当系统检测到并发请求数量超过了预设的最大阈值时，会调用此方法。它的作用是向客户端（浏览器或调用方）返回一个 HTTP 响应，告知用户当前请求过于频繁或系统负载过高，并提示“请求超过最大数，请稍候再试”
     * @param response
     * @param exchange
     * @return
     */
    private Mono<Void> writeResponse(ServerResponse response, ServerWebExchange exchange)
    {
        return ServletUtils.webFluxResponseWriter(exchange.getResponse(), "请求超过最大数，请稍候再试");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex)
    {
        // 若已经提交给客户端，则继续抛出错误
        if (exchange.getResponse().isCommitted())
        {
            return Mono.error(ex);
        }
        // 如果不是Sentinel的阻塞一场，不是当前处理器处理它，交给其他异常处理器处理
        if (!BlockException.isBlockException(ex))
        {
            return Mono.error(ex);
        }
        return handleBlockedRequest(exchange, ex).flatMap(response -> writeResponse(response, exchange));
    }

    /**
     * 用于生成具体的响应内容(Sentinel负责挡住请求，通过回调接口问后端接下来怎么回复客户端)
     * @param exchange
     * @param throwable
     * @return
     */
    private Mono<ServerResponse> handleBlockedRequest(ServerWebExchange exchange, Throwable throwable)
    {
        return GatewayCallbackManager.getBlockHandler().handleRequest(exchange, throwable);
    }
}
