package com.example.monolith.labs.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.monolith.labs.LabReport;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 原理实验：循环依赖与三级缓存。
 *
 * <p>用 SpringApplication 启动（而不是裸的 ApplicationContext），因为"默认禁止循环依赖"是 Spring Boot 2.6 加的默认值
 * （spring.main.allow-circular-references=false），Spring Framework 本身默认是允许的。
 */
class CircularDependencyLabTest {

    @Test
    void SpringBoot默认禁止循环依赖_字段注入的环启动就失败() {
        Throwable failure = catchThrowable(() -> run(FieldCycleConfig.class, false));

        Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
        LabReport.print("Spring Boot 默认：字段注入的循环依赖启动失败，根因 {}", root.getClass().getSimpleName());
        assertThat(root).isInstanceOf(BeanCurrentlyInCreationException.class);
    }

    @Test
    void 打开allow_circular_references后_字段注入的环靠三级缓存解开() {
        try (ConfigurableApplicationContext context = run(FieldCycleConfig.class, true)) {
            FieldA a = context.getBean(FieldA.class);
            LabReport.print("允许循环依赖后：A 持有 B、B 持有 A，同一个对象吗？{}", a.b.a == a);
            assertThat(a.b.a).isSameAs(a);
        }
    }

    @Test
    void 构造器注入的环_三级缓存也解不开() {
        Throwable failure = catchThrowable(() -> run(ConstructorCycleConfig.class, true));

        Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
        LabReport.print(
                "构造器注入的循环依赖：即使允许循环依赖也失败，根因 {}（对象还没构造出来，没有半成品可以提前暴露）",
                root.getClass().getSimpleName());
        assertThat(root).isInstanceOf(BeanCurrentlyInCreationException.class);
    }

    @Test
    void 构造器参数加Lazy_注入的是代理_环被打断() {
        try (ConfigurableApplicationContext context = run(LazyConstructorCycleConfig.class, false)) {
            LazyA a = context.getBean(LazyA.class);
            LabReport.print(
                    "@Lazy：B 里注入的 A 是代理吗？{}；第一次调用时才去容器里取真正的 A，调用结果 {}", AopUtils.isAopProxy(a.b.a), a.b.a.name());
            assertThat(AopUtils.isAopProxy(a.b.a)).isTrue();
            assertThat(a.b.a.name()).isEqualTo("A");
        }
    }

    @Test
    void 循环依赖加AOP_三级缓存提前生成代理_注入的就是代理() {
        try (ConfigurableApplicationContext context = run(AopCycleConfig.class, true)) {
            AopA a = context.getBean(AopA.class);
            // a 是代理对象：不能直接读 a.b（代理对象自己的字段是 null），要通过方法调用转到目标对象上
            AopA injectedIntoB = a.getB().a;
            LabReport.print(
                    "循环依赖 + AOP 代理：B 拿到的 A 是代理吗？{}；和容器里最终的 A 是同一个对象吗？{}",
                    AopUtils.isAopProxy(injectedIntoB),
                    injectedIntoB == a);
            // 三级缓存里放的是 ObjectFactory：B 来要 A 的早期引用时，由 AOP 的后处理器提前生成代理，
            // 保证 B 拿到的和最终放进容器的是同一个代理对象
            assertThat(AopUtils.isAopProxy(injectedIntoB)).isTrue();
            assertThat(injectedIntoB).isSameAs(a);
        }
    }

    @Test
    void 循环依赖加Async_Spring7实测可以正常启动() {
        // 很多资料的结论是"会启动失败"，报 "...has been injected into other beans [...] in its raw version
        // as part of a circular reference..."：因为 @Async 的后处理器以前不参与提前暴露代理。
        // Spring Framework 7.0.9 上实测：AbstractAdvisingBeanPostProcessor 已经实现了 getEarlyBeanReference，
        // @Async 的代理也能在三级缓存里提前生成，所以能正常启动。哪个版本开始变的没有逐一验证。
        try (ConfigurableApplicationContext context = run(AsyncCycleConfig.class, true)) {
            AsyncA a = context.getBean(AsyncA.class);
            AsyncA injectedIntoB = a.getB().a;
            LabReport.print(
                    "循环依赖 + @Async（Spring Framework 7.0.9）：启动成功；B 拿到的 A 是代理吗？{}；和最终的 A 是同一个对象吗？{}",
                    AopUtils.isAopProxy(injectedIntoB),
                    injectedIntoB == a);
            assertThat(AopUtils.isAopProxy(injectedIntoB)).isTrue();
            assertThat(injectedIntoB).isSameAs(a);
        }
    }

