package com.example.monolith.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.MonolithApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * 模块边界写成测试：违反了，构建就失败，而不是靠代码评审时有人"看出来"。
 *
 * <p>{@code verify()} 检查三件事：
 * <ol>
 *   <li>模块之间没有循环依赖；</li>
 *   <li>只访问其他模块对外暴露的类型（模块根包、命名接口），不碰别人的内部实现（子包）；</li>
 *   <li>如果模块声明了 allowedDependencies，只依赖声明过的模块。</li>
 * </ol>
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(MonolithApplication.class);

    @Test
    void 模块之间没有循环依赖且只通过对外接口交互() {
        MODULES.verify();
    }

    @Test
    void 识别出五个业务模块和共享内核() {
        assertThat(MODULES.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .containsExactlyInAnyOrder("auth", "user", "product", "inventory", "order", "shared");
    }

    @Test
    void 依赖方向符合设计() {
        // 认证依赖用户（查账号、监听用户事件）；库存依赖订单（实现 SPI、监听订单事件）；订单依赖商品（价格快照）
        assertThat(dependenciesOf("auth")).contains("user");
        assertThat(dependenciesOf("inventory")).contains("order");
        assertThat(dependenciesOf("order")).contains("product").doesNotContain("inventory");
        // 用户、商品是被依赖的一方，不依赖其他业务模块
        assertThat(dependenciesOf("user")).containsOnly("shared");
        assertThat(dependenciesOf("product")).containsOnly("shared");
    }

    /** 顺带生成模块关系图（PlantUML / C4）和每个模块的说明，输出在 target/spring-modulith-docs */
    @Test
    void 生成模块文档() {
        new Documenter(MODULES).writeDocumentation();
    }

    private static List<String> dependenciesOf(String module) {
        ApplicationModule applicationModule = MODULES.getModuleByName(module).orElseThrow();
        return applicationModule
                .getDirectDependencies(MODULES)
                .uniqueModules()
                .map(target -> target.getIdentifier().toString())
                .toList();
    }
}
