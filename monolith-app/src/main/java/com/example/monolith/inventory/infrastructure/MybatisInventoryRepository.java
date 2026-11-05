package com.example.monolith.inventory.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.monolith.inventory.domain.InventoryRepository;
import com.example.monolith.inventory.domain.Reservation;
import com.example.monolith.inventory.domain.ReservationStatus;
import com.example.monolith.inventory.domain.Stock;
import com.example.monolith.shared.persistence.DbTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 库存仓储的 MyBatis-Plus 实现 */
@Repository
class MybatisInventoryRepository implements InventoryRepository {

    private final StockMapper stockMapper;
    private final ReservationMapper reservationMapper;

    MybatisInventoryRepository(StockMapper stockMapper, ReservationMapper reservationMapper) {
        this.stockMapper = stockMapper;
        this.reservationMapper = reservationMapper;
    }

    @Override
    public Optional<Stock> findStock(long skuId) {
        return Optional.ofNullable(stockMapper.selectById(skuId))
                .map(po -> new Stock(po.getSkuId(), po.getAvailable(), po.getReserved(), po.getSold()));
    }

    @Override
    public boolean tryReserve(long skuId, int quantity, Instant now) {
        return stockMapper.tryReserve(skuId, quantity, DbTime.toDb(now)) == 1;
    }

    @Override
    public void confirmReserved(long skuId, int quantity, Instant now) {
        stockMapper.confirmReserved(skuId, quantity, DbTime.toDb(now));
    }

    @Override
    public void releaseReserved(long skuId, int quantity, Instant now) {
        stockMapper.releaseReserved(skuId, quantity, DbTime.toDb(now));
    }

    @Override
    public boolean setAvailable(long skuId, int available, Instant now) {
        return stockMapper.setAvailable(skuId, available, DbTime.toDb(now)) == 1;
    }

    @Override
    public void insertReservations(List<Reservation> reservations, Instant now) {
        LocalDateTime time = DbTime.toDb(now);
        for (Reservation reservation : reservations) {
            ReservationPO po = new ReservationPO();
            po.setOrderNo(reservation.orderNo());
            po.setSkuId(reservation.skuId());
            po.setQuantity(reservation.quantity());
            po.setStatus(reservation.status().name());
            po.setCreatedAt(time);
            po.setUpdatedAt(time);
            reservationMapper.insert(po);
        }
    }

    @Override
    public List<Reservation> findReservations(long orderNo) {
        return reservationMapper
                .selectList(Wrappers.lambdaQuery(ReservationPO.class)
                        .eq(ReservationPO::getOrderNo, orderNo)
                        .orderByAsc(ReservationPO::getSkuId))
                .stream()
                .map(po -> new Reservation(
                        po.getOrderNo(), po.getSkuId(), po.getQuantity(), ReservationStatus.valueOf(po.getStatus())))
                .toList();
    }

    @Override
    public int transitionReservations(long orderNo, ReservationStatus from, ReservationStatus to, Instant now) {
        return reservationMapper.transition(orderNo, from.name(), to.name(), DbTime.toDb(now));
    }
}
