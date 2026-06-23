package com.laigeoffer.pmhub.gateway.filter;

import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.base.core.utils.html.EscapeUtil;
import com.laigeoffer.pmhub.gateway.config.properties.XssProperties;
import io.netty.buffer.ByteBufAllocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 跨站脚本过滤器
 *
 * @author canghe
 * @description 主要功能是防御跨站脚本攻击(XSS),拦截请求，读取并清洗请求体中的恶意脚本，实现接口的安全防护
 */
@Component
//条件注解：只有配置文件中 security.xss.enabled=true 时，当前类 / 方法才会注册为 Spring Bean。
@ConditionalOnProperty(value = "security.xss.enabled", havingValue = "true")
public class XssFilter implements GlobalFilter, Ordered
{
    // 跨站脚本的 xss 配置，nacos自行添加，已在Properties中创建过配置类了，直接注入即可
    @Autowired
    private XssProperties xss;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        ServerHttpRequest request = exchange.getRequest();
        // xss开关未开启 或 通过nacos关闭，不过滤
        if (!xss.getEnabled())
        {
            return chain.filter(exchange);
        }
        // GET DELETE 不过滤(因为get和delete请求通常没有请求体)
        HttpMethod method = request.getMethod();
        if (method == null || method == HttpMethod.GET || method == HttpMethod.DELETE)
        {
            return chain.filter(exchange);
        }
        // 非json类型，不过滤(防止图片和二进制文件被破坏)
        if (!isJsonRequest(exchange))
        {
            return chain.filter(exchange);
        }
        // excludeUrls 不过滤(由于公告模块本身包含大量的HTML标签，如果过滤页面展示时无法正常渲染排版，全部显示原始代码文本)
        String url = request.getURI().getPath();
        if (StringUtils.matches(url, xss.getExcludeUrls()))
        {
            return chain.filter(exchange);
        }
        //
        ServerHttpRequestDecorator httpRequestDecorator = requestDecorator(exchange);
        return chain.filter(exchange.mutate().request(httpRequestDecorator).build());

    }

    private ServerHttpRequestDecorator requestDecorator(ServerWebExchange exchange)
    {
        //重写ServerHttpRequestDecorator的getBody方法以及getHeaders方法，实现自定义
        ServerHttpRequestDecorator serverHttpRequestDecorator = new ServerHttpRequestDecorator(exchange.getRequest())
        {
            /**
             * 处理HTTP请求体并防御XSS攻击的典型实现
             * @return
             */
            @Override
            public Flux<DataBuffer> getBody()
            {
                //在WebFlux中，请求体是以 DataBuffer（数据缓冲区）的形式以流（Flux）的方式异步传递的。
                Flux<DataBuffer> body = super.getBody();
                return body.buffer().map(dataBuffers -> {
                    DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
                    DataBuffer join = dataBufferFactory.join(dataBuffers);
                    byte[] content = new byte[join.readableByteCount()];
                    // 将DataBuffer中的数据读取到字节数组中
                    join.read(content);
                    // 释放掉内存
                    DataBufferUtils.release(join);
                    // 转成UTF-8字符串
                    String bodyStr = new String(content, StandardCharsets.UTF_8);
                    // 防xss攻击过滤，对字符串进行清洗，转义
                    bodyStr = EscapeUtil.clean(bodyStr);
                    // 转成字节
                    byte[] bytes = bodyStr.getBytes(StandardCharsets.UTF_8);
                    // 使用 NettyDataBufferFactory 分配一个新的 DataBuffer，将字节数组写入，并返回。这样下游处理拿到的就是被XSS过滤后的安全请求体。
                    NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);
                    DataBuffer buffer = nettyDataBufferFactory.allocateBuffer(bytes.length);
                    buffer.write(bytes);
                    return buffer;
                });
            }


            /**
             * 其核心目的是将请求的传输编码方式从传统的Content-Length（固定长度）模式转换为Transfer-Encoding: chunked（分块传输）模式。
             * @return
             */
            @Override
            public HttpHeaders getHeaders()
            {
                HttpHeaders httpHeaders = new HttpHeaders();
                // 获取原始请求的所有HTTP头信息，全部复制到新创建的对象中
                httpHeaders.putAll(super.getHeaders());
                // 由于getBody()中修改了请求体的body，导致content-length长度不确定，因此需要删除原先的content-length
                httpHeaders.remove(HttpHeaders.CONTENT_LENGTH);
                // 添加（或覆盖）Transfer-Encoding头，将其值设置为chunked，告诉服务端本次请求采用分块传输编码。
                httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                return httpHeaders;
            }

        };
        return serverHttpRequestDecorator;
    }

    /**
     * 是否是Json请求
     *
     * @param exchange HTTP请求
     */
    public boolean isJsonRequest(ServerWebExchange exchange)
    {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        return StringUtils.startsWithIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }

    //优先级相较于AuthFilter低，全局过滤器中第二个执行
    @Override
    public int getOrder()
    {
        return -100;
    }
}
