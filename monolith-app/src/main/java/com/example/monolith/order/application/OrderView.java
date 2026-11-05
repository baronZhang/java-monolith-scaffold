package com.example.monolith.order.application;

import com.example.monolith.order.domain.Order;
import com.example.monolith.order.domain.OrderLine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 订单的读模型。
 *
 * @param orderNo 订单号用字符串：雪花 ID 超过了 JavaScript Number 的安全整数范围（2^53），用数字前端会丢精度
 * @param paymentDeadline 支付截止时间，前端可以据此显示倒计时
 */
public record OrderView(
        String orderNo,
        String status,
        BigDecimal totalAmount,
        List<Item> items,
        String cancelReason,
        Instant createdAt,
        Instant paymentDeadline,
        Instant paidAt,
        Instant cancelledAt) {

    public static OrderView from(Order order, Duration paymentTimeout) {
        return new OrderView(
                String.valueOf(order.orderNo()),
                order.status().name(),
                order.totalAmount(),
                order.lines().stream().map(Item::from).toList(),
                order.cancelReason() == null ? null : order.cancelReason().name(),
                order.createdAt(),
                order.paymentDeadline(paymentTimeout),
                order.paidAt(),
                order.cancelledAt());
    }

    /** 订单里的一个商品 */
    public record Item(long skuId, String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {

        static Item from(OrderLine line) {
            return new Item(line.skuId(), line.productName(), line.unitPrice(), line.quantity(), line.subtotal());
        }
    }
}
