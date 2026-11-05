package com.example.monolith.inventory.application;

import com.example.monolith.inventory.domain.StockQuantity;
import com.example.monolith.order.spi.StockLine;
import com.example.monolith.order.spi.StockReservationPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 库存模块对订单 SPI 的实现：把订单的类型翻译成库存自己的类型，再交给 InventoryService。
 * 订单模块只认识 {@link StockReservationPort} 接口，不知道是谁、怎么实现的。
 */
@Component
class OrderStockReservationAdapter implements StockReservationPort {

    private final InventoryService inventoryService;

    OrderStockReservationAdapter(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void reserve(long orderNo, List<StockLine> lines) {
        inventoryService.reserve(
                orderNo,
                lines.stream()
                        .map(line -> new StockQuantity(line.skuId(), line.quantity()))
                        .toList());
    }
}
