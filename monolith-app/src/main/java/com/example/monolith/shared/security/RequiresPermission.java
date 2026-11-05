package com.example.monolith.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 接口级权限：标在 Controller 方法上，没有对应权限码的请求直接返回 403。
 *
 * <p>实现是 Spring Security 的"元注解 + 模板表达式"：{@code {value}} 会被替换成注解上写的权限码，
 * 最终等价于 {@code @PreAuthorize("hasAnyAuthority('*', 'user:list')")}。
 * 好处是业务代码里只写权限码，不用到处写 SpEL 字符串；{@code '*'} 表示超级管理员拥有全部权限。
 *
 * <p>前端的菜单隐藏、按钮隐藏只是体验；这里才是真正的安全边界——绕过前端直接调接口，也过不了这一关。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyAuthority('*', '{value}')")
public @interface RequiresPermission {

    /** 权限码，例如 user:list、user:delete */
    String value();
}
