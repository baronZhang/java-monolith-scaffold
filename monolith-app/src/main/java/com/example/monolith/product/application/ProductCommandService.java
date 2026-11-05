package com.example.monolith.product.application;

import com.example.monolith.product.domain.Product;
import com.example.monolith.product.domain.ProductRepository;
import com.example.monolith.shared.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品写操作：改价后删除缓存。
 *
 * <p>为什么是"先更新数据库、再删缓存"，而不是"更新缓存"：
 * 更新缓存在并发下容易乱序（A 先改库后写缓存、B 后改库先写缓存，缓存里留下的是 A 的旧值）；
 * 删除缓存则让下一次读去库里取最新值。缓存管理器是"事务感知"的，删除会推迟到事务提交之后才执行。
 * 剩下的极小窗口（读请求在提交前读到旧值、又在删除后写回缓存）靠过期时间兜底，见"单体 4.4 缓存设计"。
 */
@Service
public class ProductCommandService {

    private final ProductRepository products;
    private final Clock clock;

    public ProductCommandService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Transactional
    @CacheEvict(cacheNames = ProductQueryService.CACHE_NAME, key = "#id")
    public ProductView changePrice(long id, BigDecimal price) {
        Product product = products.findById(id).orElseThrow(() -> BusinessException.notFound("商品 " + id + " 不存在"));
        product.changePrice(price);
        products.updatePrice(id, product.price(), clock.instant());
        return ProductView.from(product);
    }
}
