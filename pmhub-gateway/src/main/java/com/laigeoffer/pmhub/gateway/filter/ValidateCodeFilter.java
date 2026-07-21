package com.laigeoffer.pmhub.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.laigeoffer.pmhub.base.core.utils.ServletUtils;
import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.gateway.config.properties.CaptchaProperties;
import com.laigeoffer.pmhub.gateway.service.ValidateCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证码过滤器
 *
 * @author JingYi
 */
@Component
public class ValidateCodeFilter extends AbstractGatewayFilterFactory<Object>
{
    // 这里存放的是需要进行"验证码校验"的接口路径
    private final static String[] VALIDATE_URL = new String[] { "/auth/login", "/auth/register" };

    @Autowired
    private ValidateCodeService validateCodeService;

    @Autowired
    private CaptchaProperties captchaProperties;

    private static final String CODE = "code";

    private static final String UUID = "uuid";

    @Override
    public GatewayFilter apply(Object config)
    {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 非登录/注册请求或验证码关闭(不需要验证码的情况)，不处理，直接到下一个过滤器
            if (!StringUtils.equalsAnyIgnoreCase(request.getURI().getPath(), VALIDATE_URL) || !captchaProperties.getEnabled())
            {
                return chain.filter(exchange);
            }

            // 校验用户提交的验证码是否正确
            try
            {
                // 获取请求体数据
                String rspStr = resolveBodyFromRequest(request);
                // 将读到的字符串转换成JSONObject对象，方便后续提取字段
                JSONObject obj = JSON.parseObject(rspStr);
                // validateCodeService回去Redis或缓存中查找UUID对应的正确验证码，与用户输入的CODE进行比对
                validateCodeService.checkCaptcha(obj.getString(CODE), obj.getString(UUID));
            }
            catch (Exception e)
            {
                // 调用工具类 webFluxResponseWriter，直接在网关层写回响应。
                return ServletUtils.webFluxResponseWriter(exchange.getResponse(), e.getMessage());
            }
            // 如果一致我们就放行到下一个过滤器
            return chain.filter(exchange);
        };
    }

    private String resolveBodyFromRequest(ServerHttpRequest serverHttpRequest)
    {
        // 获取请求体的数据流
        Flux<DataBuffer> body = serverHttpRequest.getBody();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        // 订阅数据流。这告诉 WebFlux：“当有数据到达时，请执行后面的逻辑”。
        body.subscribe(buffer -> {
            // 将二进制缓冲区解码为字符缓冲区。
            CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer.asByteBuffer());
            // 手动释放内存缓冲区，防止内存泄漏。
            DataBufferUtils.release(buffer);
            // 将读取到的字符串存入 bodyRef。
            bodyRef.set(charBuffer.toString());
        });
        return bodyRef.get();
    }
}
