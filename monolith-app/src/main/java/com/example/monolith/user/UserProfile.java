package com.example.monolith.user;

import java.util.List;

/**
 * 当前登录用户的资料和权限，字段和前端 {@code src/types/auth.ts} 里的 UserProfile 一致。
 *
 * @param id 用户 id
 * @param username 用户名
 * @param nickname 昵称
 * @param roles 角色，例如 admin / editor / guest
 * @param permissions 权限码，'*' 表示拥有全部权限
 */
public record UserProfile(long id, String username, String nickname, List<String> roles, List<String> permissions) {

    public UserProfile {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
