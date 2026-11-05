package com.example.monolith.order;

import java.time.Instant;

/**
 * 领域事件：订单已取消（超时未支付或用户主动取消）。库存模块监听它，释放预占的库存。
 *
 * @param orderNo 订单号
 * @param userId 下单用户
 * @param reason 取消原因：TIMEOUT / USER
 * @param occurredAt 取消时间
 */
public record OrderCancelled(long orderNo, long userId, String reason, Instant occurredAt) {}
