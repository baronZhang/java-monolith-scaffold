package com.example.monolith.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.monolith.order.OrderCancelled;
import com.example.monolith.order.OrderPaid;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 订单状态机的单元测试：纯 Java，不启动 Spring、不连数据库，毫秒级跑完。 */
class OrderTest {

    static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    static final Duration TIMEOUT = Duration.ofMinutes(15);

    @Test
    void 下单时按订单行计算总金额() {
        Order order = newOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        // 399.00 × 2 + 9.90 × 3 = 827.70，BigDecimal 计算没有精度误差
        assertThat(order.totalAmount()).isEqualByComparingTo("827.70");
        assertThat(order.paymentDeadline(TIMEOUT)).isEqualTo(NOW.plus(TIMEOUT));
    }

    @Test
    void 支付成功后状态变为已支付并产生事件() {
        Order order = newOrder();

        OrderPaid event = order.pay(NOW.plusSeconds(60), TIMEOUT);

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(event.orderNo()).isEqualTo(order.orderNo());
        assertThat(event.occurredAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void 超过支付时限的订单不能再支付() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.pay(NOW.plus(TIMEOUT), TIMEOUT))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单已超时，请重新下单")
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ORDER_STATE_INVALID);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void 已取消的订单不能支付_已支付的订单不能取消() {
        Order cancelled = newOrder();
        cancelled.cancel(CancelReason.USER, NOW);
        assertThatThrownBy(() -> cancelled.pay(NOW, TIMEOUT)).hasMessage("订单已取消，不能支付");

        Order paid = newOrder();
        paid.pay(NOW, TIMEOUT);
        assertThatThrownBy(() -> paid.cancel(CancelReason.TIMEOUT, NOW)).hasMessage("订单已支付，不能取消");
    }

    @Test
    void 取消后记录原因并产生事件() {
        Order order = newOrder();

        OrderCancelled event = order.cancel(CancelReason.TIMEOUT, NOW.plus(TIMEOUT));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancelReason()).isEqualTo(CancelReason.TIMEOUT);
        assertThat(event.reason()).isEqualTo("TIMEOUT");
    }

    @Test
    void 超时判断的边界_截止时间那一刻就算超时() {
        Order order = newOrder();

        assertThat(order.isPaymentExpired(NOW.plus(TIMEOUT).minusMillis(1), TIMEOUT))
                .isFalse();
        assertThat(order.isPaymentExpired(NOW.plus(TIMEOUT), TIMEOUT)).isTrue();
    }

    @Test
    void 没有商品不能下单() {
        assertThatThrownBy(() -> Order.place(1L, 1L, List.of(), "key-12345678", "hash", NOW))
                .hasMessage("订单至少要有一个商品");
    }

    private static Order newOrder() {
        return Order.place(
                1001L,
                1L,
                List.of(
                        new OrderLine(1, "机械键盘 K8", new BigDecimal("399.00"), 2),
                        new OrderLine(6, "限量帆布包（秒杀）", new BigDecimal("9.90"), 3)),
                "key-12345678",
                "hash",
                NOW);
    }
}
