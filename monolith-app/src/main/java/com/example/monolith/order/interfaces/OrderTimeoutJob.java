package com.example.monolith.order.interfaces;

import com.example.monolith.order.application.OrderCancellationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时订单扫描：定时任务也是一种"入站适配器"，和 Controller 一样放在 interfaces 层，只负责触发用例。
 *
 * <p>为什么选定时扫描而不是延时队列，见 ADR"单体 5.4 超时取消：定时扫描还是延时队列"。
 * fixedDelay：上一轮执行完再等一个间隔，不会因为某一轮执行慢而叠加执行。
 */
@Component
@ConditionalOnProperty(name = "app.order.timeout-scan-enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderCancellationService cancellationService;

    public OrderTimeoutJob(OrderCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @Scheduled(fixedDelayString = "${app.order.timeout-scan-interval:PT30S}")
    public void cancelExpiredOrders() {
        int cancelled = cancellationService.cancelExpiredOrders();
        if (cancelled > 0) {
            log.info("超时未支付，自动取消了 {} 张订单", cancelled);
        }
    }
}
