package com.example.monolith.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceOrderCommandTest {

    @Test
    void 请求指纹与商品顺序无关_同一商品出现多次会合并() {
        PlaceOrderCommand a = command(new PlaceOrderCommand.Item(6, 1), new PlaceOrderCommand.Item(1, 2));
        PlaceOrderCommand b = command(
                new PlaceOrderCommand.Item(1, 1), new PlaceOrderCommand.Item(6, 1), new PlaceOrderCommand.Item(1, 1));

        assertThat(a.requestHash()).isEqualTo(b.requestHash());
        assertThat(b.mergedQuantities()).containsExactly(java.util.Map.entry(1L, 2), java.util.Map.entry(6L, 1));
    }

    @Test
    void 数量不同则指纹不同() {
        assertThat(command(new PlaceOrderCommand.Item(6, 1)).requestHash())
                .isNotEqualTo(command(new PlaceOrderCommand.Item(6, 2)).requestHash());
    }

    private static PlaceOrderCommand command(PlaceOrderCommand.Item... items) {
        return new PlaceOrderCommand(1L, "key-12345678", List.of(items));
    }
}
