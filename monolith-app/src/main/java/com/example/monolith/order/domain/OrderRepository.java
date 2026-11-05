package com.example.monolith.order.domain;

import com.example.monolith.shared.api.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 订单仓储（端口） */
public interface OrderRepository {

    /** 写入订单和订单行。同一用户的幂等键重复时抛出 {@link DuplicateOrderRequestException}。 */
    void insert(Order order);

    Optional<Order> findByOrderNo(long orderNo);

    /**
     * 加锁读取（SELECT ... FOR UPDATE）：支付、取消、超时取消先锁住这一张订单，彼此串行。
     * 订单行几乎没有竞争（一张订单同时只会有一两个请求），悲观锁简单可靠；
     * 库存是热点行，竞争激烈，用的是条件更新——锁的选择看竞争程度。
     */
    Optional<Order> lockByOrderNo(long orderNo);

    Optional<Order> findByIdempotencyKey(long userId, String idempotencyKey);

    /** 保存状态变化。条件里带上 expected 状态，作为加锁之外的第二道保险。 */
    boolean updateStatus(Order order, OrderStatus expected);

    /** 找出下单时间早于 createdBefore 的待支付订单，按下单时间排序，最多 limit 条。 */
    List<Long> findExpiredPendingOrderNos(Instant createdBefore, int limit);

    PageResult<Order> findByUser(long userId, long page, long pageSize);
}
