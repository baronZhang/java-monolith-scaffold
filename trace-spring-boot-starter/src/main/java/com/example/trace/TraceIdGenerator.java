package com.example.trace;

/**
 * traceId 生成策略。
 *
 * <p>自动配置里注册了默认实现 {@link RandomTraceIdGenerator}，并标注了 {@code @ConditionalOnMissingBean}：
 * 使用方自己声明一个这个类型的 Bean，默认实现就会自动让位——这是 starter "约定优于配置、但允许覆盖"的典型写法。
 */
@FunctionalInterface
public interface TraceIdGenerator {

    /** 生成一个新的 traceId。 */
    String generate();
}
