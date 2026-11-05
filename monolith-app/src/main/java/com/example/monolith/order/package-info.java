/**
 * 订单模块：幂等下单、模拟支付、取消、超时自动取消。
 *
 * <p>对外暴露：
 * <ul>
 *   <li>根包里的领域事件 {@link com.example.monolith.order.OrderPaid}、{@link com.example.monolith.order.OrderCancelled}；</li>
 *   <li>{@code spi} 命名接口：订单需要的"预占库存"能力，由库存模块实现（模块级的依赖倒置）。</li>
 * </ul>
 *
 * <p>依赖方向：order → product（价格快照）；inventory → order（实现 SPI、监听事件）。
 * 为什么不是 order → inventory：库存要监听订单的支付、取消事件，如果订单再直接调用库存，两个模块就互相依赖、形成环了。
 * 详见"单体 1.2 模块划分与依赖规则"。
 */
@ApplicationModule(displayName = "订单")
package com.example.monolith.order;

import org.springframework.modulith.ApplicationModule;
