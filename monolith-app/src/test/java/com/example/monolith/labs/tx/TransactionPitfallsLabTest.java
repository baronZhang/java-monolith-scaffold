package com.example.monolith.labs.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.monolith.labs.LabReport;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.RollbackOn;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 原理实验：@Transactional 失效的几种场景和传播行为（Spring Framework 7.0 实测）。
 *
 * <p>用一个最小的 Spring 容器（不是 Spring Boot），把代理方式写死成 CGLIB（和 Spring Boot 的默认值一致），
 * 每个场景都看"数据最后有没有留在数据库里"来判断事务到底生没生效。
 */
class TransactionPitfallsLabTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void 自调用_事务注解不生效() {
        LabService service = start(LabConfig.class).getBean(LabService.class);

        // 外层方法没有 @Transactional，它用 this 调用了本类里有 @Transactional 的方法
        assertThatThrownBy(() -> service.callInternally("self-invocation")).isInstanceOf(IllegalStateException.class);
        int saved = service.count("self-invocation");

        // 同一个方法通过代理调用：回滚了
        assertThatThrownBy(() -> service.insertThenFail("via-proxy")).isInstanceOf(IllegalStateException.class);
        int savedViaProxy = service.count("via-proxy");

        LabReport.print("自调用：抛异常后数据仍在库里 {} 行（事务没生效）；同一个方法经代理调用，剩下 {} 行（回滚了）", saved, savedViaProxy);
        assertThat(saved).isEqualTo(1);
        assertThat(savedViaProxy).isZero();
    }

    @Test
    void 非public方法_Spring6之后的CGLIB代理照样有事务() {
        LabService service = start(LabConfig.class).getBean(LabService.class);

        boolean protectedActive = service.protectedProbe();
        boolean packagePrivateActive = service.packagePrivateProbe();

        LabReport.print(
                "非 public 方法（CGLIB 代理）：protected 方法里有事务吗？{}；包级私有方法里有事务吗？{}", protectedActive, packagePrivateActive);
        // 很多资料说"非 public 方法上的 @Transactional 不生效"——那是 Spring 5 及以前的结论。
        // Spring 6.0 起，基于类的代理（CGLIB）也会拦截 protected 和包级私有方法；private 方法仍然无法被代理。
        assertThat(protectedActive).isTrue();
        assertThat(packagePrivateActive).isTrue();
    }

    @Test
    void final方法_代理拦不住_而且依赖注入的字段是null() {
        LabService service = start(LabConfig.class).getBean(LabService.class);
        assertThat(AopUtils.isCglibProxy(service)).isTrue();

        // CGLIB 生成的是子类，final 方法没法重写：调用直接落在代理对象自己身上，
        // 而代理对象是绕过构造器创建的，它的字段（jdbc）都是 null
        assertThatThrownBy(() -> service.finalMethod("final")).isInstanceOf(NullPointerException.class);
        LabReport.print("final 方法：调用落在代理对象本身，注入的字段为 null，抛出 NullPointerException");
    }

    @Test
    void 异常被catch吞掉_事务正常提交() {
        LabService service = start(LabConfig.class).getBean(LabService.class);

        service.insertThenSwallow("swallowed");

        int saved = service.count("swallowed");
        LabReport.print("异常在方法内部被 catch：数据保留 {} 行（事务不知道出过错，照常提交）", saved);
        assertThat(saved).isEqualTo(1);
    }

    @Test
    void 检查型异常默认不回滚_加rollbackFor才回滚() {
        LabService service = start(LabConfig.class).getBean(LabService.class);

        assertThatThrownBy(() -> service.insertThenThrowChecked("checked")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> service.insertThenThrowCheckedWithRollbackFor("checked-rollback-for"))
                .isInstanceOf(Exception.class);

        int withoutRollbackFor = service.count("checked");
        int withRollbackFor = service.count("checked-rollback-for");
        LabReport.print(
                "检查型异常：默认规则下数据保留 {} 行（提交了）；加 rollbackFor = Exception.class 后保留 {} 行（回滚了）",
                withoutRollbackFor,
                withRollbackFor);
        assertThat(withoutRollbackFor).isEqualTo(1);
        assertThat(withRollbackFor).isZero();
    }

    @Test
    void Spring6点2起可以全局配置检查型异常也回滚() {
        LabService service = start(AllExceptionsRollbackConfig.class).getBean(LabService.class);

        assertThatThrownBy(() -> service.insertThenThrowChecked("global-rollback-on"))
                .isInstanceOf(Exception.class);

        int saved = service.count("global-rollback-on");
        LabReport.print("@EnableTransactionManagement(rollbackOn = ALL_EXCEPTIONS)：检查型异常也回滚，数据保留 {} 行", saved);
        assertThat(saved).isZero();
    }

    @Test
    void 内层REQUIRED抛异常_外层catch住_提交时报UnexpectedRollbackException() {
        LabService service = start(LabConfig.class).getBean(LabService.class);

        // 内层和外层是同一个事务；内层抛出运行时异常时，事务拦截器已经把整个事务标记为"只能回滚"
        assertThatThrownBy(() -> service.outerCatchesInnerRequired("rollback-only"))
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("rollback-only");

        int saved = service.count("rollback-only-outer");
        LabReport.print("内层 REQUIRED 失败、外层 catch：外层以为没事，提交时抛 UnexpectedRollbackException，外层写的数据剩 {} 行", saved);
        assertThat(saved).isZero();
    }

    @Test
    void REQUIRES_NEW_内层独立提交_不受外层回滚影响() {
        LabService service = start(LabConfig.class).getBean(LabService.class);

        assertThatThrownBy(() -> service.outerFailsAfterRequiresNew("requires-new"))
                .isInstanceOf(IllegalStateException.class);

        int outer = service.count("requires-new-outer");
        int inner = service.count("requires-new-inner");
        LabReport.print("REQUIRES_NEW：外层回滚后，外层数据 {} 行、内层数据 {} 行（内层已经独立提交）", outer, inner);
        assertThat(outer).isZero();
        assertThat(inner).isEqualTo(1);
    }

    @Test
    void NESTED_内层回滚到保存点_外层照常提交() {
        LabService service = start(LabConfig.class).getBean(LabService.class);

        service.outerCatchesNested("nested");

        int outer = service.count("nested-outer");
        int inner = service.count("nested-inner");
        LabReport.print("NESTED：内层失败只回滚到保存点，外层数据 {} 行、内层数据 {} 行", outer, inner);
        assertThat(outer).isEqualTo(1);
        assertThat(inner).isZero();
    }

    @Test
    void MANDATORY必须在事务里调用_NEVER不能在事务里调用() {
        AnnotationConfigApplicationContext ctx = start(LabConfig.class);
        LabService service = ctx.getBean(LabService.class);
        InnerService inner = ctx.getBean(InnerService.class);

        assertThatThrownBy(inner::mandatory).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(service::callNeverInsideTransaction).isInstanceOf(IllegalTransactionStateException.class);
        LabReport.print("MANDATORY 在事务外调用、NEVER 在事务里调用：都抛 IllegalTransactionStateException");
    }

    private AnnotationConfigApplicationContext start(Class<?> config) {
        context = new AnnotationConfigApplicationContext(config);
        context.getBean(JdbcTemplate.class)
                .execute("CREATE TABLE lab_tx (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64))");
        return context;
    }

    // ===================== 实验用的容器配置和 Bean =====================

    /** 两个实验容器共用的 Bean；事务开关放在子类上，每个容器只有一个 @EnableTransactionManagement */
    abstract static class LabBeans {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:txlab-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        InnerService innerService(JdbcTemplate jdbc) {
            return new InnerService(jdbc);
        }

        @Bean
        LabService labService(JdbcTemplate jdbc, InnerService inner) {
            return new LabService(jdbc, inner);
        }
    }

    /** 和 Spring Boot 默认一致：proxyTargetClass = true，即 CGLIB 代理 */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class LabConfig extends LabBeans {}

    /** Spring Framework 6.2 新增：全局声明"任何异常都回滚" */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true, rollbackOn = RollbackOn.ALL_EXCEPTIONS)
    static class AllExceptionsRollbackConfig extends LabBeans {}

    static class LabService {

        private final JdbcTemplate jdbc;
        private final InnerService inner;

        LabService(JdbcTemplate jdbc, InnerService inner) {
            this.jdbc = jdbc;
            this.inner = inner;
        }

        /** 没有 @Transactional 的外层方法，用 this 调用下面带事务的方法 */
        public void callInternally(String name) {
            insertThenFail(name);
        }

        @Transactional
        public void insertThenFail(String name) {
            insert(name);
            throw new IllegalStateException("模拟业务失败");
        }

        @Transactional
        protected boolean protectedProbe() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Transactional
        boolean packagePrivateProbe() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Transactional
        public final void finalMethod(String name) {
            insert(name);
        }

        @Transactional
        public void insertThenSwallow(String name) {
            try {
                insert(name);
                throw new IllegalStateException("模拟业务失败");
            } catch (IllegalStateException ignored) {
                // 故意吞掉：演示"事务看不到异常就会提交"
            }
        }

        @Transactional
        public void insertThenThrowChecked(String name) throws Exception {
            insert(name);
            throw new Exception("检查型异常");
        }

        @Transactional(rollbackFor = Exception.class)
        public void insertThenThrowCheckedWithRollbackFor(String name) throws Exception {
            insert(name);
            throw new Exception("检查型异常");
        }

        @Transactional
        public void outerCatchesInnerRequired(String name) {
            insert(name + "-outer");
            try {
                inner.requiredThenFail(name + "-inner");
            } catch (IllegalStateException ignored) {
                // 外层以为把异常处理掉了，但事务已经被内层标记成只能回滚
            }
        }

        @Transactional
        public void outerFailsAfterRequiresNew(String name) {
            insert(name + "-outer");
            inner.requiresNew(name + "-inner");
            throw new IllegalStateException("外层失败");
        }

        @Transactional
        public void outerCatchesNested(String name) {
            insert(name + "-outer");
            try {
                inner.nestedThenFail(name + "-inner");
            } catch (IllegalStateException ignored) {
                // 内层回滚到保存点，外层继续
            }
        }

        @Transactional
        public void callNeverInsideTransaction() {
            inner.never();
        }

        int count(String name) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM lab_tx WHERE name = ?", Integer.class, name);
            return count == null ? 0 : count;
        }

        private void insert(String name) {
            jdbc.update("INSERT INTO lab_tx (name) VALUES (?)", name);
        }
    }

    static class InnerService {

        private final JdbcTemplate jdbc;

        InnerService(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void requiredThenFail(String name) {
            jdbc.update("INSERT INTO lab_tx (name) VALUES (?)", name);
            throw new IllegalStateException("内层失败");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void requiresNew(String name) {
            jdbc.update("INSERT INTO lab_tx (name) VALUES (?)", name);
        }

        @Transactional(propagation = Propagation.NESTED)
        public void nestedThenFail(String name) {
            jdbc.update("INSERT INTO lab_tx (name) VALUES (?)", name);
            throw new IllegalStateException("内层失败");
        }

        @Transactional(propagation = Propagation.MANDATORY)
        public void mandatory() {}

        @Transactional(propagation = Propagation.NEVER)
        public void never() {}
    }
}
