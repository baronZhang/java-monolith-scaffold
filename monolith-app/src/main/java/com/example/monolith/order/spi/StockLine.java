package com.example.monolith.order.spi;

/**
 * 预占一个商品的数量。
 *
 * @param skuId 商品 id
 * @param quantity 数量，大于 0
 */
public record StockLine(long skuId, int quantity) {}
