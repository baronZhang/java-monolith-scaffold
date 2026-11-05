package com.example.monolith.product.application;

import com.example.monolith.product.domain.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 商品查询，详情走缓存（Cache-Aside：先读缓存，没有再查库并写回缓存）。
 *
 * <p>{@code sync = true} 防击穿：一个热点商品的缓存刚好过期，上千个请求同时未命中时，
 * 只有一个线程去查库，其他线程等它的结果。Caffeine 自己按 key 加锁；Redis 实现由 SingleFlightCacheManager
 * 在进程内按 key 加锁——多实例部署时每个实例各查一次库，从"上千次"降到"实例数次"，通常已经够了。
 *
 * <p>返回 Optional：商品不存在时缓存的是空值（防穿透），同一个不存在的 id 再来就直接从缓存返回，不再打到数据库。
 */
@Service
public class ProductQueryService {

    public static final String CACHE_NAME = "product";

    private final ProductRepository products;

    public ProductQueryService(ProductRepository products) {
        this.products = products;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#id", sync = true)
    public Optional<ProductView> find(long id) {
        return products.findById(id).map(ProductView::from);
    }

    /** 列表数据量小、变化少，演示里直接查库；真实项目可以按"分类 + 页码"缓存 */
    public List<ProductView> listAll() {
        return products.findAll().stream().map(ProductView::from).toList();
    }
}
