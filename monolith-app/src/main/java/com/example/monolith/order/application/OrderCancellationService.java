package com.example.monolith.order.application;

import com.example.monolith.order.OrderCancelled;
import com.example.monolith.order.domain.CancelReason;
import com.example.monolith.order.domain.Order;
import com.example.monolith.order.domain.OrderRepository;
import com.example.monolith.order.domain.OrderStatus;
import com.example.monolith.shared.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 取消订单：用户主动取消，或者定时任务取消超时未支付的订单。取消后发布 OrderCancelled，库存模块释放预占。
 */
@Service
public class OrderCancellationService {

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;
    private final OrderProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public OrderCancellationService(
            OrderRepository orders,
            ApplicationEventPublisher events,
            OrderProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public OrderView cancelByUser(long orderNo, long userId) {
        Order order = orders.lockByOrderNo(orderNo)
                .filter(candidate -> candidate.isOwnedBy(userId))
                .orElseThrow(() -> BusinessException.notFound("订单 " + orderNo + " 不存在"));
        // 幂等：重复的取消请求直接返回当前结果
        if (order.status() == OrderStatus.CANCELLED) {
            return OrderView.from(order, properties.paymentTimeout());
        }
        cancel(order, CancelReason.USER, clock.instant());
        return OrderView.from(order, properties.paymentTimeout());
    }

    /**
     * 取消一批超时未支付的订单，返回实际取消了几张。
     *
     * <p>每张订单一个独立的小事务：一张出错不影响其他订单；锁的持有时间也短。
     * 这里如果写成"循环里调用本类另一个标了 @Transactional 的方法"，事务是不会生效的——
     * 自调用不经过代理，这是 @Transactional 失效最常见的场景，见"单体 3.1"。所以用 TransactionTemplate。
     *
     * <p>多实例同时扫描也不会重复取消：每张订单都先加锁再判断状态，第二个实例看到的已经是"已取消"。
     */
    public int cancelExpiredOrders() {
        Instant now = clock.instant();
        Instant createdBefore = now.minus(properties.paymentTimeout());
        List<Long> candidates = orders.findExpiredPendingOrderNos(createdBefore, properties.timeoutScanBatchSize());
        int cancelled = 0;
        for (Long orderNo : candidates) {
            Boolean done = transactionTemplate.execute(status -> cancelIfStillExpired(orderNo, now));
            if (Boolean.TRUE.equals(done)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private boolean cancelIfStillExpired(long orderNo, Instant now) {
        Order order = orders.lockByOrderNo(orderNo).orElse(null);
        // 加锁之后再判断一次：查出候选列表到现在的这段时间里，它可能刚刚被支付了
        if (order == null || !order.isPaymentExpired(now, properties.paymentTimeout())) {
            return false;
        }
        cancel(order, CancelReason.TIMEOUT, now);
        return true;
    }

    private void cancel(Order order, CancelReason reason, Instant now) {
        OrderCancelled cancelled = order.cancel(reason, now);
        orders.updateStatus(order, OrderStatus.PENDING_PAYMENT);
        events.publishEvent(cancelled);
    }
}
