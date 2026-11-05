package com.example.monolith.inventory.interfaces;

import com.example.monolith.inventory.application.InventoryService;
import com.example.monolith.order.OrderCancelled;
import com.example.monolith.order.OrderPaid;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 监听订单事件：支付后确认库存，取消后释放库存。
 *
 * <p>订单事务提交之后才会触发（AFTER_COMMIT），在独立的线程和事务里执行：
 * <ul>
 *   <li>订单回滚了，这里根本不会被调用，库存不会被误释放；</li>
 *   <li>这里失败了（抛异常），订单的支付 / 取消也不会回滚；事件登记表把这条记录标成失败，由重投任务再试；</li>
 *   <li>因为会重投，处理逻辑必须幂等，见 {@link InventoryService#confirm(long)}。</li>
 * </ul>
 * 订单取消到库存释放之间有一个很短的窗口（毫秒级），这段时间里库存"看起来少了"——只会少卖，不会超卖。
 */
@Component
public class OrderEventListener {

    private final InventoryService inventoryService;

    public OrderEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @ApplicationModuleListener
    public void on(OrderPaid event) {
        inventoryService.confirm(event.orderNo());
    }

    @ApplicationModuleListener
    public void on(OrderCancelled event) {
        inventoryService.release(event.orderNo());
    }
}
