package com.example.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

/**
 * traceId 的自动配置。
 *
 * <p>Spring Boot 启动时读取 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}，
 * 把里面列出的类当成配置类候选，再逐个评估上面的条件注解，全部满足才生效：
 * <ul>
 *   <li>{@code @ConditionalOnWebApplication}：只在 Servlet 类型的 Web 应用里生效；</li>
 *   <li>{@code @ConditionalOnClass}：用类名字符串而不是 Class 字面量，类路径上没有 Servlet API 时也不会因为加载类而报错；</li>
 *   <li>{@code @ConditionalOnProperty}：{@code scaffold.trace.enabled=false} 可以整体关掉，不配置时默认开启。</li>
 * </ul>
 *
 * <p>注意：自动配置类不能被应用的组件扫描扫到（它不在应用的包下），否则条件注解的评估顺序会乱掉。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnProperty(prefix = "scaffold.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TraceProperties.class)
public class TraceAutoConfiguration {

    /** 默认的生成策略；使用方声明了自己的 TraceIdGenerator，这个就不注册。 */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdGenerator traceIdGenerator() {
        return new RandomTraceIdGenerator();
    }

    /** 用 FilterRegistrationBean 注册过滤器，才能明确控制顺序和匹配路径。 */
    @Bean
    @ConditionalOnMissingBean(name = "traceIdFilterRegistration")
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(
            TraceProperties properties, TraceIdGenerator traceIdGenerator) {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter(properties, traceIdGenerator));
        // 最高优先级：排在 Spring Security 过滤器链（默认 order = -100）前面
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /** 让异步线程也带上 traceId；使用方已经有自己的 TaskDecorator 时让位。 */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
