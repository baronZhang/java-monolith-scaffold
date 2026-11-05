/**
 * 商品模块（演示里商品就是 SKU）：商品列表、详情（带缓存）、改价。
 *
 * <p>对外暴露 {@link com.example.monolith.product.ProductApi}：下单时订单模块用它拿商品的名称和价格快照。
 * 下单读的是数据库而不是缓存——价格以下单那一刻的数据库为准，缓存可能有几秒到几分钟的延迟。
 */
@ApplicationModule(displayName = "商品")
package com.example.monolith.product;

import org.springframework.modulith.ApplicationModule;
