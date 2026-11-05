package com.example.monolith.product;

import java.util.Collection;
import java.util.List;

/** 商品模块对外提供的接口（进程内 API）。 */
public interface ProductApi {

    /**
     * 查询可售商品的快照（名称、价格），不存在或已下架的商品不会出现在结果里。
     * 订单里保存的是这份快照：之后商品改价、改名，不影响已经下的订单。
     */
    List<ProductSnapshot> findSaleable(Collection<Long> skuIds);
}
