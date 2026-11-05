package com.example.monolith.inventory.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** inventory_reservation 的 Mapper */
@Mapper
public interface ReservationMapper extends BaseMapper<ReservationPO> {

    /** 状态流转的条件更新：重复的事件改不动任何行，这是监听器幂等的关键 */
    @Update("UPDATE inventory_reservation SET status = #{to}, updated_at = #{now}"
            + " WHERE order_no = #{orderNo} AND status = #{from}")
    int transition(
            @Param("orderNo") long orderNo,
            @Param("from") String from,
            @Param("to") String to,
            @Param("now") LocalDateTime now);
}
