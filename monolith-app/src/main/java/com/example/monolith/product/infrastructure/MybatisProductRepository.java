package com.example.monolith.product.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.monolith.product.domain.Product;
import com.example.monolith.product.domain.ProductRepository;
import com.example.monolith.product.domain.ProductStatus;
import com.example.monolith.shared.persistence.DbTime;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 商品仓储的 MyBatis-Plus 实现 */
@Repository
class MybatisProductRepository implements ProductRepository {

    private final ProductMapper mapper;

    MybatisProductRepository(ProductMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisProductRepository::toDomain);
    }

    @Override
    public List<Product> findByIds(Collection<Long> ids) {
        return mapper.selectByIds(ids).stream()
                .map(MybatisProductRepository::toDomain)
                .toList();
    }

    @Override
    public List<Product> findAll() {
        return mapper.selectList(Wrappers.lambdaQuery(ProductPO.class).orderByAsc(ProductPO::getId)).stream()
                .map(MybatisProductRepository::toDomain)
                .toList();
    }

    @Override
    public void updatePrice(long id, BigDecimal price, Instant updatedAt) {
        mapper.update(Wrappers.lambdaUpdate(ProductPO.class)
                .set(ProductPO::getPrice, price)
                .set(ProductPO::getUpdatedAt, DbTime.toDb(updatedAt))
                .eq(ProductPO::getId, id));
    }

    private static Product toDomain(ProductPO po) {
        return new Product(
                po.getId(),
                po.getName(),
                po.getPrice(),
                ProductStatus.valueOf(po.getStatus()),
                DbTime.fromDb(po.getCreatedAt()));
    }
}
