package com.example.monolith.user;

import java.time.Instant;

/**
 * 领域事件：用户被启用或禁用了。事件名用过去式——它描述的是已经发生的事实，监听方不能拒绝它。
 *
 * <p>认证模块监听它：用户被禁用后，立即作废他的所有会话，不用等 access token 自然过期。
 *
 * @param userId 用户 id
 * @param status 新状态：active / disabled
 * @param occurredAt 发生时间
 */
public record UserStatusChanged(long userId, String status, Instant occurredAt) {

    public boolean disabled() {
        return "disabled".equals(status);
    }
}
