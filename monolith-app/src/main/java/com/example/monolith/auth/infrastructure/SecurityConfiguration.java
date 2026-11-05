package com.example.monolith.auth.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 整个应用的 Spring Security 配置。
 *
 * <p>两道关：
 * <ol>
 *   <li>过滤器链（这里）：只管"有没有登录"——除了登录、刷新、健康检查，其他接口都要带有效的 access token；</li>
 *   <li>方法级权限（{@code @RequiresPermission}）：管"能不能做这件事"，标在各模块的 Controller 方法上。</li>
 * </ol>
 * 前端的菜单、按钮隐藏只是体验；接口级的这两道关才是真正的安全边界。
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, JwtDecoder jwtDecoder, JsonSecurityErrorHandler errorHandler) throws Exception {
        http
                // 无状态：token 放在 Authorization 头里，浏览器不会自动携带，CSRF 攻击无从下手，所以关掉 CSRF 防护
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh")
                                .permitAll()
                                // 只在 demo profile 下存在；其他环境没有处理器，返回 404
                                .requestMatchers(HttpMethod.GET, "/api/debug/stats")
                                .permitAll()
                                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                                .permitAll()
                                // 出错时 Spring Boot 会转发到 /error：不放行的话，真实错误会被掩盖成 401
                                .requestMatchers("/error")
                                .permitAll()
                                // 其他监控端点（指标、模块结构……）只有管理员能看；生产上更推荐放到单独的管理端口
                                .requestMatchers("/actuator/**")
                                .hasAuthority("*")
                                .anyRequest()
                                .authenticated())
                // 用 Spring Security 自带的 Resource Server 校验 JWT，不手写过滤器
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler));
        return http.build();
    }

    /** JWT 里的 perms 声明 → Spring Security 的权限（GrantedAuthority），不加默认的 SCOPE_ 前缀。 */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("perms");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** 让 @RequiresPermission 这种元注解里的 {value} 占位符生效。 */
    @Bean
    public static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    /**
     * 密码编码器：存进数据库的哈希带算法前缀，形如 {bcrypt}$2a$10$...。
     * 以后要换成 Argon2，新密码用新算法、老密码照样能验证，用户登录时再逐步升级——这叫"算法敏捷性"。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
