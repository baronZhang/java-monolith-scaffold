package com.example.monolith.inventory.domain;

/**
 * 库存模块自己的"商品 + 数量"类型。核心逻辑不直接用订单模块的 StockLine：
 * 订单模块改了它的类型，影响只停在适配器那一层。
 *
 * @param skuId 商品 id
 * @param quantity 数量
 */
public record StockQuantity(long skuId, int quantity) {}
