package com.example.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * 模块化单体的启动类。
 *
 * <p>启动类所在的包 {@code com.example.monolith} 下面，每个直接子包就是一个应用模块（Spring Modulith 的约定）：
 * auth、user、product、inventory、order 五个业务模块，加一个共享内核 shared。
 * 模块之间的依赖规则写在 {@code ModularityTest} 里，违反了测试就失败。
 *
 * <p>{@code sharedModules = "shared"}：共享内核在做单模块测试（@ApplicationModuleTest）时总是一起加载。
 */
@Modulithic(systemName = "模块化单体脚手架", sharedModules = "shared")
@SpringBootApplication
@ConfigurationPropertiesScan
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }
}
