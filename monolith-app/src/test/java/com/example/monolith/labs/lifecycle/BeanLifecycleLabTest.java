package com.example.monolith.labs.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.labs.LabReport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 原理实验：一个 Bean 从创建到销毁，每个扩展点被调用的真实顺序。
 * 每一步都往同一个列表里记一笔，最后按顺序打印出来。
 */
class BeanLifecycleLabTest {

    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Test
    void 记录Bean生命周期的每一步() {
        EVENTS.clear();
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(LifecycleConfig.class)) {
            EVENTS.add("—— 容器启动完成，Bean 可以使用了 ——");
            context.getBean(LifecycleProbe.class);
        }

        for (int i = 0; i < EVENTS.size(); i++) {
            LabReport.print("生命周期 {}. {}", i + 1, EVENTS.get(i));
        }
        assertThat(EVENTS)
                .containsExactly(
                        "构造器（实例化）",
                        "属性注入（@Autowired setter）",
                        "BeanNameAware.setBeanName",
                        "BeanFactoryAware.setBeanFactory",
                        "ApplicationContextAware.setApplicationContext",
                        "BeanPostProcessor.postProcessBeforeInitialization",
                        "@PostConstruct",
                        "InitializingBean.afterPropertiesSet",
                        "@Bean(initMethod)",
                        "BeanPostProcessor.postProcessAfterInitialization（AOP 代理在这一步生成）",
                        "—— 容器启动完成，Bean 可以使用了 ——",
                        "@PreDestroy",
                        "DisposableBean.destroy",
                        "@Bean(destroyMethod)");
    }

    @Configuration(proxyBeanMethods = false)
    static class LifecycleConfig {

        @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
        LifecycleProbe lifecycleProbe() {
            return new LifecycleProbe();
        }

        @Bean
        Dependency dependency() {
            return new Dependency();
        }

        /** BeanPostProcessor 要声明成 static：它必须比普通 Bean 更早创建 */
        @Bean
        static BeanPostProcessor recordingPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof LifecycleProbe) {
                        EVENTS.add("BeanPostProcessor.postProcessBeforeInitialization");
                    }
                    return bean;
                }

                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof LifecycleProbe) {
                        EVENTS.add("BeanPostProcessor.postProcessAfterInitialization（AOP 代理在这一步生成）");
                    }
                    return bean;
                }
            };
        }
    }

    static class Dependency {}

    static class LifecycleProbe
            implements BeanNameAware, BeanFactoryAware, ApplicationContextAware, InitializingBean, DisposableBean {

        LifecycleProbe() {
            EVENTS.add("构造器（实例化）");
        }

        @Autowired
        void setDependency(Dependency dependency) {
            EVENTS.add("属性注入（@Autowired setter）");
        }

        @Override
        public void setBeanName(String name) {
            EVENTS.add("BeanNameAware.setBeanName");
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            EVENTS.add("BeanFactoryAware.setBeanFactory");
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            EVENTS.add("ApplicationContextAware.setApplicationContext");
        }

        @PostConstruct
        void postConstruct() {
            EVENTS.add("@PostConstruct");
        }

        @Override
        public void afterPropertiesSet() {
            EVENTS.add("InitializingBean.afterPropertiesSet");
        }

        void customInit() {
            EVENTS.add("@Bean(initMethod)");
        }

        @PreDestroy
        void preDestroy() {
            EVENTS.add("@PreDestroy");
        }

        @Override
        public void destroy() {
            EVENTS.add("DisposableBean.destroy");
        }

        void customDestroy() {
            EVENTS.add("@Bean(destroyMethod)");
        }
    }
}
