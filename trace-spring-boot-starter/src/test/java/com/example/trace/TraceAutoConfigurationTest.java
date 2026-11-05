package com.example.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

/**
 * 自动配置的测试：用 ApplicationContextRunner 在几毫秒内起一个最小容器，逐条验证条件注解。
 * 这是 Spring Boot 官方测试自动配置的方式，比 @SpringBootTest 快得多，也不受应用其他配置干扰。
 */
class TraceAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner =
            new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class));

    @Test
    void 在Servlet应用里默认注册过滤器和默认实现() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(TraceIdGenerator.class);
            assertThat(context.getBean(TraceIdGenerator.class)).isInstanceOf(RandomTraceIdGenerator.class);
            assertThat(context).hasSingleBean(MdcTaskDecorator.class);
            FilterRegistrationBean<?> registration =
                    context.getBean("traceIdFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        });
    }

    @Test
    void 配置开关关掉后什么都不注册() {
        webRunner.withPropertyValues("scaffold.trace.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TraceIdGenerator.class);
            assertThat(context).doesNotHaveBean("traceIdFilterRegistration");
        });
    }

    @Test
    void 使用方声明了自己的实现时默认实现让位() {
        webRunner.withUserConfiguration(CustomGeneratorConfig.class).run(context -> {
            assertThat(context).hasSingleBean(TraceIdGenerator.class);
            assertThat(context.getBean(TraceIdGenerator.class).generate()).isEqualTo("custom-trace-id");
        });
    }

    @Test
    void 使用方已有TaskDecorator时不覆盖() {
        webRunner.withUserConfiguration(CustomDecoratorConfig.class).run(context -> {
            assertThat(context).hasSingleBean(TaskDecorator.class);
            assertThat(context).doesNotHaveBean(MdcTaskDecorator.class);
        });
    }

    @Test
    void 非Web应用里不生效() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(TraceIdGenerator.class));
    }

    @Test
    void 配置项绑定到属性对象() {
        webRunner
                .withPropertyValues("scaffold.trace.header-name=X-Request-Id", "scaffold.trace.access-log=false")
                .run(context -> {
                    TraceProperties properties = context.getBean(TraceProperties.class);
                    assertThat(properties.getHeaderName()).isEqualTo("X-Request-Id");
                    assertThat(properties.isAccessLog()).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomGeneratorConfig {
        @Bean
        TraceIdGenerator customGenerator() {
            return () -> "custom-trace-id";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecoratorConfig {
        @Bean
        TaskDecorator customDecorator() {
            return runnable -> runnable;
        }
    }
}
