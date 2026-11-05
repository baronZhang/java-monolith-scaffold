package com.example.monolith.order.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** orders 的 Mapper */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {

    @Select("SELECT * FROM orders WHERE order_no = #{orderNo} FOR UPDATE")
    OrderPO lockByOrderNo(@Param("orderNo") long orderNo);

    /** 状态流转：WHERE 里带上期望的旧状态，旧状态不对就更新 0 行 */
    @Update("UPDATE orders SET status = #{status}, paid_at = #{paidAt}, cancelled_at = #{cancelledAt},"
            + " cancel_reason = #{cancelReason}, updated_at = #{updatedAt}"
            + " WHERE order_no = #{orderNo} AND status = #{expected}")
    int updateStatus(
            @Param("orderNo") long orderNo,
            @Param("expected") String expected,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("cancelledAt") LocalDateTime cancelledAt,
            @Param("cancelReason") String cancelReason,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 超时扫描：走联合索引 idx_orders_status_created (status, created_at)，
     * 等值条件在前、范围条件在后，正好用上索引的有序性，ORDER BY 也不需要额外排序。
     */
    @Select("SELECT order_no FROM orders WHERE status = 'PENDING_PAYMENT' AND created_at < #{createdBefore}"
            + " ORDER BY created_at LIMIT #{limit}")
    List<Long> findExpiredPending(@Param("createdBefore") LocalDateTime createdBefore, @Param("limit") int limit);
}
