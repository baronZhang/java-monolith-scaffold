package com.example.monolith.order.domain;

/**
 * 取消原因。
 *
 * <p>和微服务版对比：单体里没有"库存不足而取消"——库存不足时整个下单事务回滚，订单根本不会被创建。
 */
public enum CancelReason {
    /** 超时未支付，由定时任务取消 */
    TIMEOUT,
    /** 用户主动取消 */
    USER
}
