package com.laigeoffer.pmhub.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合所有微服务Swagger接口文档核心配置类
 * @description: 让用户只需要访问网关的 Swagger 页面，就能看到并测试网关后面挂着的所有微服务的接口，而不需要去单独访问每个微服务的 Swagger 页面。
 * 我们之前的单体项目只需要一个Swagger页面
 * JingYi
 */
@Component
// 当 Spring 容器中存在多个相同类型的 Bean 时，@Primary 注解标记的 Bean 将被优先选择和注入。
// 就比如说如果有两个类Cat,Dog 实现Animal接口，Cat使用@Primary注解，那么我们如果使用Animal animal就会优先注入Cat
// Springfox Swagger 库本身也会自动注册一个默认的 SwaggerResourcesProvider。如果我们不使用 @Primary 标记我们这个自定义的类，Swagger UI 就会去使用默认的那个（它只能扫描当前网关的接口，看不到下游微服务），导致聚合功能失效。
@Primary
// SwaggerResourcesProvider：实现了 Swagger 的资源提供接口，这是聚合文档的关键。
// WebFluxConfigurer：实现了 WebFlux 配置接口，用于配置静态资源映射。
public class SwaggerProvider implements SwaggerResourcesProvider, WebFluxConfigurer
{
    /**
     * Swagger2默认的url后缀
     */
    public static final String SWAGGER2URL = "/v2/api-docs";

    /**
     * 网关路由
     * @description: 从配置文件中获取路由定义(这里我们的项目是从注册中心nacos中读取所有的路由定义)
     * @function: 通过getRoutes()方法获取当前网关中配置中了哪些路由，从而知道下游有哪些服务
     */
    @Lazy
    @Autowired
    private RouteLocator routeLocator;

    /**
     * GatewayProperties 是 Spring Cloud Gateway 内部的一个类，它对应了配置文件中以 spring.cloud.gateway 开头的所有配置。
     * routeLocator.getRoutes() 能获取到所有激活的路由，但它返回的 Route 对象主要关注的是“如何转发”（Filter、URI 等），对于路由定义的原始配置细节（特别是 Path 断言的具体参数）封装得可能不够直接，或者在某些版本中获取原始路径参数比较麻烦。
     * GatewayProperties直接映射了配置文件的结构
     */
    @Autowired
    private GatewayProperties gatewayProperties;

    /**
     * 动态发现下游服务并构建Swagger资源列表
     *
     * @return
     */
    @Override
    public List<SwaggerResource> get()
    {
        // resourceList 用于存放最终返回给前端的 Swagger 资源信息
        List<SwaggerResource> resourceList = new ArrayList<>();
        // routes 用于临时存放路由 ID。
        List<String> routes = new ArrayList<>();
        // 获取网关中配置的route
        routeLocator.getRoutes().subscribe(route -> routes.add(route.getId()));
        // 双重校验配置文件中的路由ID是否存在第二部获取的动态路由列表中
        //
        gatewayProperties.getRoutes().stream()
                .filter(routeDefinition -> routes
                        .contains(routeDefinition.getId()))
                .forEach(routeDefinition -> routeDefinition.getPredicates().stream()
                        .filter(predicateDefinition -> "Path".equalsIgnoreCase(predicateDefinition.getName()))
                        .filter(predicateDefinition -> !"pmhub-auth".equalsIgnoreCase(routeDefinition.getId()))
                        .forEach(predicateDefinition -> resourceList
                                .add(swaggerResource(routeDefinition.getId(), predicateDefinition.getArgs()
                                        .get(NameUtils.GENERATED_NAME_PREFIX + "0").replace("/**", SWAGGER2URL)))));
        return resourceList;
    }

    /**
     * 构建swaggerResource方法，调用SwaggerResource的Setter方法得到SwaggerResoure对象
     * @param name
     * @param location
     * @return
     */
    private SwaggerResource swaggerResource(String name, String location)
    {
        SwaggerResource swaggerResource = new SwaggerResource();
        swaggerResource.setName(name);
        swaggerResource.setLocation(location);
        swaggerResource.setSwaggerVersion("2.0");
        return swaggerResource;
    }

    /**
     * 定义对外暴露的URL路径模式
     * @description: 告诉 Spring，当浏览器请求 /swagger-ui/** 开头的静态文件（如 HTML 页面、CSS 样式表、JS 脚本）时，去哪里找这些文件。
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        /** swagger-ui 地址 */
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/");
    }
}
