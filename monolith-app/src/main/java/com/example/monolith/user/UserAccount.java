package com.example.monolith.user;

import java.util.List;

/**
 * 认证需要的账号信息。只在用户模块和认证模块之间传递，不会出现在任何接口响应里。
 *
 * @param userId 用户 id
 * @param username 用户名
 * @param passwordHash BCrypt 哈希；为空表示这个用户不能登录（演示数据里只有三个演示账号设置了密码）
 * @param enabled 是否启用
 * @param permissions 权限码，'*' 表示全部权限
 */
public record UserAccount(
        long userId, String username, String passwordHash, boolean enabled, List<String> permissions) {

    public UserAccount {
        permissions = List.copyOf(permissions);
    }

    /** 不在 toString 里输出密码哈希：日志里打印对象时不会把它带出去 */
    @Override
    public String toString() {
        return "UserAccount[userId=" + userId + ", username=" + username + ", enabled=" + enabled + "]";
    }
}
