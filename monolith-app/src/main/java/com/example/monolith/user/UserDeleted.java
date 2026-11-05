package com.example.monolith.user;

import java.time.Instant;

/**
 * 领域事件：用户被删除了（逻辑删除）。认证模块监听它，作废这个用户的所有会话。
 *
 * @param userId 用户 id
 * @param occurredAt 发生时间
 */
public record UserDeleted(long userId, Instant occurredAt) {}
