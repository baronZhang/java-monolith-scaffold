/**
 * 订单模块的 SPI（Service Provider Interface）：订单"需要什么"由订单自己定义，由别的模块提供实现。
 *
 * <p>{@code @NamedInterface("spi")}：把这个子包声明为模块对外暴露的命名接口，其他模块才被允许引用它
 * （默认只有模块根包是对外的，子包都是内部实现）。
 */
@NamedInterface("spi")
package com.example.monolith.order.spi;

import org.springframework.modulith.NamedInterface;
