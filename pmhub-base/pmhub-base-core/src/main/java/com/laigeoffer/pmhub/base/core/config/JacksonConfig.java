package com.laigeoffer.pmhub.base.core.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;


/**
 * 自定义SpringMVC中的JSON数据的序列化与反序列化行为
 */
@Configuration
public class JacksonConfig {
    // Spring框架中用来处理HTTP请求和响应中的JSON数据的转换器。
    // Controller返回一个对象时，Spring会调用这个转换器把对象转成JSON字符串写给前端
    // SpringMVC中的有一个HTTP消息转换器，用于处理JSON数据的转换
    @Bean
    public MappingJackson2HttpMessageConverter jackson2HttpMessageConverter() {
        final Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        // 在序列化Java对象为JSON时，不包含值为null的属性，减少JSON数据大小
        builder.serializationInclusion(JsonInclude.Include.NON_NULL);
        final ObjectMapper objectMapper = builder.build();
        // Jackson的扩展模块，用于添加自定义的序列化和反序列化器
        SimpleModule simpleModule = new SimpleModule();
        // Long 转为 String 防止 js 丢失精度
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        objectMapper.registerModule(simpleModule);
        // 忽略 transient 关键词属性，transient 属性本意就是不想被序列化(保护一些敏感信息比如密码)
        objectMapper.configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true);
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }

}
