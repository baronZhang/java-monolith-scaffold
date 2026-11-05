package com.example.monolith.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试的组合注解：启动完整的应用上下文（所有模块、真实的过滤器链、H2 + Flyway），用 MockMvc 调接口。
 * 用同一组注解的测试类会复用同一个 Spring 上下文（测试框架按配置缓存），不用每个类都重启一次。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
public @interface IntegrationTest {}
