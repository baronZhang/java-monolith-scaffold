package com.example.monolith.order.application;

import com.example.monolith.order.domain.DuplicateOrderRequestException;
import com.example.monolith.order.domain.Order;
import com.example.monolith.order.domain.OrderLine;
import com.example.monolith.order.domain.OrderRepository;
import com.example.monolith.order.spi.StockLine;
import com.example.monolith.order.spi.StockReservationPort;
import com.example.monolith.product.ProductApi;
import com.example.monolith.product.ProductSnapshot;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 下单用例：幂等 + 一个本地事务里完成"写订单 + 预占库存"。
 *
 * <p>幂等分两层：
 * <ol>
 *   <li>快速路径：先按 (用户, 幂等键) 查一次，已经有订单就直接返回它（重试、重复点击走这里）；</li>
 *   <li>兜底：两个相同的请求同时到达，都没查到，都去插入——唯一索引让后到的那个失败、事务回滚，
 *       然后读出先到的那张订单返回。数据库约束是最终的正确性保证，查一次只是优化。</li>
 * </ol>
 *
 * <p>用编程式事务（TransactionTemplate）而不是 @Transactional：唯一索引冲突要在<b>事务外</b>捕获——
 * 事务里出了这个异常，事务已经被标记为只能回滚，在事务里接着查已有订单是不行的。
 * 编程式事务把"事务从哪里开始、到哪里结束"写得清清楚楚，也避开了同一个类里方法自调用让 @Transactional 失效的坑。
 */
@Service
public class PlaceOrderService {

    private final OrderRepository orders;
    private final ProductApi productApi;
    private final StockReservationPort stockReservation;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final OrderProperties properties;
    private final Clock clock;

    public PlaceOrderService(
            OrderRepository orders,
            ProductApi productApi,
            StockReservationPort stockReservation,
            SnowflakeIdGenerator idGenerator,
            PlatformTransactionManager transactionManager,
            OrderProperties properties,
            Clock clock) {
        this.orders = orders;
        this.productApi = productApi;
        this.stockReservation = stockReservation;
        this.idGenerator = idGenerator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
    }

    public PlaceOrderResult place(PlaceOrderCommand command) {
        String requestHash = command.requestHash();
        Optional<Order> existing = orders.findByIdempotencyKey(command.userId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }
        try {
            Order order = transactionTemplate.execute(status -> placeInTransaction(command, requestHash));
            return new PlaceOrderResult(OrderView.from(order, properties.paymentTimeout()), false);
        } catch (DuplicateOrderRequestException ex) {
            Order winner = orders.findByIdempotencyKey(command.userId(), command.idempotencyKey())
                    .orElseThrow(() -> ex);
            return replay(winner, requestHash);
        }
    }

    private Order placeInTransaction(PlaceOrderCommand command, String requestHash) {
        Map<Long, Integer> quantities = command.mergedQuantities();
        Map<Long, ProductSnapshot> products = productApi.findSaleable(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductSnapshot::skuId, Function.identity()));
        List<OrderLine> lines = quantities.entrySet().stream()
                .map(entry -> {
                    ProductSnapshot product = products.get(entry.getKey());
                    if (product == null) {
                        throw BusinessException.rule("商品 " + entry.getKey() + " 不存在或已下架");
                    }
                    return new OrderLine(product.skuId(), product.name(), product.price(), entry.getValue());
                })
                .toList();

        Order order = Order.place(
                idGenerator.nextId(), command.userId(), lines, command.idempotencyKey(), requestHash, clock.instant());
        // ① 先写订单：幂等键冲突在这一步暴露，还没碰库存
        orders.insert(order);
        // ② 再预占库存：条件更新，任何一个商品不足就抛异常，整个事务（包括上面写的订单）一起回滚
        stockReservation.reserve(
                order.orderNo(),
                lines.stream()
                        .map(line -> new StockLine(line.skuId(), line.quantity()))
                        .toList());
        return order;
    }

    private PlaceOrderResult replay(Order order, String requestHash) {
        if (!order.requestHash().equals(requestHash)) {
            throw BusinessException.rule("Idempotency-Key 已经用于另一个内容不同的下单请求");
        }
        return new PlaceOrderResult(OrderView.from(order, properties.paymentTimeout()), true);
    }

    /**
     * 下单结果。
     *
     * @param replayed true 表示这是对同一个请求的重放，返回的是之前创建的订单
     */
    public record PlaceOrderResult(OrderView order, boolean replayed) {}
}