    private static ConfigurableApplicationContext run(Class<?> config, boolean allowCircularReferences) {
        return new SpringApplicationBuilder(config)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.main.log-startup-info=false",
                        "logging.level.org.springframework=off",
                        "spring.main.allow-circular-references=" + allowCircularReferences)
                .run();
    }

    // ---------- 字段注入的环 ----------
    @Configuration(proxyBeanMethods = false)
    static class FieldCycleConfig {
        @Bean
        FieldA fieldA() {
            return new FieldA();
        }

        @Bean
        FieldB fieldB() {
            return new FieldB();
        }
    }

    static class FieldA {
        @Autowired
        FieldB b;
    }

    static class FieldB {
        @Autowired
        FieldA a;
    }

    // ---------- 构造器注入的环 ----------
    @Configuration(proxyBeanMethods = false)
    static class ConstructorCycleConfig {
        @Bean
        CtorA ctorA(CtorB b) {
            return new CtorA(b);
        }

        @Bean
        CtorB ctorB(CtorA a) {
            return new CtorB(a);
        }
    }

    record CtorA(CtorB b) {}

    record CtorB(CtorA a) {}

    // ---------- @Lazy 打断环 ----------
    @Configuration(proxyBeanMethods = false)
    static class LazyConstructorCycleConfig {
        @Bean
        LazyA lazyA(LazyB b) {
            return new LazyA(b);
        }

        @Bean
        LazyB lazyB(@Lazy LazyA a) {
            return new LazyB(a);
        }
    }

    static class LazyA {
        final LazyB b;

        LazyA(LazyB b) {
            this.b = b;
        }

        public String name() {
            return "A";
        }
    }

    static class LazyB {
        final LazyA a;

        LazyB(LazyA a) {
            this.a = a;
        }
    }

    // ---------- 循环依赖 + AOP 代理 ----------
    /**
     * 自动代理创建器（DefaultAdvisorAutoProxyCreator）和 @Transactional、@Aspect 背后用的是同一个父类
     * AbstractAutoProxyCreator：它实现了 getEarlyBeanReference，能在三级缓存里提前生成代理。
     */
    @Configuration(proxyBeanMethods = false)
    static class AopCycleConfig {
        @Bean
        AopA aopA() {
            return new AopA();
        }

        @Bean
        AopB aopB() {
            return new AopB();
        }

        @Bean
        static DefaultAdvisorAutoProxyCreator autoProxyCreator() {
            DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
            creator.setProxyTargetClass(true);
            return creator;
        }

        /** 只给 AopA 加一个什么都不做的拦截器：目的是让它被代理 */
        @Bean
        static Advisor aopAAdvisor() {
            Pointcut pointcut = new StaticMethodMatcherPointcut() {
                @Override
                public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                    return AopA.class.isAssignableFrom(targetClass);
                }
            };
            return new DefaultPointcutAdvisor(pointcut, (MethodInterceptor) MethodInvocation::proceed);
        }
    }

    static class AopA {
        @Autowired
        AopB b;

        public AopB getB() {
            return b;
        }
    }

    static class AopB {
        @Autowired
        AopA a;
    }

    // ---------- 循环依赖 + @Async ----------
    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    static class AsyncCycleConfig {
        @Bean
        AsyncA asyncA() {
            return new AsyncA();
        }

        @Bean
        AsyncB asyncB() {
            return new AsyncB();
        }
    }

    static class AsyncA {
        @Autowired
        AsyncB b;

        public AsyncB getB() {
            return b;
        }

        @Async
        public void work() {}
    }

    static class AsyncB {
        @Autowired
        AsyncA a;
    }
}
