package com.example.monolith.order.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** order_line 的 Mapper */
@Mapper
public interface OrderLineMapper extends BaseMapper<OrderLinePO> {}
