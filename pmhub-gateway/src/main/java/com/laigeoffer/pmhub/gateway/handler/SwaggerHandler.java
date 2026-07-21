package com.laigeoffer.pmhub.gateway.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import springfox.documentation.swagger.web.*;

import java.util.Optional;

/**
 * 配合前端 Swagger UI 页面工作而提供的数据接口。
 *
 * @author JingYi
 */
@RestController
@RequestMapping("/swagger-resources")
public class SwaggerHandler
{
    @Autowired(required = false)
    private SecurityConfiguration securityConfiguration;

    @Autowired(required = false)
    private UiConfiguration uiConfiguration;

    // 使用final字段保证swaggerResources一旦被赋值就不能改变
    private final SwaggerResourcesProvider swaggerResources;

    // 在构造器注入，不但可以配合final保证不变性，并且在类加载阶段就可以告诉是否缺东西，字段注入的话运行到才可以报空指针
    @Autowired
    public SwaggerHandler(SwaggerResourcesProvider swaggerResources)
    {
        this.swaggerResources = swaggerResources;
    }

    /**
     * 提供Swagger的安全认证配置
     * @return
     */
    @GetMapping("/configuration/security")
    public Mono<ResponseEntity<SecurityConfiguration>> securityConfiguration()
    {
        return Mono.just(new ResponseEntity<>(
                Optional.ofNullable(securityConfiguration).orElse(SecurityConfigurationBuilder.builder().build()),
                HttpStatus.OK));
    }

    /**
     * 提供Swagger UI的界面显示配置
     * @return
     */
    @GetMapping("/configuration/ui")
    public Mono<ResponseEntity<UiConfiguration>> uiConfiguration()
    {
        return Mono.just(new ResponseEntity<>(
                Optional.ofNullable(uiConfiguration).orElse(UiConfigurationBuilder.builder().build()), HttpStatus.OK));
    }

    // 用于提供所有微服务的API文档资源列表，我们要看的API列表就是访问这个网址
    @SuppressWarnings("rawtypes")
    @GetMapping("")
    public Mono<ResponseEntity> swaggerResources()
    {
        return Mono.just((new ResponseEntity<>(swaggerResources.get(), HttpStatus.OK)));
    }
}
