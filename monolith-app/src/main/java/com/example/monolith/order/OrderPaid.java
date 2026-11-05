package com.example.monolith.order;

import java.time.Instant;

/**
 * 领域事件：订单已支付。库存模块监听它，把预占的库存转成已售。
 *
 * <p>事件只带 id，不带订单明细："扣了哪些库存"库存模块自己有记录（预占记录），不需要订单告诉它。
 * 事件越瘦，发布方和监听方之间的耦合越少，事件结构也越不容易因为某一方的变化而改动。
 *
 * @param orderNo 订单号
 * @param userId 下单用户
 * @param occurredAt 支付时间
 */
public record OrderPaid(long orderNo, long userId, Instant occurredAt) {}
