package com.example.monolith.order.application;

import com.example.monolith.order.OrderPaid;
import com.example.monolith.order.domain.Order;
import com.example.monolith.order.domain.OrderRepository;
import com.example.monolith.order.domain.OrderStatus;
import com.example.monolith.shared.error.BusinessException;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟支付：真实项目里这一步是支付网关的异步回调（要验签、按支付流水号做幂等），这里简化成一个接口调用。
 *
 * <p>和超时取消的竞争：用户在第 14 分 59 秒支付、定时任务在第 15 分钟取消，两者都先锁住这张订单，
 * 谁先拿到锁谁赢，另一个看到的已经是最新状态。
 */
@Service
public class OrderPaymentService {

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderPaymentService(
            OrderRepository orders, ApplicationEventPublisher events, OrderProperties properties, Clock clock) {
        this.orders = orders;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OrderView pay(long orderNo, long userId) {
        Order order = orders.lockByOrderNo(orderNo)
                .filter(candidate -> candidate.isOwnedBy(userId))
                .orElseThrow(() -> BusinessException.notFound("订单 " + orderNo + " 不存在"));
        // 幂等：已经支付过的订单，重复的支付请求直接返回成功（支付回调本来就可能重复到达）
        if (order.status() == OrderStatus.PAID) {
            return OrderView.from(order, properties.paymentTimeout());
        }
        OrderPaid paid = order.pay(clock.instant(), properties.paymentTimeout());
        orders.updateStatus(order, OrderStatus.PENDING_PAYMENT);
        // 事件先记进 event_publication 表（同一个事务），事务提交后库存模块才收到：库存不会在订单回滚时被误确认
        events.publishEvent(paid);
        return OrderView.from(order, properties.paymentTimeout());
    }
}
