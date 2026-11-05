package com.example.monolith.order.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 订单配置，前缀 {@code app.order}。
 *
 * @param paymentTimeout 支付时限：下单后超过这个时间没支付，订单自动取消、库存释放
 * @param timeoutScanBatchSize 超时扫描每批最多处理多少张订单：一次捞太多，事务和锁持有时间都会变长
 */
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(
        @DefaultValue("15m") Duration paymentTimeout,
        @DefaultValue("100") int timeoutScanBatchSize) {}
