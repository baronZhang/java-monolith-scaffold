package com.example.monolith.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 库存仓储（端口） */
public interface InventoryRepository {

    Optional<Stock> findStock(long skuId);

    /**
     * 预占：一条条件更新完成"判断够不够 + 扣减"——
     * {@code UPDATE ... SET available = available - ? WHERE sku_id = ? AND available >= ?}。
     * 判断和扣减在同一条 SQL 里，由数据库的行锁保证原子性，不存在"读出来够、写回去时已经不够"的窗口。
     *
     * @return 是否预占成功（影响行数为 1）
     */
    boolean tryReserve(long skuId, int quantity, Instant now);

    /** 预占转已售（支付成功） */
    void confirmReserved(long skuId, int quantity, Instant now);

    /** 预占退回可售（订单取消） */
    void releaseReserved(long skuId, int quantity, Instant now);

    /** 调整可售库存（补货、盘点） */
    boolean setAvailable(long skuId, int available, Instant now);

    void insertReservations(List<Reservation> reservations, Instant now);

    List<Reservation> findReservations(long orderNo);

    /** 把一张订单的预占记录从 from 状态改成 to 状态，返回改了几行；0 行说明已经处理过了。 */
    int transitionReservations(long orderNo, ReservationStatus from, ReservationStatus to, Instant now);
}
