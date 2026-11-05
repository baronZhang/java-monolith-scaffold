package com.example.monolith.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.monolith.order.application.OrderCancellationService;
import com.example.monolith.order.application.PlaceOrderCommand;
import com.example.monolith.order.application.PlaceOrderService;
import com.example.monolith.support.ApiClient;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import com.example.monolith.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 下单链路的集成测试：幂等下单 → 预占库存 → 支付 / 取消 / 超时取消 → 事件驱动的库存确认与释放。
 *
 * <p>用 {@code @TestBean} 把应用的 Clock 换成可以拨动的时钟：测试"15 分钟没支付自动取消"不用真的等。
 * 这里只用 1~5 号商品，6 号留给防超卖实验。
 */
@IntegrationTest
class OrderFlowIT {

    @TestBean(name = "clock")
    Clock clock;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlaceOrderService placeOrderService;

    @Autowired
    OrderCancellationService cancellationService;

    ApiClient api;
    String adminToken;
    String editorToken;

    /** @TestBean 约定：同名的静态工厂方法提供替换用的 Bean */
    static Clock clock() {
        return new MutableClock(Instant.now());
    }

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, jsonMapper);
        adminToken = api.accessToken(DemoAccounts.ADMIN);
        editorToken = api.accessToken(DemoAccounts.EDITOR);
    }

    @Test
    void 下单成功后预占库存() throws Exception {
        Stock before = stock(1);

        ApiClient.Response response = placeOrder(adminToken, newKey(), "[{\"skuId\":1,\"quantity\":2}]");

        assertThat(response.code()).as(response.body()).isZero();
        JsonNode order = response.data();
        assertThat(order.get("status").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("798.00");
        // 雪花 ID 以字符串返回，前端不会丢精度
        assertThat(order.get("orderNo").isString()).isTrue();
        assertThat(stock(1)).isEqualTo(new Stock(before.available() - 2, before.reserved() + 2, before.sold()));
    }

    @Test
    void 同一个幂等键重复提交_返回同一张订单_库存只扣一次() throws Exception {
        String key = newKey();
        Stock before = stock(2);

        ApiClient.Response first = placeOrder(adminToken, key, "[{\"skuId\":2,\"quantity\":1}]");
        ApiClient.Response second = placeOrder(adminToken, key, "[{\"skuId\":2,\"quantity\":1}]");

        assertThat(second.data().get("orderNo").asString())
                .isEqualTo(first.data().get("orderNo").asString());
        assertThat(first.header("Idempotent-Replayed")).isNull();
        assertThat(second.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(stock(2).reserved()).isEqualTo(before.reserved() + 1);
    }

    @Test
    void 幂等键相同但内容不同_明确报错而不是悄悄返回旧订单() throws Exception {
        String key = newKey();
        placeOrder(adminToken, key, "[{\"skuId\":2,\"quantity\":1}]");

        ApiClient.Response response = placeOrder(adminToken, key, "[{\"skuId\":2,\"quantity\":5}]");

        assertThat(response.code()).isEqualTo(10002);
        assertThat(response.message()).contains("Idempotency-Key");
    }

    @Test
    void 缺少或乱填幂等键返回400() throws Exception {
        ApiClient.Response missing = api.toResponse(mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"skuId\":1,\"quantity\":1}]}"))
                .andReturn());
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.message()).isEqualTo("缺少请求头 Idempotency-Key");

        ApiClient.Response invalid = placeOrder(adminToken, "短", "[{\"skuId\":1,\"quantity\":1}]");
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.message()).contains("Idempotency-Key 必须是");
    }

    @Test
    void 库存不足时整个下单回滚_不会留下订单_也不会扣掉其他商品的库存() throws Exception {
        String key = newKey();
        Stock keyboard = stock(1);
        Stock monitor = stock(3);

        // 1 号够，3 号不够（库存 30）：1 号的预占也要一起撤销
        ApiClient.Response response =
                placeOrder(adminToken, key, "[{\"skuId\":1,\"quantity\":1},{\"skuId\":3,\"quantity\":99}]");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.code()).isEqualTo(20001);
        assertThat(response.message()).isEqualTo("商品 3 库存不足");
        assertThat(stock(1)).isEqualTo(keyboard);
        assertThat(stock(3)).isEqualTo(monitor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE idempotency_key = ?", Integer.class, key))
                .isZero();
    }

    @Test
    void 支付后库存从预占转为已售_重复支付是幂等的() throws Exception {
        String orderNo = placeOrder(adminToken, newKey(), "[{\"skuId\":4,\"quantity\":3}]")
                .data()
                .get("orderNo")
                .asString();
        Stock afterPlace = stock(4);

        ApiClient.Response paid = api.postJson("/api/orders/" + orderNo + "/pay", adminToken, null);
        assertThat(paid.data().get("status").asString()).isEqualTo("PAID");

        // 库存的确认由事件驱动：订单事务提交后，库存模块在另一个线程里处理
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(stock(4))
                        .isEqualTo(
                                new Stock(afterPlace.available(), afterPlace.reserved() - 3, afterPlace.sold() + 3)));

        ApiClient.Response again = api.postJson("/api/orders/" + orderNo + "/pay", adminToken, null);
        assertThat(again.code()).isZero();
        assertThat(again.data().get("status").asString()).isEqualTo("PAID");
    }

    @Test
    void 用户取消后释放库存_已取消的订单不能再支付() throws Exception {
        Stock before = stock(5);
        String orderNo = placeOrder(adminToken, newKey(), "[{\"skuId\":5,\"quantity\":2}]")
                .data()
                .get("orderNo")
                .asString();

        ApiClient.Response cancelled = api.postJson("/api/orders/" + orderNo + "/cancel", adminToken, null);
        assertThat(cancelled.data().get("cancelReason").asString()).isEqualTo("USER");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(stock(5)).isEqualTo(before));

        ApiClient.Response pay = api.postJson("/api/orders/" + orderNo + "/pay", adminToken, null);
        assertThat(pay.code()).isEqualTo(20002);
        assertThat(pay.message()).isEqualTo("订单已取消，不能支付");
    }

    @Test
    void 超过支付时限自动取消并释放库存() throws Exception {
        String orderNo = placeOrder(editorToken, newKey(), "[{\"skuId\":1,\"quantity\":1}]")
                .data()
                .get("orderNo")
                .asString();
        assertThat(reservationStatus(orderNo)).isEqualTo("RESERVED");

        // 时间拨到 16 分钟后（默认支付时限 15 分钟），由测试直接触发一次扫描（定时任务在测试里是关着的）。
        // 扫描会取消所有已超时的待支付订单（包括其他测试留下的），所以这里只检查本测试这张订单
        ((MutableClock) clock).advance(Duration.ofMinutes(16));
        assertThat(cancellationService.cancelExpiredOrders()).isGreaterThanOrEqualTo(1);

        // 时钟拨过之后旧的 access token 已经过期（有效期 5 分钟），重新登录
        String token = api.accessToken(DemoAccounts.EDITOR);
        JsonNode order = api.getJson("/api/orders/" + orderNo, token).data();
        assertThat(order.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(order.get("cancelReason").asString()).isEqualTo("TIMEOUT");
        // 库存释放由事件驱动，异步完成
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(reservationStatus(orderNo)).isEqualTo("RELEASED"));
    }

    @Test
    void 超时但还没被扫描到的订单_支付时也会被拒绝() throws Exception {
        String orderNo = placeOrder(editorToken, newKey(), "[{\"skuId\":2,\"quantity\":1}]")
                .data()
                .get("orderNo")
                .asString();

        ((MutableClock) clock).advance(Duration.ofMinutes(15));

        ApiClient.Response pay =
                api.postJson("/api/orders/" + orderNo + "/pay", api.accessToken(DemoAccounts.EDITOR), null);
        assertThat(pay.code()).isEqualTo(20002);
        assertThat(pay.message()).isEqualTo("订单已超时，请重新下单");
    }

    @Test
    void 数据权限_只能看自己的订单_管理员可以看全部() throws Exception {
        String adminOrder = placeOrder(adminToken, newKey(), "[{\"skuId\":2,\"quantity\":1}]")
                .data()
                .get("orderNo")
                .asString();
        String editorOrder = placeOrder(editorToken, newKey(), "[{\"skuId\":2,\"quantity\":1}]")
                .data()
                .get("orderNo")
                .asString();

        // 看别人的订单返回 404 而不是 403：不暴露"这个订单号存在"
        assertThat(api.getJson("/api/orders/" + adminOrder, editorToken).status())
                .isEqualTo(404);
        assertThat(api.postJson("/api/orders/" + adminOrder + "/cancel", editorToken, null)
                        .status())
                .isEqualTo(404);
        assertThat(api.getJson("/api/orders/" + editorOrder, adminToken).status())
                .isEqualTo(200);
        assertThat(api.getJson("/api/orders", editorToken).data().get("list").toString())
                .contains(editorOrder)
                .doesNotContain(adminOrder);
    }

    @Test
    void 同一个幂等键并发提交_只生成一张订单() throws Exception {
        String key = newKey();
        Stock before = stock(2);
        int threads = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return placeOrderService
                            .place(new PlaceOrderCommand(
                                    DemoAccounts.ADMIN_ID, key, List.of(new PlaceOrderCommand.Item(2, 1))))
                            .order()
                            .orderNo();
                }));
            }
            startGate.countDown();
            Set<String> orderNos = new HashSet<>();
            for (Future<String> future : futures) {
                orderNos.add(future.get());
            }
            assertThat(orderNos).hasSize(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(stock(2).reserved()).isEqualTo(before.reserved() + 1);
    }

    private ApiClient.Response placeOrder(String token, String idempotencyKey, String items) throws Exception {
        return api.toResponse(mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":" + items + "}"))
                .andReturn());
    }

    private String reservationStatus(String orderNo) {
        return jdbc.queryForObject(
                "SELECT DISTINCT status FROM inventory_reservation WHERE order_no = ?",
                String.class,
                Long.parseLong(orderNo));
    }

    private Stock stock(long skuId) {
        return jdbc.queryForObject(
                "SELECT available, reserved, sold FROM inventory WHERE sku_id = ?",
                (rs, rowNum) -> new Stock(rs.getInt(1), rs.getInt(2), rs.getInt(3)),
                skuId);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    record Stock(int available, int reserved, int sold) {}
}
