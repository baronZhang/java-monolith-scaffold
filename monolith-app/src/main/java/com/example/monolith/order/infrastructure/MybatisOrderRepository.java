package com.example.monolith.order.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.monolith.order.domain.CancelReason;
import com.example.monolith.order.domain.DuplicateOrderRequestException;
import com.example.monolith.order.domain.Order;
import com.example.monolith.order.domain.OrderLine;
import com.example.monolith.order.domain.OrderRepository;
import com.example.monolith.order.domain.OrderStatus;
import com.example.monolith.shared.api.PageResult;
import com.example.monolith.shared.persistence.DbTime;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 订单仓储的 MyBatis-Plus 实现：订单头和订单行一起读写，对外表现为一个聚合。 */
@Repository
class MybatisOrderRepository implements OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderLineMapper lineMapper;

    MybatisOrderRepository(OrderMapper orderMapper, OrderLineMapper lineMapper) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
    }

    @Override
    public void insert(Order order) {
        try {
            orderMapper.insert(toPo(order));
        } catch (DuplicateKeyException ex) {
            // MyBatis-Spring 已经把 MySQL 的 1062 / H2 的 23505 翻译成了 Spring 的 DuplicateKeyException，
            // 这里再翻译成领域异常，应用层不用认识持久化框架
            throw new DuplicateOrderRequestException(order.idempotencyKey(), ex);
        }
        for (OrderLine line : order.lines()) {
            OrderLinePO po = new OrderLinePO();
            po.setOrderNo(order.orderNo());
            po.setSkuId(line.skuId());
            po.setProductName(line.productName());
            po.setUnitPrice(line.unitPrice());
            po.setQuantity(line.quantity());
            lineMapper.insert(po);
        }
    }

    @Override
    public Optional<Order> findByOrderNo(long orderNo) {
        return Optional.ofNullable(orderMapper.selectById(orderNo)).map(this::withLines);
    }

    @Override
    public Optional<Order> lockByOrderNo(long orderNo) {
        return Optional.ofNullable(orderMapper.lockByOrderNo(orderNo)).map(this::withLines);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(long userId, String idempotencyKey) {
        return Optional.ofNullable(orderMapper.selectOne(Wrappers.lambdaQuery(OrderPO.class)
                        .eq(OrderPO::getUserId, userId)
                        .eq(OrderPO::getIdempotencyKey, idempotencyKey)))
                .map(this::withLines);
    }

    @Override
    public boolean updateStatus(Order order, OrderStatus expected) {
        // 状态只会因支付或取消而变化，所以更新时间就是支付时间或取消时间
        return orderMapper.updateStatus(
                        order.orderNo(),
                        expected.name(),
                        order.status().name(),
                        DbTime.toDb(order.paidAt()),
                        DbTime.toDb(order.cancelledAt()),
                        order.cancelReason() == null
                                ? null
                                : order.cancelReason().name(),
                        DbTime.toDb(order.paidAt() != null ? order.paidAt() : order.cancelledAt()))
                == 1;
    }

    @Override
    public List<Long> findExpiredPendingOrderNos(Instant createdBefore, int limit) {
        return orderMapper.findExpiredPending(DbTime.toDb(createdBefore), limit);
    }

    @Override
    public PageResult<Order> findByUser(long userId, long page, long pageSize) {
        Page<OrderPO> result = orderMapper.selectPage(
                Page.of(page, pageSize),
                Wrappers.lambdaQuery(OrderPO.class)
                        .eq(OrderPO::getUserId, userId)
                        .orderByDesc(OrderPO::getCreatedAt));
        Map<Long, List<OrderLine>> lines =
                loadLines(result.getRecords().stream().map(OrderPO::getOrderNo).toList());
        List<Order> orders = result.getRecords().stream()
                .map(po -> toDomain(po, lines.getOrDefault(po.getOrderNo(), List.of())))
                .toList();
        return new PageResult<>(orders, result.getTotal(), page, pageSize);
    }

    private Order withLines(OrderPO po) {
        return toDomain(po, loadLines(List.of(po.getOrderNo())).getOrDefault(po.getOrderNo(), List.of()));
    }

    /** 一次查出多张订单的订单行，避免"每张订单查一次"的 N+1 问题 */
    private Map<Long, List<OrderLine>> loadLines(Collection<Long> orderNos) {
        if (orderNos.isEmpty()) {
            return Map.of();
        }
        return lineMapper
                .selectList(Wrappers.lambdaQuery(OrderLinePO.class)
                        .in(OrderLinePO::getOrderNo, orderNos)
                        .orderByAsc(OrderLinePO::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        OrderLinePO::getOrderNo,
                        Collectors.mapping(
                                po -> new OrderLine(
                                        po.getSkuId(), po.getProductName(), po.getUnitPrice(), po.getQuantity()),
                                Collectors.toList())));
    }

    private static OrderPO toPo(Order order) {
        OrderPO po = new OrderPO();
        po.setOrderNo(order.orderNo());
        po.setUserId(order.userId());
        po.setStatus(order.status().name());
        po.setTotalAmount(order.totalAmount());
        po.setIdempotencyKey(order.idempotencyKey());
        po.setRequestHash(order.requestHash());
        po.setCreatedAt(DbTime.toDb(order.createdAt()));
        po.setUpdatedAt(DbTime.toDb(order.createdAt()));
        return po;
    }

    private static Order toDomain(OrderPO po, List<OrderLine> lines) {
        return new Order(
                po.getOrderNo(),
                po.getUserId(),
                OrderStatus.valueOf(po.getStatus()),
                lines,
                po.getIdempotencyKey(),
                po.getRequestHash(),
                po.getCancelReason() == null ? null : CancelReason.valueOf(po.getCancelReason()),
                DbTime.fromDb(po.getCreatedAt()),
                DbTime.fromDb(po.getPaidAt()),
                DbTime.fromDb(po.getCancelledAt()));
    }
}
