package com.example.monolith.support;

/**
 * 种子数据里的三个演示账号（见 V2__seed_data.sql），测试登录用。
 * 密码与 Vue 项目 mock/data.ts 的 DEMO_PASSWORD 相同，数据库里只存了它的 BCrypt 哈希。
 */
public final class DemoAccounts {

    public static final String PASSWORD = "123456";

    public static final String ADMIN = "admin";
    public static final long ADMIN_ID = 1;
    public static final String EDITOR = "editor";
    public static final long EDITOR_ID = 2;
    public static final String GUEST = "guest";
    public static final long GUEST_ID = 3;

    private DemoAccounts() {}
}
