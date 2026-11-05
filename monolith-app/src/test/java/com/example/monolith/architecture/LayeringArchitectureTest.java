package com.example.monolith.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模块内部的分层规则（ArchUnit）。模块之间的边界由 {@link ModularityTest}（Spring Modulith）负责，
 * 这里管的是每个模块内部：接口层 → 应用层 → 领域层 ← 基础设施层。
 *
 * <pre>
 *   interfaces ──▶ application ──▶ domain ◀── infrastructure
 *                       ▲                          │
 *                       └──────────────────────────┘ （基础设施实现应用层定义的端口，比如 AccessTokenIssuer）
 * </pre>
 */
class LayeringArchitectureTest {

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.monolith");

    @Test
    void 分层依赖只能朝向领域层() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Interfaces")
                .definedBy("..interfaces..")
                .layer("Application")
                .definedBy("..application..")
                .layer("Domain")
                .definedBy("..domain..")
                .layer("Infrastructure")
                .definedBy("..infrastructure..")
                .whereLayer("Interfaces")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("Interfaces", "Application", "Infrastructure")
                // 依赖倒置的关键一条：谁都不能直接依赖基础设施层，只能依赖它实现的接口
                .whereLayer("Infrastructure")
                .mayNotBeAccessedByAnyLayer()
                .check(CLASSES);
    }

    @Test
    void 领域层不依赖任何框架() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "com.baomidou..",
                        "org.apache.ibatis..",
                        "jakarta.servlet..",
                        "jakarta.persistence..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..")
                .because("领域层只表达业务规则，换框架、换数据库都不应该改到它")
                .check(CLASSES);
    }

    @Test
    void MyBatis只能出现在基础设施层() {
        noClasses()
                .that()
                .resideOutsideOfPackages("..infrastructure..", "..shared.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.baomidou..", "org.apache.ibatis..")
                .because("Wrapper、Mapper 散落到应用层和接口层，SQL 就无处不在，换 ORM、查慢 SQL 都要全局搜")
                .check(CLASSES);
    }

    @Test
    void Controller只能放在接口层() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .resideInAPackage("..interfaces..")
                .check(CLASSES);
    }

    @Test
    void 业务代码不直接读取系统时间() {
        noClasses()
                .should()
                .callMethod(Instant.class, "now")
                .orShould()
                .callMethod(LocalDateTime.class, "now")
                .because("统一从注入的 Clock 取时间，测试里才能拨动时钟，验证超时取消这类逻辑")
                .check(CLASSES);
    }

    @Test
    void 通用编码规范() {
        // 只用构造器注入：依赖一目了然，也方便在单元测试里直接 new
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(CLASSES);
        // 不用 System.out / System.err，日志统一走 SLF4J（带 traceId、可以分级、可以收集）
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(CLASSES);
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CLASSES);
    }
}
