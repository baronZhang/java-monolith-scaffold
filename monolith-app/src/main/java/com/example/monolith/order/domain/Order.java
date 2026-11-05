package com.example.monolith.order.domain;

import com.example.monolith.order.OrderCancelled;
import com.example.monolith.order.OrderPaid;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 订单（聚合根）：状态机写在这里，谁也绕不过去。
 *
 * <pre>
 * PENDING_PAYMENT ──支付──▶ PAID
 *        │
 *        └──超时 / 用户取消──▶ CANCELLED
 * </pre>
 *
 * <p>状态变更的方法返回对应的领域事件：由领域对象决定"发生了什么"，由应用层负责把事件发布出去。
 * 领域层只认识事件这个普通的 record，不认识 Spring 的事件机制。
 */
public class Order {

    private final long orderNo;
    private final long userId;
    private OrderStatus status;
    private final BigDecimal totalAmount;
    private final List<OrderLine> lines;
    private final String idempotencyKey;
    private final String requestHash;
    private CancelReason cancelReason;
    private final Instant createdAt;
    private Instant paidAt;
    private Instant cancelledAt;

    public Order(
            long orderNo,
            long userId,
            OrderStatus status,
            List<OrderLine> lines,
            String idempotencyKey,
            String requestHash,
            CancelReason cancelReason,
            Instant createdAt,
            Instant paidAt,
            Instant cancelledAt) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.status = status;
        this.lines = List.copyOf(lines);
        this.totalAmount = this.lines.stream().map(OrderLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
        this.cancelledAt = cancelledAt;
    }

    /** 创建一张待支付的订单 */
    public static Order place(
            long orderNo, long userId, List<OrderLine> lines, String idempotencyKey, String requestHash, Instant now) {
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单至少要有一个商品");
        }
        return new Order(
                orderNo,
                userId,
                OrderStatus.PENDING_PAYMENT,
                lines,
                idempotencyKey,
                requestHash,
                null,
                now,
                null,
                null);
    }

    /** 支付截止时间 = 下单时间 + 支付时限 */
    public Instant paymentDeadline(Duration paymentTimeout) {
        return createdAt.plus(paymentTimeout);
    }

    /** 是否已经超过支付时限（只对待支付订单有意义） */
    public boolean isPaymentExpired(Instant now, Duration paymentTimeout) {
        return status == OrderStatus.PENDING_PAYMENT && !now.isBefore(paymentDeadline(paymentTimeout));
    }

    /**
     * 支付。超时的订单不能再支付：定时扫描有延迟，不能指望"超时的订单一定已经被取消了"，这里必须再判断一次。
     */
    public OrderPaid pay(Instant now, Duration paymentTimeout) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "支付");
        if (isPaymentExpired(now, paymentTimeout)) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已超时，请重新下单");
        }
        status = OrderStatus.PAID;
        paidAt = now;
        return new OrderPaid(orderNo, userId, now);
    }

    /** 取消：只有待支付的订单可以取消；已支付的要走退款流程（演示里没有）。 */
    public OrderCancelled cancel(CancelReason reason, Instant now) {
        requireStatus(OrderStatus.PENDING_PAYMENT, "取消");
        status = OrderStatus.CANCELLED;
        cancelReason = reason;
        cancelledAt = now;
        return new OrderCancelled(orderNo, userId, reason.name(), now);
    }

    public boolean isOwnedBy(long candidateUserId) {
        return userId == candidateUserId;
    }

    private void requireStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单" + status.label() + "，不能" + action);
        }
    }

    public long orderNo() {
        return orderNo;
    }

    public long userId() {
        return userId;
    }

    public OrderStatus status() {
        return status;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    public CancelReason cancelReason() {
        return cancelReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant paidAt() {
        return paidAt;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }
}
