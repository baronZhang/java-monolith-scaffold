package com.example.monolith.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 持久层切片测试的组合注解：数据源 + Flyway + 事务 + MyBatis-Plus 的 Mapper，不加载 Web、安全和业务 Service。
 * 每个测试方法结束后自动回滚。
 *
 * <p>为什么不用 MyBatis-Plus 自带的 @MybatisPlusTest：3.5.17 版本里它的元注解还是 Spring Boot 3 的包名
 * （org.springframework.boot.test.autoconfigure.filter.TypeExcludeFilters 等），Spring Boot 4 里这些类已经搬家。
 * JVM 遇到找不到类的注解会直接忽略，于是"切片"悄悄退化成了全量扫描——测试照样能跑，只是不再是切片了。
 * 这里改用 Spring Boot 4 自己的 @JdbcTest，再把 MyBatis-Plus 的自动配置加进来。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
@ActiveProfiles("test")
public @interface MybatisSliceTest {}
