package com.example.monolith.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.monolith.order.application.OrderPaymentService;
import com.example.monolith.order.application.OrderView;
import com.example.monolith.order.application.PlaceOrderCommand;
import com.example.monolith.order.application.PlaceOrderService;
import com.example.monolith.order.spi.StockReservationPort;
import com.example.monolith.product.ProductApi;
import com.example.monolith.product.ProductSnapshot;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 单模块测试（Spring Modulith 的 @ApplicationModuleTest）：只启动订单模块（加上共享内核），
 * 它依赖的商品 API、库存 SPI 用 Mock 代替。
 *
 * <p>意义：证明订单模块可以脱离其他模块单独运行和测试——这是"将来能拆出去"的前提。
 * 如果订单模块偷偷依赖了别的模块的 Bean，这个测试的容器就起不来。
 */
@ApplicationModuleTest
@ActiveProfiles("test")
class OrderModuleTest {

    @MockitoBean
    ProductApi productApi;

    @MockitoBean
    StockReservationPort stockReservation;

    @Autowired
    PlaceOrderService placeOrderService;

    @Autowired
    OrderPaymentService paymentService;

    @Autowired
    ApplicationContext context;

    @Test
    void 只启动了订单模块() {
        // 其他业务模块的 Bean 都不在容器里
        assertThat(context.getBeanNamesForType(com.example.monolith.inventory.interfaces.OrderEventListener.class))
                .isEmpty();
        assertThat(context.containsBean("authService")).isFalse();
    }

    @Test
    void 下单时调用库存SPI_支付后发布OrderPaid事件(Scenario scenario) {
        given(productApi.findSaleable(any()))
                .willReturn(List.of(new ProductSnapshot(1, "机械键盘 K8", new BigDecimal("399.00"))));
        OrderView order = placeOrderService
                .place(new PlaceOrderCommand(7L, "module-test-key-0001", List.of(new PlaceOrderCommand.Item(1, 1))))
                .order();
        long orderNo = Long.parseLong(order.orderNo());

        verify(stockReservation).reserve(eq(orderNo), anyList());

        // Scenario：触发一个动作，等待某个事件发布出来，再对事件做断言
        scenario.stimulate(() -> paymentService.pay(orderNo, 7L))
                .andWaitForEventOfType(OrderPaid.class)
                .matchingMappedValue(OrderPaid::orderNo, orderNo)
                .toArriveAndVerify(event -> assertThat(event.userId()).isEqualTo(7L));
    }
}
