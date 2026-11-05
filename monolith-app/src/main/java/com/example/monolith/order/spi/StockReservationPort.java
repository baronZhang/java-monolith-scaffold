package com.example.monolith.order.spi;

import java.util.List;

/**
 * 下单时预占库存的端口：接口由使用方（订单）按自己的需要定义，库存模块去实现——模块级的依赖倒置。
 *
 * <p>约定：
 * <ul>
 *   <li>必须在下单的事务里调用：预占和写订单要么一起成功，要么一起回滚，这正是单体"一个本地事务搞定"的优势；</li>
 *   <li>任何一个商品库存不足，抛出业务异常（20001），调用方的事务随之回滚，前面已经预占的也一起撤销。</li>
 * </ul>
 *
 * <p>拆成微服务时，这个接口不变，实现从"进程内调用库存模块"换成"远程调用库存服务"；
 * 但"和写订单在同一个事务里"这一条就保证不了了，要换成 TCC / Saga 等分布式方案，见"单体 5.5"。
 */
public interface StockReservationPort {

    void reserve(long orderNo, List<StockLine> lines);
}
