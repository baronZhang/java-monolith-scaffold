package com.example.monolith.shared.security;

import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 读取当前登录用户，给各模块的接口层（Controller）用。
 *
 * <p>为什么不让 Controller 直接拿 JWT：只有认证模块知道"登录态是用 JWT 表示的"，
 * 其他模块只关心"当前用户是谁、有没有某个权限"。以后换成 Session 或网关透传用户头，只改这里和认证模块。
 *
 * <p>应用层和领域层不要调用它：把用户 id 作为参数传进去，这样它们不依赖 Web 环境，单元测试也好写。
 */
public final class CurrentUser {

    /** 超级管理员的权限码：拥有全部权限 */
    public static final String ALL_PERMISSIONS = "*";

    private CurrentUser() {}

    /** 当前登录用户的 id；没有登录时抛出 401（正常情况下请求到不了这里，过滤器链已经拦住了）。 */
    public static long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return Long.parseLong(authentication.getName());
    }

    /** 当前用户是否拥有某个权限码（'*' 视为拥有全部权限），用于数据权限之类的细粒度判断。 */
    public static boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String granted = authority.getAuthority();
            if (ALL_PERMISSIONS.equals(granted) || permission.equals(granted)) {
                return true;
            }
        }
        return false;
    }
}
