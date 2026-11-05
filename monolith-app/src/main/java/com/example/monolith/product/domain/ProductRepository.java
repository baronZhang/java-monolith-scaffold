package com.example.monolith.product.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 商品仓储（端口） */
public interface ProductRepository {

    Optional<Product> findById(long id);

    List<Product> findByIds(Collection<Long> ids);

    List<Product> findAll();

    void updatePrice(long id, BigDecimal price, Instant updatedAt);
}
