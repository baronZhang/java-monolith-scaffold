package com.example.monolith.inventory.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * inventory 的 Mapper。库存相关的更新全部写成显式 SQL：一眼就能看出并发安全靠的是什么。
 */
@Mapper
public interface StockMapper extends BaseMapper<StockPO> {

    /**
     * 预占：判断和扣减在一条 SQL 里。
     * InnoDB 执行 UPDATE 时先给这一行加排他锁，再"当前读"出最新的 available 判断条件——
     * 并发的扣减在行锁上排队，每个都基于最新值判断，所以不会超卖。返回 0 表示库存不够。
     */
    @Update("UPDATE inventory SET available = available - #{quantity}, reserved = reserved + #{quantity},"
            + " version = version + 1, updated_at = #{now}"
            + " WHERE sku_id = #{skuId} AND available >= #{quantity}")
    int tryReserve(@Param("skuId") long skuId, @Param("quantity") int quantity, @Param("now") LocalDateTime now);

    @Update("UPDATE inventory SET reserved = reserved - #{quantity}, sold = sold + #{quantity},"
            + " version = version + 1, updated_at = #{now} WHERE sku_id = #{skuId}")
    int confirmReserved(@Param("skuId") long skuId, @Param("quantity") int quantity, @Param("now") LocalDateTime now);

    @Update("UPDATE inventory SET reserved = reserved - #{quantity}, available = available + #{quantity},"
            + " version = version + 1, updated_at = #{now} WHERE sku_id = #{skuId}")
    int releaseReserved(@Param("skuId") long skuId, @Param("quantity") int quantity, @Param("now") LocalDateTime now);

    @Update("UPDATE inventory SET available = #{available}, version = version + 1, updated_at = #{now}"
            + " WHERE sku_id = #{skuId}")
    int setAvailable(@Param("skuId") long skuId, @Param("available") int available, @Param("now") LocalDateTime now);
}
