package com.example.monolith.product.application;

import com.example.monolith.product.ProductApi;
import com.example.monolith.product.ProductSnapshot;
import com.example.monolith.product.domain.Product;
import com.example.monolith.product.domain.ProductRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/** 商品模块对外 API 的实现：直接读库，不走缓存（下单要的是最准确的价格）。 */
@Service
class ProductApiImpl implements ProductApi {

    private final ProductRepository products;

    ProductApiImpl(ProductRepository products) {
        this.products = products;
    }

    @Override
    public List<ProductSnapshot> findSaleable(Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        return products.findByIds(skuIds).stream()
                .filter(Product::isOnSale)
                .map(product -> new ProductSnapshot(product.id(), product.name(), product.price()))
                .toList();
    }
}
