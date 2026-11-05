/**
 * 用户模块：用户资料、启用 / 禁用、删除，以及角色到权限码的映射（RBAC）。
 *
 * <p>对外暴露的只有根包里的类型：
 * <ul>
 *   <li>{@link com.example.monolith.user.UserApi}：给认证模块查账号和权限；</li>
 *   <li>{@link com.example.monolith.user.UserStatusChanged}、{@link com.example.monolith.user.UserDeleted}：领域事件，
 *       认证模块监听它们，把被禁用、被删除用户的会话作废。</li>
 * </ul>
 * 子包（interfaces / application / domain / infrastructure）都是模块内部实现，其他模块引用会被 ModularityTest 拦下。
 */
@ApplicationModule(displayName = "用户")
package com.example.monolith.user;

import org.springframework.modulith.ApplicationModule;
