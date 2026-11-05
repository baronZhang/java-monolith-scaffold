package com.example.monolith.order.domain;

import java.math.BigDecimal;

/**
 * 订单行：下单时刻的商品快照 + 数量。
 *
 * @param skuId 商品 id
 * @param productName 下单时的商品名称
 * @param unitPrice 下单时的单价
 * @param quantity 数量
 */
public record OrderLine(long skuId, String productName, BigDecimal unitPrice, int quantity) {

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
