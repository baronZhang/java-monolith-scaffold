package com.example.monolith.product;

import java.math.BigDecimal;

/**
 * 下单时刻的商品快照。
 *
 * @param skuId 商品（SKU）id
 * @param name 商品名称
 * @param price 单价：金额一律用 BigDecimal，double 做加减乘除会有精度误差（0.1 + 0.2 != 0.3）
 */
public record ProductSnapshot(long skuId, String name, BigDecimal price) {}
