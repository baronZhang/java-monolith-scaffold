package com.example.monolith.product.domain;

import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;

/** 商品（聚合根）。演示里一个商品就是一个 SKU。 */
public class Product {

    private final long id;
    private final String name;
    private BigDecimal price;
    private final ProductStatus status;
    private final Instant createdAt;

    public Product(long id, String name, BigDecimal price, ProductStatus status, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** 改价：价格必须大于 0，最多两位小数（分）。 */
    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() <= 0 || newPrice.scale() > 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "价格必须大于 0，最多两位小数");
        }
        this.price = newPrice;
    }

    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public BigDecimal price() {
        return price;
    }

    public ProductStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
