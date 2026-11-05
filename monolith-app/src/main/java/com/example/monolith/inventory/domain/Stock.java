package com.example.monolith.inventory.domain;

/**
 * 一个商品的库存。三列之和就是总库存：available + reserved + sold。
 *
 * <ul>
 *   <li>available：还能卖的；</li>
 *   <li>reserved：已下单、待支付，暂时扣住的；支付后转入 sold，取消后退回 available；</li>
 *   <li>sold：已支付的。</li>
 * </ul>
 * 为什么要"预占"而不是下单直接扣减：订单可能不付钱。直接扣减的话，取消时要加回去，
 * 而且分不清"这部分库存是被未支付订单占着"还是"真的卖掉了"，对账和统计都不方便。
 */
public record Stock(long skuId, int available, int reserved, int sold) {}
