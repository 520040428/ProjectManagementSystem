package com.laigeoffer.pmhub.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * 获取body请求数据（解决流不能重复读取问题）
 * InputStream（输入流）的设计遵循**“单向流动”**的原则。当你从一个流中读取数据时，内部的读取指针（或位置标记）会不断向后移动。当数据被读取完毕（到达流末尾，返回 -1）后，指针就停在了末尾。
 * 再次调用read()时，会直接返回-1，无法获取其他数据
 * @author canghe
 */
@Component
public class CacheRequestFilter extends AbstractGatewayFilterFactory<CacheRequestFilter.Config>
{
    public CacheRequestFilter()
    {
        super(Config.class);
    }

    @Override
    public String name()
    {
        return "CacheRequestFilter";
    }

    // apply()是过滤器的核心装配方法，创建了实际的过滤器实例
    @Override
    public GatewayFilter apply(Config config)
    {
        // 实例化一个自定义的过滤器，功能为缓存请求体
        CacheRequestGatewayFilter cacheRequestGatewayFilter = new CacheRequestGatewayFilter();
        // 获取配置中的order，如果为空则使用默认值
        Integer order = config.getOrder();
        if (order == null)
        {
            return cacheRequestGatewayFilter;
        }
        // 配置了order值后，对原始过滤器进行包装
        return new OrderedGatewayFilter(cacheRequestGatewayFilter, order);
    }

    public static class CacheRequestGatewayFilter implements GatewayFilter
    {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
        {
            // GET DELETE 不过滤(因为这两类请求没有请求体)
            HttpMethod method = exchange.getRequest().getMethod();
            if (method == null || method == HttpMethod.GET || method == HttpMethod.DELETE)
            {
                // 放行到下一个过滤器
                return chain.filter(exchange);
            }
            // 生成了新的请求体对象然后判断是否缓存过，缓存过就放行，没缓存过就把新的请求对象放到exchange中
            return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, (serverHttpRequest) -> {
                // 如果传入的serverHttpRequest和原始请求是一个对象，说明没有包装(请求体为空或者已经被缓存过)，放行exchange到下一个过滤器
                if (serverHttpRequest == exchange.getRequest())
                {
                    return chain.filter(exchange);
                }
                // 如果生成了新的请求对象，必须将这个新的请求对象设置到 ServerWebExchange 中，然后放行这个被修改过的新 exchange，后续的过滤器才能拿到可重复读的请求体。
                return chain.filter(exchange.mutate().request(serverHttpRequest).build());
            });
        }
    }

    @Override
    public List<String> shortcutFieldOrder()
    {
        return Collections.singletonList("order");
    }

    static class Config
    {
        private Integer order;

        public Integer getOrder()
        {
            return order;
        }

        public void setOrder(Integer order)
        {
            this.order = order;
        }
    }
}