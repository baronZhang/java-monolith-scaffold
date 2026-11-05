package com.example.monolith.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.order.application.PlaceOrderCommand;
import com.example.monolith.order.application.PlaceOrderService;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 防超卖：200 个请求同时抢 50 件库存，走真实的下单用例（写订单 + 条件更新预占库存，一个本地事务）。
 * 结果必须是：恰好 50 单成功，150 单返回库存不足，可售库存正好为 0，订单数和预占数一致。
 *
 * <p>商品用测试里新插入的 900 号，和其他测试互不影响。几种扣减方式（含会超卖的错误写法）的对比实验见
 * {@code labs.stock.OversellStrategiesLabTest}。
 */
@IntegrationTest
class InventoryOversellIT {

    private static final Logger log = LoggerFactory.getLogger(InventoryOversellIT.class);
    private static final long SKU = 900;
    private static final int STOCK = 50;
    private static final int REQUESTS = 200;

    @Autowired
    PlaceOrderService placeOrderService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 并发抢购不超卖() throws Exception {
        jdbc.update("INSERT INTO product (id, name, price, status, created_at, updated_at)"
                + " VALUES (900, '压测商品', 1.00, 'ON_SALE', '2026-10-01 00:00:00', '2026-10-01 00:00:00')");
        jdbc.update(
                "INSERT INTO inventory (sku_id, available, reserved, sold, version, updated_at)"
                        + " VALUES (900, ?, 0, 0, 0, '2026-10-01 00:00:00')",
                STOCK);

        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        placeOrderService.place(new PlaceOrderCommand(
                                DemoAccounts.ADMIN_ID,
                                UUID.randomUUID().toString(),
                                List.of(new PlaceOrderCommand.Item(SKU, 1))));
                        return true;
                    } catch (BusinessException ex) {
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
                        return false;
                    }
                }));
            }
            startGate.countDown();
            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    succeeded++;
                }
            }
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            Map<String, Object> stock =
                    jdbc.queryForMap("SELECT available, reserved FROM inventory WHERE sku_id = 900");
            Integer orders = jdbc.queryForObject("SELECT COUNT(*) FROM order_line WHERE sku_id = 900", Integer.class);
            Integer reservations = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservation WHERE sku_id = 900", Integer.class);
            log.info(
                    "[实验结果] {} 个并发请求抢 {} 件库存：成功 {}，库存不足 {}，可售剩余 {}，预占 {}，订单 {}，预占记录 {}，耗时 {} ms",
                    REQUESTS,
                    STOCK,
                    succeeded,
                    REQUESTS - succeeded,
                    stock.get("available"),
                    stock.get("reserved"),
                    orders,
                    reservations,
                    elapsedMillis);

            assertThat(succeeded).isEqualTo(STOCK);
            assertThat(((Number) stock.get("available")).intValue()).isZero();
            assertThat(((Number) stock.get("reserved")).intValue()).isEqualTo(STOCK);
            assertThat(orders).isEqualTo(STOCK);
            assertThat(reservations).isEqualTo(STOCK);
        }
    }
}
