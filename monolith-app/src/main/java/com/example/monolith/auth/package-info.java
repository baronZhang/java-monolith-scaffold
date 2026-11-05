/**
 * 认证模块：登录、刷新 token（轮换 + 重放检测）、退出、当前用户信息，以及整个应用的 Spring Security 配置。
 *
 * <p>依赖用户模块的 {@code UserApi} 查账号，并监听用户模块的事件（禁用、删除）作废会话：依赖方向是 auth → user。
 * 其他模块不依赖认证模块：它们只通过共享内核的 {@code CurrentUser} 和 {@code @RequiresPermission} 拿到"当前用户是谁、能做什么"，
 * 不知道背后用的是 JWT。
 */
@ApplicationModule(displayName = "认证")
package com.example.monolith.auth;

import org.springframework.modulith.ApplicationModule;
