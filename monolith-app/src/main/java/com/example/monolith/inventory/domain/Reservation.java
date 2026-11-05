package com.example.monolith.inventory.domain;

/**
 * 预占记录：哪张订单预占了哪个商品多少件。唯一索引 (order_no, sku_id)。
 *
 * <p>它让库存的确认和释放不依赖订单模块传数据：事件里只有订单号，库存按订单号查自己的预占记录就知道要还多少。
 *
 * @param orderNo 订单号
 * @param skuId 商品 id
 * @param quantity 数量
 * @param status 状态
 */
public record Reservation(long orderNo, long skuId, int quantity, ReservationStatus status) {}
