package com.example.monolith.product.application;

import com.example.monolith.product.domain.Product;
import java.math.BigDecimal;

/**
 * 商品的读模型，也是缓存里存的对象。
 *
 * <p>缓存里存不可变的 record 而不是领域对象：缓存的对象会被多个线程共享，可变对象被某个调用方改了，
 * 所有人读到的都是被改过的数据（Caffeine 存的是对象引用，不是副本）。
 */
public record ProductView(long id, String name, BigDecimal price, String status) {

    static ProductView from(Product product) {
        return new ProductView(
                product.id(), product.name(), product.price(), product.status().name());
    }
}
