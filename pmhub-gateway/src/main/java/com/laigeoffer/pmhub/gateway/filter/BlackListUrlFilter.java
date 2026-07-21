package com.laigeoffer.pmhub.gateway.filter;

import com.laigeoffer.pmhub.base.core.utils.ServletUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 黑名单过滤器(网关过滤器)
 * 针对特定路由生效，必须绑定到某个具体的路由配置上才会工作，需要在application.yml的路由配置下显式指定才能生效
 * @description 主要作用是拦截特定路由的请求，并检查请求中的URL是否命中了配置的黑名单。如果命中，则直接拒绝访问
 *
 * @author JingYi
 */
@Component
public class BlackListUrlFilter extends AbstractGatewayFilterFactory<BlackListUrlFilter.Config>
{
    @Override
    public GatewayFilter apply(Config config)
    {
        return (exchange, chain) -> {

            String url = exchange.getRequest().getURI().getPath();
            // 如果我们获得请求体的url是我们黑名单中的url，我们置返回信息为"请求地址不允许访问"
            if (config.matchBlacklist(url))
            {
                return ServletUtils.webFluxResponseWriter(exchange.getResponse(), "请求地址不允许访问");
            }

            return chain.filter(exchange);
        };
    }

    // 调用父类构造函数，将Config类的字节码对象传递给父类，目的是告诉框架这个过滤器使用Config类来接收配置参数
    public BlackListUrlFilter()
    {
        super(Config.class);
    }

    public static class Config
    {
        // 原始配置的黑名单URL列表
        private List<String> blacklistUrl;

        // 编译后的正则表达式列表，用于高性能匹配
        private List<Pattern> blacklistUrlPattern = new ArrayList<>();

        public boolean matchBlacklist(String url)
        {
            // anyMatch()方法只要有一条命中就返回true,全部不命中返回false
            // p.matcher(url)：用正则创建匹配器，匹配目标 url
            // find()：只要 url 中任意一段包含正则匹配内容，就返回 true
            return !blacklistUrlPattern.isEmpty() && blacklistUrlPattern.stream().anyMatch(p -> p.matcher(url).find());
        }

        public List<String> getBlacklistUrl()
        {
            return blacklistUrl;
        }

        public void setBlacklistUrl(List<String> blacklistUrl)
        {
            // 设置黑名单Url列表
            this.blacklistUrl = blacklistUrl;
            // 清空现有的Url匹配模式
            this.blacklistUrlPattern.clear();
            // 遍历黑名单，将通配符转换为正则表达式并编译为模式对象
            this.blacklistUrl.forEach(url -> {
                // 将Url中的通配符**替换为正则表达式(.*?)，忽略大小写
                this.blacklistUrlPattern.add(Pattern.compile(url.replaceAll("\\*\\*", "(.*?)"), Pattern.CASE_INSENSITIVE));
            });
        }
    }

}
