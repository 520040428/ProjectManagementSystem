package com.laigeoffer.pmhub.gateway.filter;

import com.laigeoffer.pmhub.base.core.config.redis.RedisService;
import com.laigeoffer.pmhub.base.core.constant.CacheConstants;
import com.laigeoffer.pmhub.base.core.constant.HttpStatus;
import com.laigeoffer.pmhub.base.core.constant.SecurityConstants;
import com.laigeoffer.pmhub.base.core.constant.TokenConstants;
import com.laigeoffer.pmhub.base.core.utils.JwtUtils;
import com.laigeoffer.pmhub.base.core.utils.ServletUtils;
import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.gateway.config.properties.IgnoreWhiteProperties;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * 网关鉴权
 *
 * @author canghe
 * @description 这段代码基于Spring Cloud Gateway的全局鉴权过滤器，核心作用在网管层统一拦截所有进入的HTTP请求，进行身份验证和权限校验，同时记录接口的访问耗时日志
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final String BEGIN_VISIT_TIME = "begin_visit_time";//开始访问时间

    // 排除过滤的 uri 地址，nacos自行添加
    @Autowired
    private IgnoreWhiteProperties ignoreWhite;

    @Autowired
    private RedisService redisService;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //ServerWebExchange是网关里的核心上下文对象，一次完整请求的全部信息都存在它里面
        //包括原始请求，响应，属性，上下文参数等
        ServerHttpRequest request = exchange.getRequest();
        //原始request不能直接改header,参数,路径，必须通过mutate请求构造器修改
        ServerHttpRequest.Builder mutate = request.mutate();

        //获取请求路径，不带参数，如果要获取带参数的请求路径，使用getRawPath()
        String url = request.getURI().getPath();
        // 跳过不需要验证的路径(白名单中的路径不需要鉴权)
        if (StringUtils.matches(url, ignoreWhite.getWhites())) {
            //放行这个请求到下一个过滤器
            return chain.filter(exchange);
        }
        //获取请求头中的token
        String token = getToken(request);
        //1.请求头为空，则返回令牌不能为空的信息
        if (StringUtils.isEmpty(token)) {
            return unauthorizedResponse(exchange, "令牌不能为空");
        }
        //2.请求头不为空，则解析token
        Claims claims = JwtUtils.parseToken(token);
        //3.解析失败，则返回令牌验证不通过的信息(为空说明解析失败，没有拿到自定义的信息)
        if (claims == null) {
            return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
        }
        //4.解析成功，则获取usekey(通常是登录的唯一凭证/UUID)，并设置到请求头中
        String userkey = JwtUtils.getUserKey(claims);
        //5.判断redis中是否存在这个userkey，不存在则返回登录状态已过期(因为redis会设置这个登录信息的过期时间)
        boolean islogin = redisService.hasKey(getTokenKey(userkey));
        if (!islogin) {
            return unauthorizedResponse(exchange, "登录状态已过期");
        }
        //6.获得userid,username,为空则返回令牌验证失败
        String userid = JwtUtils.getUserId(claims);
        String username = JwtUtils.getUserName(claims);
        if (StringUtils.isEmpty(userid) || StringUtils.isEmpty(username)) {
            return unauthorizedResponse(exchange, "令牌验证失败");
        }

        // 设置用户信息到请求
        addHeader(mutate, SecurityConstants.USER_KEY, userkey);
        addHeader(mutate, SecurityConstants.DETAILS_USER_ID, userid);
        addHeader(mutate, SecurityConstants.DETAILS_USERNAME, username);
        // 内部请求来源参数清除（防止网关携带内部请求标识，造成系统安全风险，防止绕过鉴权）
        removeHeader(mutate, SecurityConstants.FROM_SOURCE);

        //先记录下访问接口的开始时间
        exchange.getAttributes().put(BEGIN_VISIT_TIME, System.currentTimeMillis());

//        return chain.filter(exchange.mutate().request(mutate.build()).build());

        // Mono.fromRunnable 是非阻塞的，适合在 then 中处理后续的日志逻辑。
        // 前面放行这个请求，执行后面所有的过滤器，最终转发到目标微服务，前置逻辑结束
        //前置逻辑结束后我们，后端处理完，响应回来后，再执行then中的逻辑，计算接口耗时，打印访问日志
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            try {
                // 记录接口访问日志
                Long beginVisitTime = exchange.getAttribute(BEGIN_VISIT_TIME);
                if (beginVisitTime != null) {
                    URI uri = exchange.getRequest().getURI();
                    Map<String, Object> logData = new HashMap<>();
                    logData.put("host", uri.getHost());
                    logData.put("port", uri.getPort());
                    logData.put("path", uri.getPath());
                    logData.put("query", uri.getRawQuery());
                    logData.put("duration", (System.currentTimeMillis() - beginVisitTime) + "ms");

                    log.info("访问接口信息: {}", logData);
                    log.info("我是美丽分割线: ###################################################");
                }
            } catch (Exception e) {
                log.error("记录日志时发生异常: ", e);
            }
        }));
    }

    /**
     * 给请求头新增键值对
     * @param mutate
     * @param name
     * @param value
     */
    private void addHeader(ServerHttpRequest.Builder mutate, String name, Object value) {
        if (value == null) {
            return;
        }
        String valueStr = value.toString();
        //对字符串进行URL编码，值里有中文，空格，特殊符号，不编码传到后端回乱码，解析失败
        String valueEncode = ServletUtils.urlEncode(valueStr);
        mutate.header(name, valueEncode);
    }

    /**
     * 从请求头中移除指定key的请求头
     * @param mutate
     * @param name
     */
    private void removeHeader(ServerHttpRequest.Builder mutate, String name) {
        mutate.headers(httpHeaders -> httpHeaders.remove(name)).build();
    }

    /**
     * 鉴权异常处理
     * @param exchange
     * @param msg
     * @return
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg) {
        log.error("[鉴权异常处理]请求路径:{}", exchange.getRequest().getPath());
        return ServletUtils.webFluxResponseWriter(exchange.getResponse(), msg, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 获取缓存key
     */
    private String getTokenKey(String token) {
        return CacheConstants.LOGIN_TOKEN_KEY + token;
    }

    /**
     * 获取请求token
     */
    private String getToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(TokenConstants.AUTHENTICATION);
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX)) {
            token = token.replaceFirst(TokenConstants.PREFIX, StringUtils.EMPTY);
        }
        return token;
    }

    //过滤器执行优先级，返回数字代表排序值；数字越小，过滤器越先执行
    //认证，鉴权过滤器一般设负数，优先拦截未登录请求
    @Override
    public int getOrder() {
        return -200;
    }




}