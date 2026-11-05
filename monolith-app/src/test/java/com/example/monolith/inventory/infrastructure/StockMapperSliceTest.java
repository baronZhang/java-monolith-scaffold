package com.example.monolith.inventory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.monolith.support.MybatisSliceTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 持久层切片测试：只启动数据源、Flyway、MyBatis 和 Mapper，不加载 Web、安全、业务 Service。
 * 每个测试方法结束后自动回滚，互不影响。
 *
 * <p>测的是 SQL 本身的语义：条件更新在库存不够时影响 0 行；CHECK 约束兜底防止负库存。
 */
@MybatisSliceTest
class StockMapperSliceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 8, 0);

    @Autowired
    StockMapper stockMapper;

    @Test
    void 库存不够时条件更新影响0行_够的时候影响1行() {
        // 3 号商品种子库存 30
        assertThat(stockMapper.tryReserve(3, 31, NOW)).isZero();
        assertThat(stockMapper.tryReserve(3, 30, NOW)).isEqualTo(1);
        assertThat(stockMapper.tryReserve(3, 1, NOW)).isZero();

        StockPO stock = stockMapper.selectById(3L);
        assertThat(stock.getAvailable()).isZero();
        assertThat(stock.getReserved()).isEqualTo(30);
        assertThat(stock.getVersion()).isEqualTo(1);
    }

    @Test
    void CHECK约束是最后一道防线_负库存写不进数据库() {
        assertThatThrownBy(() -> stockMapper.setAvailable(1, -1, NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
