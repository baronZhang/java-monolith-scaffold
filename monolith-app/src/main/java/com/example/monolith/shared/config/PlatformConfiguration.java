package com.example.monolith.shared.config;

import com.example.monolith.shared.id.SnowflakeIdGenerator;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 平台级的公共 Bean。
 *
 * <ul>
 *   <li>{@code @EnableAsync}：Spring Modulith 的 @ApplicationModuleListener 本质上是 @Async + 事务提交后触发，需要开启异步；</li>
 *   <li>{@code @EnableScheduling}：超时订单扫描、失败事件重投都是定时任务。</li>
 * </ul>
 * 线程池用 Spring Boot 自动配置的 applicationTaskExecutor，参数在 application.yml 的 spring.task.* 里，
 * 为什么要改默认值见"单体 3.5 线程池与 CompletableFuture"。
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class PlatformConfiguration {

    /**
     * 统一的时钟。业务代码一律通过注入的 Clock 取时间，不直接调 Instant.now()：
     * 测试时换成可以拨动的时钟，"15 分钟后超时取消"这种逻辑不用真的等 15 分钟。
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** 订单号生成器；多实例部署时每个实例配置不同的 app.id.worker-id。 */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(@Value("${app.id.worker-id:1}") long workerId, Clock clock) {
        return new SnowflakeIdGenerator(workerId, clock);
    }
}
