/**
 * 共享内核（Shared Kernel）：所有业务模块都可以依赖的少量公共代码。
 *
 * <p>只放两类东西：
 * <ul>
 *   <li>和前端约定的契约：统一响应 {@code Result}、分页 {@code PageResult}、错误码 {@code ErrorCode}；</li>
 *   <li>横切的技术能力：全局异常处理、权限注解、缓存、ID 生成、时钟、事件重投。</li>
 * </ul>
 *
 * <p>共享内核要刻意保持小：放进来的东西所有模块都会依赖，改一处影响全部。
 * 业务概念（用户、订单……）一律不进共享内核，否则模块边界就名存实亡了。
 *
 * <p>{@code Type.OPEN}：开放模块，子包里的类型也允许其他模块直接使用（普通模块只暴露根包）。
 */
@ApplicationModule(displayName = "共享内核", type = ApplicationModule.Type.OPEN)
package com.example.monolith.shared;

import org.springframework.modulith.ApplicationModule;
