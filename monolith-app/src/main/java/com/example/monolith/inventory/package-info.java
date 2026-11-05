/**
 * 库存模块：库存三列（可售 available / 预占 reserved / 已售 sold）、预占记录、库存调整。
 *
 * <p>和订单模块的关系：实现订单模块的 SPI（下单时同步预占），监听订单的支付、取消事件（异步确认、释放）。
 * 依赖方向是 inventory → order；对订单类型的引用只出现在两个适配器里（OrderStockReservationAdapter、
 * OrderEventListener），库存的核心逻辑用的是自己的类型——相当于模块之间的防腐层。
 */
@ApplicationModule(displayName = "库存")
package com.example.monolith.inventory;

import org.springframework.modulith.ApplicationModule;
