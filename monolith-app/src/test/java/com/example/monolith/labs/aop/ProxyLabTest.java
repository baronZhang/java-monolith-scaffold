package com.example.monolith.labs.aop;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.labs.LabReport;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原理实验：Spring AOP 的两种代理（JDK 动态代理、CGLIB），以及"自调用为什么绕过代理"。
 * 用 ProxyFactory 手工创建代理，看得最清楚；再用 Spring Boot 的事务自动配置验证它的默认选择。
 */
class ProxyLabTest {

    @Test
    void JDK动态代理_只实现接口_不能当成实现类使用() {
        ProxyFactory factory = new ProxyFactory(new DefaultGreeter());
        factory.addInterface(Greeter.class);
        factory.setProxyTargetClass(false);

        Object proxy = factory.getProxy();

        LabReport.print(
                "JDK 动态代理：类名 {}，是 Greeter 吗？{}，是 DefaultGreeter 吗？{}",
                proxy.getClass().getSimpleName(),
                proxy instanceof Greeter,
                proxy instanceof DefaultGreeter);
        assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
        assertThat(proxy).isInstanceOf(Greeter.class).isNotInstanceOf(DefaultGreeter.class);
    }

    @Test
    void CGLIB代理_是目标类的子类() {
        ProxyFactory factory = new ProxyFactory(new DefaultGreeter());
        factory.setProxyTargetClass(true);

        Object proxy = factory.getProxy();

        LabReport.print(
                "CGLIB 代理：类名 {}，父类 {}",
                proxy.getClass().getSimpleName(),
                proxy.getClass().getSuperclass().getSimpleName());
        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
        assertThat(proxy).isInstanceOf(DefaultGreeter.class);
    }

    @Test
    void SpringBoot默认用CGLIB_即使Bean实现了接口() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TransactionAutoConfiguration.class))
                .withUserConfiguration(TransactionalBeanConfig.class);

        runner.run(context -> {
            Object service = context.getBean("orderService");
            LabReport.print(
                    "Spring Boot 默认（spring.aop.proxy-target-class=true）：实现了接口的事务 Bean 是 CGLIB 代理吗？{}",
                    AopUtils.isCglibProxy(service));
            assertThat(AopUtils.isCglibProxy(service)).isTrue();
            // 所以按实现类注入也没问题
            assertThat(context.getBean(DefaultOrderService.class)).isNotNull();
        });

        runner.withPropertyValues("spring.aop.proxy-target-class=false").run(context -> {
            Object service = context.getBean("orderService");
            LabReport.print(
                    "改成 proxy-target-class=false：变成 JDK 代理吗？{}；按实现类还能取到 Bean 吗？{}",
                    AopUtils.isJdkDynamicProxy(service),
                    context.getBeanNamesForType(DefaultOrderService.class).length > 0);
            assertThat(AopUtils.isJdkDynamicProxy(service)).isTrue();
            // JDK 代理只实现了接口：按实现类型找不到 Bean，@Autowired DefaultOrderService 会启动失败
            assertThat(context.getBeanNamesForType(DefaultOrderService.class)).isEmpty();
        });
    }

    @Test
    void 自调用不经过代理_所以切面不生效() {
        List<String> intercepted = new CopyOnWriteArrayList<>();
        ProxyFactory factory = new ProxyFactory(new DefaultGreeter());
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> {
            intercepted.add(invocation.getMethod().getName());
            return invocation.proceed();
        });
        DefaultGreeter proxy = (DefaultGreeter) factory.getProxy();

        proxy.outer();

        LabReport.print("自调用：调用 outer()，outer 内部用 this 调 inner()，被拦截的方法只有 {}", intercepted);
        assertThat(intercepted).containsExactly("outer");
    }

    @Test
    void 暴露代理后_通过AopContext调用才会被拦截() {
        List<String> intercepted = new CopyOnWriteArrayList<>();
        ProxyFactory factory = new ProxyFactory(new DefaultGreeter());
        factory.setProxyTargetClass(true);
        factory.setExposeProxy(true);
        factory.addAdvice((MethodInterceptor) invocation -> {
            intercepted.add(invocation.getMethod().getName());
            return invocation.proceed();
        });
        DefaultGreeter proxy = (DefaultGreeter) factory.getProxy();

        proxy.outerViaProxy();

        LabReport.print("exposeProxy + AopContext.currentProxy()：被拦截的方法是 {}（能用，但更推荐拆成两个 Bean）", intercepted);
        assertThat(intercepted).containsExactly("outerViaProxy", "inner");
    }

    @Test
    void 多个切面按顺序层层包裹_像洋葱一样() {
        List<String> trace = new CopyOnWriteArrayList<>();
        ProxyFactory factory = new ProxyFactory(new TracingGreeter(trace));
        factory.setProxyTargetClass(true);
        // 声明的顺序故意和 order 相反。容器里的自动代理创建器会先按 order 排序再组装拦截器链
        // （值越小越靠外层），这里手工做同样的排序
        List<Advisor> advisors = new ArrayList<>(List.of(advisor("事务", trace, 2), advisor("日志", trace, 1)));
        AnnotationAwareOrderComparator.sort(advisors);
        advisors.forEach(factory::addAdvisor);

        ((Greeter) factory.getProxy()).greet("张三");

        LabReport.print("两个切面的执行顺序：{}", trace);
        assertThat(trace).containsExactly("日志-前", "事务-前", "目标方法", "事务-后", "日志-后");
    }

    private static DefaultPointcutAdvisor advisor(String name, List<String> trace, int order) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor((MethodInterceptor) invocation -> {
            trace.add(name + "-前");
            try {
                return invocation.proceed();
            } finally {
                trace.add(name + "-后");
            }
        });
        advisor.setOrder(order);
        return advisor;
    }

    /** 目标方法执行时也记一笔，方便看清切面包在它的哪一层 */
    static class TracingGreeter implements Greeter {

        private final List<String> trace;

        TracingGreeter(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public String greet(String name) {
            trace.add("目标方法");
            return "你好，" + name;
        }
    }

    interface Greeter {
        String greet(String name);
    }

    static class DefaultGreeter implements Greeter {

        @Override
        public String greet(String name) {
            return "你好，" + name;
        }

        public String outer() {
            return inner();
        }

        public String outerViaProxy() {
            return ((DefaultGreeter) AopContext.currentProxy()).inner();
        }

        public String inner() {
            return "inner";
        }
    }

    interface OrderService {
        void place();
    }

    static class DefaultOrderService implements OrderService {

        @Override
        @Transactional
        public void place() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class TransactionalBeanConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:aoplab-" + UUID.randomUUID());
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        OrderService orderService() {
            return new DefaultOrderService();
        }
    }
}
