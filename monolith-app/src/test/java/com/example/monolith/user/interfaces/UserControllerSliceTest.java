package com.example.monolith.user.interfaces;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.monolith.user.application.UserCommandService;
import com.example.monolith.user.application.UserQueryService;
import com.example.monolith.user.application.UserView;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web 层切片测试（@WebMvcTest）：只启动 MVC 相关的 Bean（Controller、全局异常处理、参数校验、安全），
 * 应用层用 Mock 代替。测的是"HTTP 契约"：路径、权限注解、参数校验、统一响应结构。
 *
 * <p>登录态用 spring-security-test 的 jwt() 直接塞进安全上下文，不用真的签发 token。
 */
@WebMvcTest(UserController.class)
@Import(UserControllerSliceTest.MethodSecurityConfig.class)
class UserControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService queries;

    @MockitoBean
    UserCommandService commands;

    @Test
    void 有权限时返回统一结构() throws Exception {
        given(queries.get(4))
                .willReturn(new UserView(
                        4,
                        "user004",
                        "陈勇",
                        "user4@example.com",
                        "guest",
                        "active",
                        Instant.parse("2026-01-07T01:00:00Z")));

        mockMvc.perform(get("/api/users/4")
                        .with(jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("user:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.nickname").value("陈勇"))
                // 时间输出成 ISO-8601 字符串（Jackson 3 默认不再输出成时间戳数字）
                .andExpect(jsonPath("$.data.createdAt").value("2026-01-07T01:00:00Z"));
    }

    @Test
    void 缺少权限码时返回403_应用层根本不会被调用() throws Exception {
        mockMvc.perform(delete("/api/users/4")
                        .with(csrf())
                        .with(jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("user:list"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(commands);
    }

    @Test
    void 星号权限可以做任何事() throws Exception {
        mockMvc.perform(delete("/api/users/4")
                        .with(csrf())
                        .with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("*"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void 请求体校验失败返回400和提示() throws Exception {
        mockMvc.perform(put("/api/users/4/status")
                        .with(csrf())
                        .with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("*")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("status 不能为空"));
    }

    /** 切片测试不会加载认证模块的安全配置，这里只把方法级权限和 {value} 占位符打开 */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }
    }
}
