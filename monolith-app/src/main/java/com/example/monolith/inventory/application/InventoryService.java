package com.example.monolith.inventory.application;

import com.example.monolith.inventory.domain.InventoryRepository;
import com.example.monolith.inventory.domain.Reservation;
import com.example.monolith.inventory.domain.ReservationStatus;
import com.example.monolith.inventory.domain.Stock;
import com.example.monolith.inventory.domain.StockQuantity;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 库存用例：预占、确认、释放、查询、调整。 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventory;
    private final Clock clock;

    public InventoryService(InventoryRepository inventory, Clock clock) {
        this.inventory = inventory;
        this.clock = clock;
    }

    /**
     * 下单时预占库存。
     *
     * <p>{@code Propagation.MANDATORY}：必须在调用方（下单）已有的事务里执行，没有事务直接报错。
     * 预占和写订单必须同生共死，这个传播行为把"约定"变成了"强制"——少见但很贴切的用法。
     *
     * <p>先按商品 id 排序再逐个扣减：多个下单事务总是按相同的顺序给库存行加锁，
     * 不会出现"A 锁了 1 等 2、B 锁了 2 等 1"的死锁。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(long orderNo, List<StockQuantity> quantities) {
        Map<Long, Integer> merged = new TreeMap<>();
        quantities.forEach(item -> merged.merge(item.skuId(), item.quantity(), Integer::sum));
        Instant now = clock.instant();
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            if (!inventory.tryReserve(entry.getKey(), entry.getValue(), now)) {
                // 抛出后调用方的事务回滚：前面已经预占成功的商品也一起撤销
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "商品 " + entry.getKey() + " 库存不足");
            }
        }
        inventory.insertReservations(
                merged.entrySet().stream()
                        .map(entry ->
                                new Reservation(orderNo, entry.getKey(), entry.getValue(), ReservationStatus.RESERVED))
                        .toList(),
                now);
    }

    /**
     * 订单支付后：预占转已售。由事件触发，可能被重复投递，所以必须幂等——
     * 先用条件更新把预占记录从 RESERVED 改成 CONFIRMED，改成功了才动库存数量，重复的事件改不动任何行，直接返回。
     *
     * @return 这次调用是否真的处理了（false 表示之前已经处理过）
     */
    @Transactional
    public boolean confirm(long orderNo) {
        Instant now = clock.instant();
        if (inventory.transitionReservations(orderNo, ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, now)
                == 0) {
            log.info("订单的预占已经处理过，忽略重复的确认 orderNo={}", orderNo);
            return false;
        }
        for (Reservation reservation : inventory.findReservations(orderNo)) {
            inventory.confirmReserved(reservation.skuId(), reservation.quantity(), now);
        }
        return true;
    }

    /** 订单取消后：预占退回可售。幂等方式同 {@link #confirm(long)}。 */
    @Transactional
    public boolean release(long orderNo) {
        Instant now = clock.instant();
        if (inventory.transitionReservations(orderNo, ReservationStatus.RESERVED, ReservationStatus.RELEASED, now)
                == 0) {
            log.info("订单的预占已经处理过，忽略重复的释放 orderNo={}", orderNo);
            return false;
        }
        for (Reservation reservation : inventory.findReservations(orderNo)) {
            inventory.releaseReserved(reservation.skuId(), reservation.quantity(), now);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Stock getStock(long skuId) {
        return inventory.findStock(skuId).orElseThrow(() -> BusinessException.notFound("商品 " + skuId + " 没有库存记录"));
    }

    /** 调整可售库存（补货、盘点）。已预占、已售的部分不受影响。 */
    @Transactional
    public Stock setAvailable(long skuId, int available) {
        if (!inventory.setAvailable(skuId, available, clock.instant())) {
            throw BusinessException.notFound("商品 " + skuId + " 没有库存记录");
        }
        return getStock(skuId);
    }
}
